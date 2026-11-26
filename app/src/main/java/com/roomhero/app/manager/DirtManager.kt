package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialContent

private const val GRID_SIZE_X = 10
private const val GRID_SIZE_Z = 10
private const val TILE_SIZE = 0.3f
private const val TILE_SPACING = 0.05f

/**
 * 管理地面上的“污渍”方格。
 *
 * MVP 版本在虚拟地面（y=0）上生成规则网格，每个格子是一个可隐藏 Entity。
 * 当吸尘器靠近时，格子被“清洁”并从场景中移除。
 */
object DirtManager {
    val root: Entity = Entity()

    private val dirtTiles = mutableListOf<Entity>()
    private val dirtyMaterial by lazy {
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(0.1f, 0.35f, 0.9f, 1.0f))
            setOpacity(0.85f)
        }
    }

    /**
     * 在地面生成网格状污渍。
     */
    fun spawnDirtGrid(content: SpatialContent) {
        reset()

        val mesh = MeshResource.createPlane(TILE_SIZE, TILE_SIZE, 0f)
            ?: MeshResource.createBox(Vector3(TILE_SIZE, 0.01f, TILE_SIZE), 0f)
            ?: return

        val offsetX = (GRID_SIZE_X - 1) * (TILE_SIZE + TILE_SPACING) / 2f
        val offsetZ = (GRID_SIZE_Z - 1) * (TILE_SIZE + TILE_SPACING) / 2f

        for (x in 0 until GRID_SIZE_X) {
            for (z in 0 until GRID_SIZE_Z) {
                val entity = Entity().apply {
                    components.set(ModelComponent(mesh, dirtyMaterial))
                }
                entity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(
                        x * (TILE_SIZE + TILE_SPACING) - offsetX,
                        0f,
                        z * (TILE_SIZE + TILE_SPACING) - offsetZ,
                    )
                }
                root.addChild(entity)
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
    fun totalCount(): Int = GRID_SIZE_X * GRID_SIZE_Z

    /**
     * 重置所有污渍。
     */
    fun reset() {
        dirtTiles.forEach { it.destroy() }
        dirtTiles.clear()
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
