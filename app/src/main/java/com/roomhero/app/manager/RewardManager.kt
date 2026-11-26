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
import kotlin.math.sin

private const val REWARD_DISTANCE = 0.8f
private const val REWARD_SPACING = 0.35f
private const val REWARD_HEIGHT = 0.15f
private const val BOB_AMP = 0.04f
private const val BOB_SPEED = 3f
private const val ROTATE_SPEED = 60f // 度/秒

/**
 * 3D 奖励管理器。
 *
 * 游戏结束时根据得分/进度在 HMD 前方地面上生成漂浮旋转的奖杯/五角星。
 * Token 到位前使用 [TripoClient] 返回的本地程序化五角星作为占位模型。
 */
object RewardManager {
    val root: Entity = Entity()

    private val rewards = mutableListOf<RewardItem>()
    private var elapsedSeconds = 0f

    private var lastScore = 0
    private var lastProgress = 0
    private var lastHmdPosition = Vector3(0f, 0f, 0f)
    private var lastFloorY = 0f

    private data class RewardItem(
        val entity: Entity,
        val baseY: Float,
        val rotateSpeed: Float,
        val bobPhase: Float,
    )

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
    fun spawnReward(score: Int = lastScore, progress: Int = lastProgress) {
        clear()

        val rank = rankFromProgress(progress)
        val count = rank.starCount
        val mesh = TripoClient.generate("trophy ${rank.name}") ?: return

        val startX = -(count - 1) * REWARD_SPACING / 2f
        for (i in 0 until count) {
            val material = PhysicallyBasedMaterial.create().apply {
                setBaseColor(rank.color)
                setOpacity(0.95f)
            }
            val entity = Entity().apply {
                components.set(ModelComponent(mesh, material))
            }

            val x = lastHmdPosition.x + startX + i * REWARD_SPACING
            val z = lastHmdPosition.z - REWARD_DISTANCE
            val baseY = lastFloorY + REWARD_HEIGHT / 2f

            entity.components[TransformComponent::class.java]?.apply {
                position = Vector3(x, baseY, z)
                quaternion = Quat.identity()
            }

            root.addChild(entity)
            rewards.add(
                RewardItem(
                    entity = entity,
                    baseY = baseY,
                    rotateSpeed = ROTATE_SPEED * (0.8f + 0.4f * i),
                    bobPhase = i * 1.2f,
                )
            )
        }
    }

    /**
     * 每帧更新奖励动画：Y 轴自转 + 上下浮动。
     */
    fun update(dtSeconds: Float) {
        elapsedSeconds += dtSeconds
        rewards.forEach { reward ->
            val transform = reward.entity.components[TransformComponent::class.java] ?: return@forEach

            // 自转
            val currentRot = transform.quaternion ?: Quat.identity()
            val deltaRot = EulerAngles(0f, reward.rotateSpeed * dtSeconds, 0f).toQuat()
            transform.quaternion = deltaRot * currentRot

            // 上下浮动
            val yOffset = sin(this.elapsedSeconds * BOB_SPEED + reward.bobPhase) * BOB_AMP
            transform.position = Vector3(
                transform.position.x,
                reward.baseY + yOffset,
                transform.position.z,
            )
        }
    }

    /**
     * 清除已生成的奖励实体。
     */
    fun clear() {
        rewards.forEach { it.entity.destroy() }
        rewards.clear()
    }

    fun reset() {
        clear()
        elapsedSeconds = 0f
        lastScore = 0
        lastProgress = 0
        lastHmdPosition = Vector3(0f, 0f, 0f)
        lastFloorY = 0f
    }

    fun activeCount(): Int = rewards.size

    private fun rankFromProgress(progress: Int): Rank {
        return when {
            progress >= 90 -> Rank.GOLD
            progress >= 60 -> Rank.SILVER
            else -> Rank.BRONZE
        }
    }

    private enum class Rank(val starCount: Int, val color: Color4) {
        GOLD(3, Color4(1.0f, 0.84f, 0.0f, 1.0f)),
        SILVER(2, Color4(0.85f, 0.85f, 0.88f, 1.0f)),
        BRONZE(1, Color4(0.8f, 0.5f, 0.2f, 1.0f)),
    }
}
