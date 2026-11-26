package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3

private const val TILE_SIZE = 0.3f
private const val TILE_SPACING = 0.05f
private const val DIRT_HEIGHT_OFFSET = 0.01f

/**
 * 管理地面上的“污渍”方格。
 *
 * 污渍会锚定到 [FloorSurface]：若检测到真实地面则贴合真实地面，
 * 否则使用 fallback 虚拟地面，保证 Emulator 与无 Spatial Data 场景也能运行。
 */
object DirtManager {
    val root: Entity = Entity()

    private val dirtTiles = mutableListOf<Entity>()
    private var floorEntity: Entity? = null

    private val dirtyMaterial by lazy {
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(0.1f, 0.35f, 0.9f, 1.0f))
            setOpacity(0.85f)
        }
    }

    /**
     * 在 [surface] 上生成网格状污渍。
     */
    fun spawnDirtGrid(surface: FloorSurface) {
        reset()

        val mesh = MeshResource.createPlane(TILE_SIZE, TILE_SIZE, 0f)
            ?: MeshResource.createBox(Vector3(TILE_SIZE, 0.005f, TILE_SIZE), 0f)
            ?: return

        // 以 surface 中心为父节点，污渍使用本地坐标，自动继承地面旋转
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
        val tileCountX = ((width - TILE_SPACING) / (TILE_SIZE + TILE_SPACING)).toInt().coerceAtLeast(1)
        val tileCountZ = ((depth - TILE_SPACING) / (TILE_SIZE + TILE_SPACING)).toInt().coerceAtLeast(1)

        val offsetX = (tileCountX - 1) * (TILE_SIZE + TILE_SPACING) / 2f
        val offsetZ = (tileCountZ - 1) * (TILE_SIZE + TILE_SPACING) / 2f

        for (x in 0 until tileCountX) {
            for (z in 0 until tileCountZ) {
                val entity = Entity().apply {
                    components.set(ModelComponent(mesh, dirtyMaterial))
                }
                entity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(
                        x * (TILE_SIZE + TILE_SPACING) - offsetX,
                        DIRT_HEIGHT_OFFSET,
                        z * (TILE_SIZE + TILE_SPACING) - offsetZ,
                    )
                    quaternion = Quat()
                }
                parent.addChild(entity)
                dirtTiles.add(entity)
            }
        }
    }

    /**
     * 清洁 [position] 周围 [radius] 范围内的污渍。
     * 返回本次清洁掉的污渍数量。
     */
    fun cleanAt(position: Vector3, radius: Float): Int {
        var cleaned = 0
        val iterator = dirtTiles.iterator()
        while (iterator.hasNext()) {
            val tile = iterator.next()
            val tilePos = tile.components[TransformComponent::class.java]?.position ?: continue
            if (horizontalDistance(tilePos, position) <= radius) {
                tile.enabled = false
                iterator.remove()
                cleaned++
            }
        }
        return cleaned
    }

    /**
     * 返回当前剩余污渍数量。
     */
    fun remainingCount(): Int = dirtTiles.size

    /**
     * 返回污渍总数。
     */
    fun totalCount(): Int = dirtTiles.size

    /**
     * 重置所有污渍。
     */
    fun reset() {
        dirtTiles.forEach { it.destroy() }
        dirtTiles.clear()
        floorEntity?.destroy()
        floorEntity = null
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
