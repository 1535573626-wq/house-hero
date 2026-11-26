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
import kotlin.random.Random

private const val CONFETTI_WIDTH = 0.26f
private const val CONFETTI_DEPTH = 0.08f
private const val CONFETTI_SPACING = 0.06f
private const val CONFETTI_HEIGHT_OFFSET = 0.008f
private const val MAX_DIRT_PIECES = 200

/**
 * 管理地面上的彩带纸屑。
 *
 * 彩带会锚定到 [FloorSurface]：若检测到真实地面则贴合真实地面，
 * 否则使用 fallback 虚拟地面，保证 Emulator 与无 Spatial Data 场景也能运行。
 */
object DirtManager {
    val root: Entity = Entity()

    private val confettiPieces = mutableListOf<Entity>()
    private var floorEntity: Entity? = null

    private val partyColors = listOf(
        Color4(1.00f, 0.20f, 0.20f, 0.90f), // 红
        Color4(1.00f, 0.75f, 0.10f, 0.90f), // 金
        Color4(0.15f, 0.55f, 1.00f, 0.90f), // 蓝
        Color4(0.20f, 0.85f, 0.30f, 0.90f), // 绿
        Color4(0.80f, 0.20f, 1.00f, 0.90f), // 紫
        Color4(1.00f, 0.50f, 0.10f, 0.90f), // 橙
        Color4(1.00f, 0.35f, 0.70f, 0.90f), // 粉
        Color4(0.10f, 0.90f, 1.00f, 0.90f), // 青
    )

    private val confettiMesh by lazy {
        MeshResource.createPlane(CONFETTI_WIDTH, CONFETTI_DEPTH, 0f)
            ?: MeshResource.createBox(Vector3(CONFETTI_WIDTH, 0.004f, CONFETTI_DEPTH), 0f)
    }

    /**
     * 在 [surface] 上生成派对彩带。
     */
    fun spawnDirtGrid(surface: FloorSurface) {
        reset()

        val mesh = confettiMesh ?: return

        // 以 surface 中心为父节点，彩带使用本地坐标，自动继承地面旋转
        val parent = Entity().apply {
            components[TransformComponent::class.java]?.apply {
                position = surface.position
                quaternion = surface.rotation
            }
        }
        floorEntity = parent
        root.addChild(parent)

        val width = surface.bounds.x
        val depth = surface.bounds.y
        var countX = ((width - CONFETTI_SPACING) / (CONFETTI_WIDTH + CONFETTI_SPACING)).toInt().coerceAtLeast(1)
        var countZ = ((depth - CONFETTI_SPACING) / (CONFETTI_DEPTH + CONFETTI_SPACING)).toInt().coerceAtLeast(1)

        // 限制总彩带数量，避免超大房间拖垮性能
        val requestedCount = countX * countZ
        if (requestedCount > MAX_DIRT_PIECES) {
            val ratio = kotlin.math.sqrt(MAX_DIRT_PIECES.toFloat() / requestedCount)
            countX = (countX * ratio).toInt().coerceAtLeast(1)
            countZ = (countZ * ratio).toInt().coerceAtLeast(1)
        }

        val offsetX = (countX - 1) * (CONFETTI_WIDTH + CONFETTI_SPACING) / 2f
        val offsetZ = (countZ - 1) * (CONFETTI_DEPTH + CONFETTI_SPACING) / 2f

        val random = Random.Default
        for (x in 0 until countX) {
            for (z in 0 until countZ) {
                val material = PhysicallyBasedMaterial.create().apply {
                    setBaseColor(partyColors.random(random))
                    setOpacity(0.88f)
                }
                val entity = Entity().apply {
                    components.set(ModelComponent(mesh, material))
                }
                entity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(
                        x * (CONFETTI_WIDTH + CONFETTI_SPACING) - offsetX,
                        CONFETTI_HEIGHT_OFFSET,
                        z * (CONFETTI_DEPTH + CONFETTI_SPACING) - offsetZ,
                    )
                    // 随机水平旋转，让彩带朝向自然杂乱
                    quaternion = EulerAngles(0f, random.nextFloat() * 360f, 0f).toQuat()
                }
                parent.addChild(entity)
                confettiPieces.add(entity)
            }
        }
    }

    /**
     * 清洁 [position] 周围 [radius] 范围内的彩带。
     * 返回本次清洁掉的数量。
     */
    fun cleanAt(position: Vector3, radius: Float): Int {
        var cleaned = 0
        val iterator = confettiPieces.iterator()
        while (iterator.hasNext()) {
            val piece = iterator.next()
            val piecePos = piece.components[TransformComponent::class.java]?.position ?: continue
            if (horizontalDistance(piecePos, position) <= radius) {
                FeedbackManager.playCleanSuccess(piecePos)
                FeedbackManager.fadeOutEntity(piece)
                iterator.remove()
                cleaned++
            }
        }
        return cleaned
    }

    /**
     * 返回当前剩余彩带数量。
     */
    fun remainingCount(): Int = confettiPieces.size

    /**
     * 返回彩带总数。
     */
    fun totalCount(): Int = confettiPieces.size

    /**
     * 重置所有彩带。
     */
    fun reset() {
        confettiPieces.forEach { it.destroy() }
        confettiPieces.clear()
        floorEntity?.destroy()
        floorEntity = null
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
