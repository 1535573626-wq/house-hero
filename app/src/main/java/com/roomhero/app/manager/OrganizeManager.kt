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
import com.pico.spatial.sense.mesh.MeshAnchor
import java.util.UUID

private const val ITEM_SIZE = 0.10f
private const val GRAB_RADIUS = 0.15f
private const val PLACE_RADIUS = 0.12f
private const val ITEM_HEIGHT_OFFSET = 0.01f
private const val TAG_RADIUS = 0.025f
private const val TAG_OFFSET_Y = 0.04f

// 真实物体检测参数
private const val MAX_OBJECT_VOLUME = 0.08f        // 排除沙发等大件
private const val MAX_OBJECT_HEIGHT = 0.40f        // 排除显示器等高物
private const val TABLE_MARGIN = 0.15f             // 桌面边缘留白
private const val MIN_OBJECT_VOLUME = 0.0005f      // 过滤噪声小点

/**
 * 整理模式里的物品种类与对应目标收纳容器规格。
 *
 * 当前用本地 wireframe 容器占位；后续拿到 Tripo token 后，
 * 可把 [targetMesh] 替换为 AI 生成的书架/杯架/收纳盒/玩具箱 GLB。
 */
private val CONTAINER_SPECS = listOf(
    ContainerSpec(
        label = "Book",
        itemColor = Color4(0.95f, 0.30f, 0.30f, 0.95f),
        containerColor = Color4(0.80f, 0.35f, 0.25f, 0.90f),
        size = Vector3(0.30f, 0.06f, 0.22f), // 扁平书架
    ),
    ContainerSpec(
        label = "Cup",
        itemColor = Color4(0.25f, 0.65f, 1.00f, 0.95f),
        containerColor = Color4(0.20f, 0.45f, 0.70f, 0.90f),
        size = Vector3(0.14f, 0.04f, 0.14f), // 杯托
    ),
    ContainerSpec(
        label = "Charger",
        itemColor = Color4(1.00f, 0.85f, 0.20f, 0.95f),
        containerColor = Color4(0.70f, 0.60f, 0.15f, 0.90f),
        size = Vector3(0.24f, 0.05f, 0.10f), // 长条收纳盒
    ),
    ContainerSpec(
        label = "Toy",
        itemColor = Color4(0.30f, 0.80f, 0.35f, 0.95f),
        containerColor = Color4(0.25f, 0.60f, 0.30f, 0.90f),
        size = Vector3(0.22f, 0.14f, 0.22f), // 玩具箱
    ),
)

private data class ContainerSpec(
    val label: String,
    val itemColor: Color4,
    val containerColor: Color4,
    val size: Vector3,
)

/**
 * 检测到的真实桌面物体。
 *
 * @param anchorUUID 空间网格锚点 UUID
 * @param label 推断的收纳类别
 * @param color 物品展示颜色
 * @param meshAnchor 原始扫描 mesh 数据
 */
private data class DetectedObject(
    val anchorUUID: UUID,
    val label: String,
    val color: Color4,
    val meshAnchor: MeshAnchor,
)

/**
 * 单个待整理物品。
 *
 * @param id 唯一编号
 * @param label AI 识别出的物品类别标签（如 Book、Cup）
 * @param color 物品颜色
 * @param currentEntity 当前位置实体（用户需要移动它）
 * @param targetEntity 目标收纳容器虚线框实体
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
 * 在检测到的桌面（或 fallback 虚拟桌面）上生成若干待整理物品与对应目标收纳容器虚线框。
 * 优先使用真实扫描到的桌面物体 mesh 作为待整理物品；未扫描到时回退到预设方块物品。
 * 用户通过移动 HMD/吸尘器光标把物品拖入对应容器完成整理。
 */
object OrganizeManager {
    val root: Entity = Entity()

    private val items = mutableListOf<OrganizeItem>()
    private var tableParent: Entity? = null

    private val itemMesh by lazy {
        MeshResource.createBox(Vector3(ITEM_SIZE), 0f)
    }

    /**
     * 在 [surface] 上生成物品与目标容器。
     *
     * 流程：
     * 1. 优先使用真实 TABLE mesh 的包围盒对齐桌面
     * 2. 尝试扫描桌面上的真实物体 mesh
     * 3. 检测到真实物体则用其真实 mesh 作为待整理物品
     * 4. 未检测到则回退到 4 个预设物品方块
     */
    fun spawnItems(surface: FloorSurface) {
        reset()

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

        // 检测真实桌面物体
        val detectedObjects = detectTabletopObjects(effectiveSurface)
        if (detectedObjects.isNotEmpty()) {
            spawnRealObjectItems(detectedObjects, effectiveSurface, parent)
        } else {
            spawnFallbackItems(effectiveSurface, parent)
        }
    }

    /**
     * 从所有扫描到的空间网格中，找出放在桌面上的真实小物体。
     */
    private fun detectTabletopObjects(table: FloorSurface): List<DetectedObject> {
        val tableY = table.position.y
        val halfW = table.bounds.x / 2f - TABLE_MARGIN
        val halfD = table.bounds.y / 2f - TABLE_MARGIN
        val minX = table.position.x - halfW
        val maxX = table.position.x + halfW
        val minZ = table.position.z - halfD
        val maxZ = table.position.z + halfD

        return RoomSurfaceManager.getAllFurnitureMeshes()
            .filter { mesh ->
                // 排除桌面本身和大件家具
                SemanticLabelType.TABLE !in mesh.semantics &&
                    mesh.boundingBoxSize.x * mesh.boundingBoxSize.y * mesh.boundingBoxSize.z in MIN_OBJECT_VOLUME..MAX_OBJECT_VOLUME &&
                    mesh.boundingBoxSize.y <= MAX_OBJECT_HEIGHT
            }
            .filter { mesh ->
                // 物体底部在桌面上方附近，且中心在桌面范围内
                val bottomY = mesh.transform.position.y - mesh.boundingBoxSize.y / 2f
                val centerX = mesh.transform.position.x
                val centerZ = mesh.transform.position.z
                bottomY in (tableY - 0.05f)..(tableY + 0.10f) &&
                    centerX in minX..maxX &&
                    centerZ in minZ..maxZ
            }
            .take(4) // 同屏最多 4 个真实物体，避免拥挤
            .mapIndexed { index, mesh ->
                val label = guessLabel(mesh.boundingBoxSize)
                val color = CONTAINER_SPECS.find { it.label == label }?.itemColor
                    ?: CONTAINER_SPECS[index % CONTAINER_SPECS.size].itemColor
                DetectedObject(
                    anchorUUID = mesh.anchorUUID,
                    label = label,
                    color = color,
                    meshAnchor = mesh,
                )
            }
    }

    /**
     * 根据包围盒尺寸推断收纳类别。
     */
    private fun guessLabel(size: Vector3): String {
        val sorted = listOf(size.x, size.y, size.z).sorted()
        val min = sorted[0]
        val mid = sorted[1]
        val max = sorted[2]

        return when {
            // 扁平且较大 → 书
            max >= 0.18f && mid >= 0.12f && size.y <= 0.08f -> "Book"
            // 长条形 → 充电器/数据线
            max >= 0.12f && min <= 0.04f -> "Charger"
            // 近似立方体且较小 → 杯子
            max in 0.05f..0.14f && mid in 0.05f..0.14f -> "Cup"
            // 其他 → 玩具/杂物
            else -> "Toy"
        }
    }

    /**
     * 使用真实扫描物体生成整理游戏。
     */
    private fun spawnRealObjectItems(
        objects: List<DetectedObject>,
        table: FloorSurface,
        parent: Entity,
    ) {
        val halfW = (table.bounds.x / 2f) - 0.25f
        val halfD = (table.bounds.y / 2f) - 0.25f

        // 物品摆在左侧，目标容器摆在右侧
        val itemX = -halfW * 0.55f
        val targetX = halfW * 0.55f
        val spacingZ = (halfD * 1.8f) / (objects.size - 1).coerceAtLeast(1)

        objects.forEachIndexed { index, obj ->
            val z = -halfD * 0.8f + index * spacingZ
            val spec = CONTAINER_SPECS.find { it.label == obj.label } ?: CONTAINER_SPECS[index % CONTAINER_SPECS.size]

            // 真实物体占位模型：优先加载扫描 mesh，失败则用包围盒线框
            val mesh = try {
                MeshResource.loadFromMeshAnchor(obj.anchorUUID)
            } catch (_: Exception) {
                MeshResource.createBox(obj.meshAnchor.boundingBoxSize, 0.01f)
            } ?: return@forEachIndexed

            val itemMaterial = createItemMaterial(obj.color)
            val itemEntity = Entity().apply {
                components.set(ModelComponent(mesh, itemMaterial))
            }
            itemEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(itemX, ITEM_HEIGHT_OFFSET + obj.meshAnchor.boundingBoxSize.y / 2f, z)
                quaternion = Quat()
            }
            parent.addChild(itemEntity)

            // 目标容器
            val targetMesh = MeshResource.createBox(spec.size, 0.01f) ?: return@forEachIndexed
            val targetEntity = Entity().apply {
                components.set(ModelComponent(targetMesh, createWireframeMaterial(spec.containerColor)))
            }
            targetEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(targetX, ITEM_HEIGHT_OFFSET + spec.size.y / 2f, z)
                quaternion = Quat()
            }
            parent.addChild(targetEntity)

            // 容器顶部标签小球
            val tagEntity = createTag(spec)
            tagEntity.components[TransformComponent::class.java]?.position = Vector3(
                targetX,
                ITEM_HEIGHT_OFFSET + spec.size.y / 2f + TAG_RADIUS + TAG_OFFSET_Y,
                z,
            )
            parent.addChild(tagEntity)

            items.add(
                OrganizeItem(
                    id = index,
                    label = spec.label,
                    color = obj.color,
                    currentEntity = itemEntity,
                    targetEntity = targetEntity,
                )
            )
        }
    }

    /**
     * 无真实物体扫描结果时的回退方案：4 个预设方块物品。
     */
    private fun spawnFallbackItems(
        table: FloorSurface,
        parent: Entity,
    ) {
        val mesh = itemMesh ?: return
        val halfW = (table.bounds.x / 2f) - 0.25f
        val halfD = (table.bounds.y / 2f) - 0.25f
        val spacingZ = (halfD * 1.8f) / (CONTAINER_SPECS.size - 1).coerceAtLeast(1)

        CONTAINER_SPECS.forEachIndexed { index, spec ->
            val startZ = -halfD * 0.8f + index * spacingZ
            val targetZ = startZ
            val startX = -halfW * 0.55f
            val targetX = halfW * 0.55f

            val itemEntity = Entity().apply {
                components.set(ModelComponent(mesh, createItemMaterial(spec.itemColor)))
            }
            itemEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(startX, ITEM_HEIGHT_OFFSET + ITEM_SIZE / 2f, startZ)
                quaternion = Quat()
            }
            parent.addChild(itemEntity)

            val targetMesh = MeshResource.createBox(spec.size, 0.01f) ?: return@forEachIndexed
            val targetEntity = Entity().apply {
                components.set(ModelComponent(targetMesh, createWireframeMaterial(spec.containerColor)))
            }
            targetEntity.components[TransformComponent::class.java]?.apply {
                position = Vector3(targetX, ITEM_HEIGHT_OFFSET + spec.size.y / 2f, targetZ)
                quaternion = Quat()
            }
            parent.addChild(targetEntity)

            val tagEntity = createTag(spec)
            tagEntity.components[TransformComponent::class.java]?.position = Vector3(
                targetX,
                ITEM_HEIGHT_OFFSET + spec.size.y / 2f + TAG_RADIUS + TAG_OFFSET_Y,
                targetZ,
            )
            parent.addChild(tagEntity)

            items.add(
                OrganizeItem(
                    id = index,
                    label = spec.label,
                    color = spec.itemColor,
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

    private fun createTag(spec: ContainerSpec): Entity {
        val sphere = try {
            MeshResource.createSphere(TAG_RADIUS)
        } catch (_: Exception) {
            null
        }
        val material = UnlitMaterial.create().apply {
            setBaseColor(spec.containerColor)
            setOpacity(0.95f)
        }
        return Entity().apply {
            if (sphere != null) components.set(ModelComponent(sphere, material))
        }
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
