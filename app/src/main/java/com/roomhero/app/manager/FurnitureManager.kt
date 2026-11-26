package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PolygonFillMode
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.SemanticLabelType
import java.util.UUID

private const val MAX_PLACEHOLDERS = 12
private const val MIN_GRAB_RADIUS = 0.20f

// 视觉反馈参数
private const val GHOST_OPACITY = 0.12f
private const val GHOST_SCALE = 0.96f
private const val TAG_RADIUS = 0.035f
private const val TAG_OFFSET_Y = 0.04f
private const val GRAB_SCALE = 1.08f

private val FURNITURE_TYPES = listOf(
    SemanticLabelType.SOFA,
    SemanticLabelType.CABINET,
    SemanticLabelType.BED,
    SemanticLabelType.TABLE,
    SemanticLabelType.CHAIR,
    SemanticLabelType.REFRIGERATOR,
    SemanticLabelType.WASHING_MACHINE,
    SemanticLabelType.AIR_CONDITIONER,
    SemanticLabelType.PLANT,
    SemanticLabelType.LAMP,
)

private val SEMANTIC_COLORS = mapOf(
    SemanticLabelType.SOFA to Color4(0.95f, 0.55f, 0.25f, 0.90f),
    SemanticLabelType.CABINET to Color4(0.55f, 0.35f, 0.20f, 0.90f),
    SemanticLabelType.BED to Color4(0.25f, 0.55f, 0.85f, 0.90f),
    SemanticLabelType.TABLE to Color4(0.35f, 0.75f, 0.45f, 0.90f),
    SemanticLabelType.CHAIR to Color4(0.85f, 0.35f, 0.65f, 0.90f),
    SemanticLabelType.REFRIGERATOR to Color4(0.80f, 0.85f, 0.90f, 0.90f),
    SemanticLabelType.WASHING_MACHINE to Color4(0.70f, 0.75f, 0.80f, 0.90f),
    SemanticLabelType.AIR_CONDITIONER to Color4(0.50f, 0.70f, 0.80f, 0.90f),
    SemanticLabelType.PLANT to Color4(0.30f, 0.75f, 0.35f, 0.90f),
    SemanticLabelType.LAMP to Color4(1.00f, 0.90f, 0.25f, 0.90f),
)

/**
 * 单个可拖动的家具占位模型。
 *
 * @param anchorUUID 对应真实空间网格锚点的 UUID
 * @param label 语义标签
 * @param entity 占位模型实体（线框外壳）
 * @param fillEntity 内部半透明 ghost 填充
 * @param tagEntity 顶部语义标记小球
 * @param size 包围盒尺寸
 * @param material 线框材质，碰撞警告时会变色
 * @param fillMaterial ghost 填充材质，也会随碰撞变色
 * @param originalColor 原始颜色
 * @param isGrabbed 是否正被光标抓起
 */
data class FurnitureItem(
    val anchorUUID: UUID,
    val label: String,
    val entity: Entity,
    val fillEntity: Entity,
    val tagEntity: Entity,
    val size: Vector3,
    val material: UnlitMaterial,
    val fillMaterial: PhysicallyBasedMaterial,
    val originalColor: Color4,
    var isGrabbed: Boolean = false,
    var wasColliding: Boolean = false,
)

/**
 * 自由摆放家具管理器。
 *
 * - 不再使用程序生成的方块，而是读取 [RoomSurfaceManager] 扫描到的真实家具空间网格
 * - 为每件真实家具生成一个线框外壳 + 半透明 ghost + 顶部语义标记
 * - 用户可拖动占位模型试摆新布局；抓起时放大高亮，放下后保持位置，不触发物理坠落
 * - 布局通过 [FurnitureAnchorManager] 持久化，下次启动可恢复
 */
object FurnitureManager {
    val root: Entity = Entity()

    private val items = mutableListOf<FurnitureItem>()

    /**
     * 在 [surface] 上根据真实扫描家具生成占位模型。
     *
     * 若 [savedLayout] 不为空，会按保存的位置/旋转恢复对应 UUID 的家具占位模型。
     */
    fun spawnFurniture(surface: FloorSurface, savedLayout: List<FurnitureSnapshot>? = null) {
        reset()

        val meshes = FURNITURE_TYPES
            .flatMap { RoomSurfaceManager.getFurnitureMeshes(it) }
            .sortedByDescending { it.boundingBoxSize.x * it.boundingBoxSize.y * it.boundingBoxSize.z }
            .take(MAX_PLACEHOLDERS)

        if (meshes.isEmpty()) {
            // Emulator / 无扫描结果时给一个 fallback 占位，保证界面不空
            createFallbackPlaceholder(surface)
            return
        }

        meshes.forEachIndexed { index, meshAnchor ->
            val primarySemantic = meshAnchor.semantics.firstOrNull { it in FURNITURE_TYPES }
                ?: SemanticLabelType.UNKNOWN
            val color = SEMANTIC_COLORS[primarySemantic] ?: Color4(0.75f, 0.75f, 0.75f, 0.90f)
            val label = primarySemantic.name

            val mesh = try {
                MeshResource.loadFromMeshAnchor(meshAnchor.anchorUUID)
            } catch (_: Exception) {
                try {
                    MeshResource.createBox(meshAnchor.boundingBoxSize, 0.01f)
                } catch (_: Exception) {
                    null
                }
            }

            if (mesh == null) return@forEachIndexed

            val (entity, material) = createWireframeShell(mesh, color)
            val (fillEntity, fillMaterial) = createGhostFill(mesh, color)
            val tagEntity = createTag(meshAnchor.boundingBoxSize, color)

            entity.addChild(fillEntity)
            entity.addChild(tagEntity)

            val saved = savedLayout?.find { it.anchorUUID == meshAnchor.anchorUUID }
            val startPosition = saved?.position ?: meshAnchor.transform.position
            val startRotation = saved?.rotation ?: meshAnchor.transform.rotation.toQuat()

            entity.components[TransformComponent::class.java]?.apply {
                position = startPosition
                quaternion = startRotation
            }

            root.addChild(entity)
            items.add(
                FurnitureItem(
                    anchorUUID = meshAnchor.anchorUUID,
                    label = label,
                    entity = entity,
                    fillEntity = fillEntity,
                    tagEntity = tagEntity,
                    size = meshAnchor.boundingBoxSize,
                    material = material,
                    fillMaterial = fillMaterial,
                    originalColor = color,
                )
            )
        }
    }

    /**
     * 获取当前所有占位模型的持久化快照。
     */
    fun snapshot(): List<FurnitureSnapshot> = items.map { item ->
        val transform = item.entity.components[TransformComponent::class.java]
        FurnitureSnapshot(
            position = transform?.position ?: Vector3.ZERO,
            rotation = transform?.quaternion ?: Quat.identity(),
            anchorUUID = item.anchorUUID,
            label = item.label,
        )
    }

    /**
     * 检查 [cursorPos] 是否抓起/拖动家具占位模型。
     *
     * 拖动过程中会实时检测与真实家具 mesh 的包围盒相交，若重叠则把线框与 ghost 变红提示不可放置。
     */
    fun checkDrag(cursorPos: Vector3): Boolean {
        val grabbed = items.find { it.isGrabbed }
        if (grabbed != null) {
            grabbed.entity.components[TransformComponent::class.java]?.apply {
                position = Vector3(cursorPos.x, cursorPos.y, cursorPos.z)
                scaleVector = Vector3(GRAB_SCALE)
            }
            updateCollisionWarning(grabbed)
            return false
        }

        var nearest: FurnitureItem? = null
        var nearestDist = Float.MAX_VALUE
        items.forEach { item ->
            val pos = item.entity.components[TransformComponent::class.java]?.position ?: return@forEach
            val grabRadius = kotlin.math.max(MIN_GRAB_RADIUS, kotlin.math.max(item.size.x, item.size.z) * 0.5f)
            val dist = horizontalDistance(pos, cursorPos)
            if (dist < grabRadius && dist < nearestDist) {
                nearest = item
                nearestDist = dist
            }
        }

        nearest?.let { item ->
            item.isGrabbed = true
            return true
        }

        return false
    }

    /**
     * 放下当前抓起的占位模型，保持当前位置不变。
     */
    fun release() {
        items.find { it.isGrabbed }?.let { item ->
            item.isGrabbed = false
            item.entity.components[TransformComponent::class.java]?.scaleVector = Vector3(1f)
            updateCollisionWarning(item)
        }
    }

    fun isGrabbing(): Boolean = items.any { it.isGrabbed }

    fun itemCount(): Int = items.size

    fun reset() {
        items.forEach { it.entity.destroy() }
        items.clear()
    }

    /**
     * 创建线框外壳实体。
     */
    private fun createWireframeShell(mesh: MeshResource, color: Color4): Pair<Entity, UnlitMaterial> {
        val material = UnlitMaterial.create().apply {
            setBaseColor(color)
            setPolygonFillMode(PolygonFillMode.LINE)
            setOpacity(0.90f)
        }
        val entity = Entity().apply {
            components.set(ModelComponent(mesh, material))
        }
        return entity to material
    }

    /**
     * 创建内部半透明 ghost 填充实体。
     */
    private fun createGhostFill(mesh: MeshResource, color: Color4): Pair<Entity, PhysicallyBasedMaterial> {
        val material = PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(color.red, color.green, color.blue, 1f))
            setOpacity(GHOST_OPACITY)
            setRoughness(0.85f)
            setMetallic(0.0f)
        }
        val entity = Entity().apply {
            components.set(ModelComponent(mesh, material))
        }
        entity.components[TransformComponent::class.java]?.scaleVector = Vector3(GHOST_SCALE)
        return entity to material
    }

    /**
     * 创建顶部语义标记小球。
     */
    private fun createTag(size: Vector3, color: Color4): Entity {
        val sphere = try {
            MeshResource.createSphere(TAG_RADIUS)
        } catch (_: Exception) {
            try {
                MeshResource.createBox(Vector3(TAG_RADIUS), 0f)
            } catch (_: Exception) {
                null
            }
        }
        val material = UnlitMaterial.create().apply {
            setBaseColor(color)
            setOpacity(0.95f)
        }
        val entity = Entity().apply {
            if (sphere != null) components.set(ModelComponent(sphere, material))
        }
        entity.components[TransformComponent::class.java]?.position = Vector3(
            0f,
            size.y / 2f + TAG_RADIUS + TAG_OFFSET_Y,
            0f,
        )
        return entity
    }

    /**
     * 检测 [item] 是否与除自身外的真实家具 mesh 发生碰撞，并更新线框与 ghost 颜色。
     */
    private fun updateCollisionWarning(item: FurnitureItem) {
        val itemPos = item.entity.components[TransformComponent::class.java]?.position ?: return
        val itemMin = itemPos - item.size * 0.5f
        val itemMax = itemPos + item.size * 0.5f

        val collides = FURNITURE_TYPES
            .flatMap { RoomSurfaceManager.getFurnitureMeshes(it) }
            .any { mesh ->
                if (mesh.anchorUUID == item.anchorUUID) return@any false
                val meshPos = mesh.transform.position
                val meshSize = mesh.boundingBoxSize
                val meshMin = meshPos - meshSize * 0.5f
                val meshMax = meshPos + meshSize * 0.5f
                aabbIntersects(itemMin, itemMax, meshMin, meshMax)
            }

        val targetColor = if (collides) Color4(1f, 0.2f, 0.2f, 0.90f) else item.originalColor
        item.material.setBaseColor(targetColor)
        item.fillMaterial.setBaseColor(Color4(targetColor.red, targetColor.green, targetColor.blue, 1f))

        // 碰撞状态由“无”变“有”时播放一次空间警告音效，避免连续鬼畜
        if (collides && !item.wasColliding) {
            FeedbackManager.playCollisionWarning(itemPos)
        }
        item.wasColliding = collides
    }

    private fun aabbIntersects(minA: Vector3, maxA: Vector3, minB: Vector3, maxB: Vector3): Boolean {
        return minA.x <= maxB.x && maxA.x >= minB.x &&
            minA.y <= maxB.y && maxA.y >= minB.y &&
            minA.z <= maxB.z && maxA.z >= minB.z
    }

    private fun createFallbackPlaceholder(surface: FloorSurface) {
        val size = Vector3(0.40f, 0.45f, 0.40f)
        val mesh = MeshResource.createBox(size, 0.01f) ?: return
        val color = Color4(0.80f, 0.80f, 0.80f, 0.85f)

        val (entity, material) = createWireframeShell(mesh, color)
        val (fillEntity, fillMaterial) = createGhostFill(mesh, color)
        val tagEntity = createTag(size, color)

        entity.addChild(fillEntity)
        entity.addChild(tagEntity)

        entity.components[TransformComponent::class.java]?.apply {
            position = surface.position + Vector3(0f, size.y / 2f, 0f)
            quaternion = Quat.identity()
        }

        root.addChild(entity)
        items.add(
            FurnitureItem(
                anchorUUID = UUID(0L, 0L),
                label = "FALLBACK",
                entity = entity,
                fillEntity = fillEntity,
                tagEntity = tagEntity,
                size = size,
                material = material,
                fillMaterial = fillMaterial,
                originalColor = color,
            )
        )
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
