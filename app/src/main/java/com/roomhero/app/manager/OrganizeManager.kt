package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PolygonFillMode
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.SemanticLabelType
import kotlin.random.Random

private const val ITEM_SIZE = 0.12f
private const val TARGET_SIZE = 0.18f
private const val GRAB_RADIUS = 0.15f
private const val PLACE_RADIUS = 0.12f
private const val ITEM_HEIGHT_OFFSET = 0.01f

/** 模拟 SpatialML 识别出的物品类别。真机可替换为 SecureMR 模型输出。 */
private val ITEM_LABELS = listOf("Book", "Cup", "Charger", "Toy")

/**
 * 单个待整理物品。
 *
 * @param id 唯一编号
 * @param label AI 识别出的物品类别标签（如 Book、Cup）
 * @param color 物品颜色
 * @param currentEntity 当前位置实体（用户需要移动它）
 * @param targetEntity 目标位置虚线框实体
 */
data class OrganizeItem(
    val id: Int,
    val label: String,
    val color: Color4,
    val currentEntity: Entity,
    val targetEntity: Entity,
    var isPlaced: Boolean = false,
    var isGrabbed: Boolean = false,
)

/**
 * 整理模式管理器。
 *
 * 在检测到的桌面（或 fallback 虚拟桌面）上生成若干待整理物品与对应目标虚线框。
 * 用户通过移动 HMD/吸尘器光标把物品拖入目标框完成整理。
 */
object OrganizeManager {
    val root: Entity = Entity()

    private val items = mutableListOf<OrganizeItem>()
    private var tableParent: Entity? = null

    private val itemColors = listOf(
        Color4(1.00f, 0.25f, 0.25f, 0.95f),
        Color4(0.25f, 0.70f, 1.00f, 0.95f),
        Color4(1.00f, 0.85f, 0.20f, 0.95f),
    )

    private val itemMesh by lazy {
        MeshResource.createBox(Vector3(ITEM_SIZE), 0f)
    }

    private val targetMesh by lazy {
        MeshResource.createBox(Vector3(TARGET_SIZE), 0f)
    }

    /**
     * 在 [surface] 上生成物品与目标框。
     *
     * 若 RoomSurfaceManager 已扫描到真实 TABLE mesh，会自动对齐到真实桌面的
     * 中心位置与包围盒，让整理游戏发生在真实家具上。
     */
    fun spawnItems(surface: FloorSurface) {
        reset()

        val mesh = itemMesh ?: return
        val targetMesh = targetMesh ?: return

        // 优先使用真实 TABLE mesh 的包围盒，否则回退平面检测尺寸
        val tableMesh = RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.TABLE)
            .maxByOrNull { it.boundingBoxSize.x * it.boundingBoxSize.y * it.boundingBoxSize.z }
        val effectiveSurface = if (tableMesh != null) {
            surface.copy(
                position = tableMesh.transform.position,
                rotation = tableMesh.transform.rotation.toQuat(),
                bounds = Vector2(tableMesh.boundingBoxSize.x, tableMesh.boundingBoxSize.z),
                anchorUUID = tableMesh.anchorUUID,
                isFallback = false,
            )
        } else {
            surface
        }

        val parent = Entity().apply {
            components[TransformComponent::class.java]?.apply {
                position = effectiveSurface.position
                quaternion = effectiveSurface.rotation
            }
        }
        tableParent = parent
        root.addChild(parent)

        val random = Random.Default
        val halfW = (effectiveSurface.bounds.x / 2f) - TARGET_SIZE
        val halfD = (effectiveSurface.bounds.y / 2f) - TARGET_SIZE

        for (i in itemColors.indices) {
            val color = itemColors[i]
            val label = ITEM_LABELS[i % ITEM_LABELS.size]
            val startX = -halfW * 0.6f
            val startZ = (i - 1) * (TARGET_SIZE + 0.05f)
            val targetX = halfW * 0.6f
            val targetZ = startZ

            val itemEntity = Entity().apply {
                components.set(ModelComponent(mesh, createItemMaterial(color)))
            }
            itemEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(startX, ITEM_HEIGHT_OFFSET + ITEM_SIZE / 2f, startZ)
                quaternion = Quat()
            }
            parent.addChild(itemEntity)

            val targetEntity = Entity().apply {
                components.set(ModelComponent(targetMesh, createWireframeMaterial(color)))
            }
            targetEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(targetX, ITEM_HEIGHT_OFFSET + TARGET_SIZE / 2f, targetZ)
                quaternion = Quat()
            }
            parent.addChild(targetEntity)

            items.add(
                OrganizeItem(
                    id = i,
                    label = label,
                    color = color,
                    currentEntity = itemEntity,
                    targetEntity = targetEntity,
                )
            )
        }
    }

    /**
     * 检查 [cursorPos] 是否拿起/放下物品。
     * 返回本次成功放置的物品数量。
     */
    fun checkPlacement(cursorPos: Vector3): Int {
        var placed = 0

        items.filter { !it.isPlaced }.forEach { item ->
            val itemPos = item.currentEntity.components[TransformComponent::class.java]?.position
                ?: return@forEach

            if (!item.isGrabbed) {
                // 光标进入抓取范围，拿起物品
                if (horizontalDistance(itemPos, cursorPos) <= GRAB_RADIUS) {
                    item.isGrabbed = true
                    // 把物品从桌面父节点移除，便于直接设置世界坐标
                    tableParent?.removeChild(item.currentEntity)
                    root.addChild(item.currentEntity)
                }
            } else {
                // 跟随光标
                item.currentEntity.components[TransformComponent::class.java]?.apply {
                    position = Vector3(cursorPos.x, cursorPos.y, cursorPos.z)
                }

                // 靠近目标框则放置成功
                val targetPos = item.targetEntity.components[TransformComponent::class.java]?.position
                    ?: return@forEach
                if (horizontalDistance(cursorPos, targetPos) <= PLACE_RADIUS) {
                    item.isPlaced = true
                    item.isGrabbed = false
                    // 对齐到目标框并播放音效 + 高亮脉冲
                    item.currentEntity.components[TransformComponent::class.java]?.position = targetPos
                    FeedbackManager.playPlaceSuccess(targetPos)
                    FeedbackManager.playSparkle(targetPos, item.color)
                    FeedbackManager.pulseEntity(item.currentEntity)
                    FeedbackManager.pulseEntity(item.targetEntity)
                    items.remove(item)
                    placed++
                }
            }
        }

        return placed
    }

    fun totalCount(): Int = items.size
    fun remainingCount(): Int = items.count { !it.isPlaced }

    fun reset() {
        items.forEach {
            it.currentEntity.destroy()
            it.targetEntity.destroy()
        }
        items.clear()
        tableParent?.destroy()
        tableParent = null
    }

    private fun createItemMaterial(color: Color4) =
        com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial.create().apply {
            setBaseColor(color)
            setOpacity(0.95f)
        }

    private fun createWireframeMaterial(color: Color4) =
        UnlitMaterial.create().apply {
            setBaseColor(color)
            setPolygonFillMode(PolygonFillMode.LINE)
        }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
