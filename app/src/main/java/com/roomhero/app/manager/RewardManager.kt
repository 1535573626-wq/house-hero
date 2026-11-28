package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.roomhero.app.network.TripoClient
import com.roomhero.app.network.TripoRank
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.sin

private const val REWARD_DISTANCE = 0.8f
private const val REWARD_SPACING = 0.35f
private const val REWARD_HEIGHT = 0.15f
private const val BOB_AMP = 0.04f
private const val BOB_SPEED = 3f
private const val ROTATE_SPEED = 60f // 度/秒

/**
 * 对象池槽位。
 *
 * 奖励实体和材质被复用，避免每次游戏结束都新建/销毁 Entity。
 * [meshReady] 标记是否已完成 Tripo 模型加载；加载前显示旋转的 loading 占位。
 */
private data class RewardSlot(
    val entity: Entity,
    val material: PhysicallyBasedMaterial,
    var inUse: Boolean = false,
    var meshReady: Boolean = false,
    var baseY: Float = 0f,
    var rotateSpeed: Float = ROTATE_SPEED,
    var bobPhase: Float = 0f,
)

/**
 * 3D 奖励管理器。
 *
 * - 使用对象池复用奖励 Entity 与 Material，减少运行时分配
 * - 多个 Tripo 模型并发生成，生成过程中显示旋转 loading 方块，不阻塞 HUD
 * - 游戏结束时根据得分/进度在 HMD 前方地面上生成漂浮旋转的奖杯
 */
object RewardManager {
    val root: Entity = Entity()

    private val slots = mutableListOf<RewardSlot>()
    private var elapsedSeconds = 0f

    private var lastScore = 0
    private var lastProgress = 0
    private var lastHmdPosition = Vector3(0f, 0f, 0f)
    private var lastFloorY = 0f

    private var loadingMesh: MeshResource? = null

    /**
     * 在游戏结束时记录当前成绩与位置，供后续手动触发奖励生成。
     */
    fun prepare(score: Int, progress: Int, hmdPosition: Vector3, floorY: Float) {
        lastScore = score
        lastProgress = progress
        lastHmdPosition = hmdPosition
        lastFloorY = floorY
    }

    /**
     * 立即在 HMD 前方生成 3D 奖励。
     *
     * @param score 最终得分，未传则使用 [prepare] 时记录的值
     * @param progress 完成百分比，未传则使用 [prepare] 时记录的值
     */
    suspend fun spawnReward(score: Int = lastScore, progress: Int = lastProgress) {
        clear()

        val rank = rankFromProgress(progress)
        val count = rank.starCount
        val tripoRank = when (rank) {
            Rank.GOLD -> TripoRank.GOLD
            Rank.SILVER -> TripoRank.SILVER
            Rank.BRONZE -> TripoRank.BRONZE
        }

        val startX = -(count - 1) * REWARD_SPACING / 2f

        // 并发请求 Tripo 模型，避免串行等待
        val deferredMeshes = coroutineScope {
            List(count) { index ->
                async {
                    val seedPrompt = "${rank.promptPrefix}_${index}"
                    TripoClient.generate(seedPrompt, tripoRank)
                }
            }
        }

        // 先立即展示 loading 占位，让用户感知到奖励正在生成
        val assignedSlots = (0 until count).map { index ->
            val slot = acquireSlot()
            val x = lastHmdPosition.x + startX + index * REWARD_SPACING
            val z = lastHmdPosition.z - REWARD_DISTANCE
            val baseY = lastFloorY + REWARD_HEIGHT / 2f

            slot?.let {
                it.inUse = true
                it.meshReady = false
                it.baseY = baseY
                it.rotateSpeed = ROTATE_SPEED * (0.8f + 0.4f * index)
                it.bobPhase = index * 1.2f

                it.entity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(x, baseY, z)
                    quaternion = Quat.identity()
                    scaleVector = Vector3(1f)
                }
                // loading 阶段用半透明方块
                it.material.apply {
                    setBaseColor(rank.color)
                    setOpacity(0.85f)
                }
                it.entity.components.set(ModelComponent(ensureLoadingMesh(), it.material))
                root.addChild(it.entity)
            }
            slot
        }

        // 等待所有 Tripo 模型加载完成，再替换 mesh
        val meshes = try {
            deferredMeshes.awaitAll()
        } catch (e: Exception) {
            e.printStackTrace()
            List(count) { null }
        }

        meshes.forEachIndexed { index, mesh ->
            val slot = assignedSlots.getOrNull(index) ?: return@forEachIndexed
            val finalMesh = mesh ?: ensureLoadingMesh()
            slot.material.apply {
                setBaseColor(rank.color)
                setOpacity(0.95f)
            }
            slot.entity.components.set(ModelComponent(finalMesh, slot.material))
            slot.meshReady = true
        }
    }

    /**
     * 每帧更新奖励动画：Y 轴自转 + 上下浮动。
     */
    fun update(dtSeconds: Float) {
        elapsedSeconds += dtSeconds
        slots.filter { it.inUse }.forEach { slot ->
            val transform = slot.entity.components[TransformComponent::class.java] ?: return@forEach

            // loading 阶段加快旋转，让用户知道正在生成
            val speed = if (slot.meshReady) slot.rotateSpeed else slot.rotateSpeed * 2.5f
            val currentRot = transform.quaternion ?: Quat.identity()
            val deltaRot = EulerAngles(0f, speed * dtSeconds, 0f).toQuat()
            transform.quaternion = deltaRot * currentRot

            // 上下浮动
            val yOffset = sin(elapsedSeconds * BOB_SPEED + slot.bobPhase) * BOB_AMP
            transform.position = Vector3(
                transform.position.x,
                slot.baseY + yOffset,
                transform.position.z,
            )
        }
    }

    /**
     * 释放所有在用槽位（放回池中，不销毁 Entity）。
     */
    fun clear() {
        slots.forEach { slot ->
            if (slot.inUse) {
                slot.inUse = false
                slot.meshReady = false
                root.removeChild(slot.entity)
                slot.entity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(0f, -1000f, 0f) // 移出视野
                    scaleVector = Vector3(1f)
                }
            }
        }
    }

    fun reset() {
        clear()
        elapsedSeconds = 0f
        lastScore = 0
        lastProgress = 0
        lastHmdPosition = Vector3(0f, 0f, 0f)
        lastFloorY = 0f
    }

    fun activeCount(): Int = slots.count { it.inUse }

    private fun acquireSlot(): RewardSlot {
        val free = slots.find { !it.inUse }
        if (free != null) return free

        // 池中没有空闲槽位时新建，最大 3 个
        val material = PhysicallyBasedMaterial.create().apply { setOpacity(0.95f) }
        val entity = Entity().apply {
            components.set(ModelComponent(ensureLoadingMesh(), material))
        }
        return RewardSlot(entity, material).also { slots.add(it) }
    }

    private fun ensureLoadingMesh(): MeshResource {
        return loadingMesh ?: MeshResource.createBox(Vector3(0.12f), 0.01f)?.also {
            loadingMesh = it
        } ?: throw IllegalStateException("Cannot create reward loading mesh")
    }

    private fun rankFromProgress(progress: Int): Rank {
        return when {
            progress >= 90 -> Rank.GOLD
            progress >= 60 -> Rank.SILVER
            else -> Rank.BRONZE
        }
    }

    private enum class Rank(val starCount: Int, val color: Color4, val promptPrefix: String) {
        GOLD(3, Color4(1.0f, 0.84f, 0.0f, 1.0f), "shiny golden trophy"),
        SILVER(2, Color4(0.85f, 0.85f, 0.88f, 1.0f), "silver medal trophy"),
        BRONZE(1, Color4(0.8f, 0.5f, 0.2f, 1.0f), "bronze trophy"),
    }
}
