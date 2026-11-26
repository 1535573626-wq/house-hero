package com.roomhero.app.manager

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PhysicsWorldComponent
import com.pico.spatial.core.ecs.RigidBodyComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.core.ecs.simulation.CollisionFilter
import com.pico.spatial.core.ecs.simulation.CollisionInfoDetailLevel
import com.pico.spatial.core.ecs.simulation.CollisionResponseMode
import com.pico.spatial.core.ecs.simulation.MassProperties
import com.pico.spatial.core.ecs.simulation.RigidBodyMode
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3

private const val GRAB_RADIUS = 0.25f

/**
 * 单个可摆放家具。
 *
 * @param id 唯一编号
 * @param entity 家具实体
 * @param size 碰撞体尺寸
 * @param material 材质引用，用于碰撞警告时变色
 * @param originalColor 原始颜色
 * @param isGrabbed 是否正被光标抓起
 */
data class FurnitureItem(
    val id: Int,
    val entity: Entity,
    val size: Vector3,
    val material: PhysicallyBasedMaterial,
    val originalColor: Color4,
    var isGrabbed: Boolean = false,
)

/**
 * 自由摆放家具管理器。
 *
 * - 初始化物理世界与地面碰撞体
 * - 生成若干带 RigidBody + Collision 的方块家具
 * - 用户移动光标靠近家具即可吸附抓起（切换为 Kinematic）
 * - 调用 [release] 后家具恢复 Dynamic，受重力落下并与地面/其他家具碰撞
 */
object FurnitureManager {
    val root: Entity = Entity()

    private val items = mutableListOf<FurnitureItem>()
    private var floorEntity: Entity? = null

    private val furnitureSpecs = listOf(
        Triple(Vector3(0.30f, 0.20f, 0.20f), Color4(0.85f, 0.45f, 0.25f, 1.0f), Vector3(-0.5f, 0f, 0.3f)),
        Triple(Vector3(0.20f, 0.35f, 0.20f), Color4(0.35f, 0.65f, 0.85f, 1.0f), Vector3(0.0f, 0f, 0.0f)),
        Triple(Vector3(0.25f, 0.15f, 0.40f), Color4(0.55f, 0.75f, 0.35f, 1.0f), Vector3(0.5f, 0f, -0.3f)),
    )

    /**
     * 在 [surface] 上生成物理世界与家具。
     *
     * 若 [savedLayout] 不为空，会按保存的位置/旋转恢复家具，实现持久化布局。
     */
    fun spawnFurniture(surface: FloorSurface, savedLayout: List<FurnitureSnapshot>? = null) {
        reset()

        // 物理世界
        root.components.set(PhysicsWorldComponent())

        // 地面碰撞体（静态）
        val floor = Entity().apply {
            components[TransformComponent::class.java]?.apply {
                position = surface.position
                quaternion = surface.rotation
            }
        }
        val floorShape = ShapeResource.createBox(Vector3(4f, 0.05f, 4f))
        floor.components.set(
            CollisionComponent(
                listOf(floorShape),
                PhysicsMaterialResource(staticFriction = 0.8f, dynamicFriction = 0.8f, restitution = 0.1f),
                collisionResponseMode = CollisionResponseMode.COLLIDER_FULL,
                collisionFilter = CollisionFilter.COLLISION_FILTER_DEFAULT,
                collisionInfoDetailLevel = CollisionInfoDetailLevel.BRIEF,
            )
        )
        // 地面不需要 RigidBody，纯静态碰撞体即可
        root.addChild(floor)
        floorEntity = floor

        // 家具
        furnitureSpecs.forEachIndexed { index, (size, color, localPos) ->
            val mesh = MeshResource.createBox(size, 0.01f) ?: return@forEachIndexed
            val shape = ShapeResource.createBox(size)
            val material = PhysicallyBasedMaterial.create().apply {
                setBaseColor(color)
                setOpacity(0.95f)
            }
            val entity = Entity().apply {
                components.set(ModelComponent(mesh, material))
            }

            val saved = savedLayout?.getOrNull(index)
            val startPosition = saved?.position ?: run {
                val startY = size.y / 2f + 0.1f * (index + 1)
                surface.position + Vector3(localPos.x, startY, localPos.z)
            }
            val startRotation = saved?.rotation ?: Quat()

            entity.components[TransformComponent::class.java]?.apply {
                position = startPosition
                quaternion = startRotation
            }

            entity.components.set(
                CollisionComponent(
                    listOf(shape),
                    PhysicsMaterialResource(staticFriction = 0.5f, dynamicFriction = 0.5f, restitution = 0.2f),
                    collisionResponseMode = CollisionResponseMode.COLLIDER_FULL,
                    collisionFilter = CollisionFilter.COLLISION_FILTER_DEFAULT,
                    collisionInfoDetailLevel = CollisionInfoDetailLevel.BRIEF,
                )
            )
            entity.components.set(
                RigidBodyComponent().apply {
                    massProperties = MassProperties(mass = 1f, inertia = Vector3(1f), centerOfMass = Vector3.ZERO)
                    rigidBodyMode = RigidBodyMode.DYNAMIC
                    isAffectedByGravity = true
                }
            )

            root.addChild(entity)
            items.add(FurnitureItem(index, entity, size, material, color))
        }
    }

    /**
     * 获取当前所有家具的持久化快照。
     */
    fun snapshot(): List<FurnitureSnapshot> = items.map { item ->
        val transform = item.entity.components[TransformComponent::class.java]
        FurnitureSnapshot(
            position = transform?.position ?: Vector3.ZERO,
            rotation = transform?.quaternion ?: Quat.identity(),
        )
    }

    /**
     * 检查 [cursorPos] 是否抓起家具，并在抓起时检测与真实家具 mesh 的碰撞。
     *
     * 若虚拟家具与扫描到的真实家具（桌子、柜子、沙发等）发生包围盒相交，
     * 家具会高亮为红色，提示用户此处不可放置。
     */
    fun checkDrag(cursorPos: Vector3): Boolean {
        val grabbed = items.find { it.isGrabbed }
        if (grabbed != null) {
            // 跟随光标
            grabbed.entity.components[TransformComponent::class.java]?.apply {
                position = Vector3(cursorPos.x, cursorPos.y, cursorPos.z)
            }
            updateCollisionWarning(grabbed)
            return false
        }

        // 找最近家具抓起
        var nearest: FurnitureItem? = null
        var nearestDist = Float.MAX_VALUE
        items.forEach { item ->
            val pos = item.entity.components[TransformComponent::class.java]?.position ?: return@forEach
            val dist = horizontalDistance(pos, cursorPos)
            if (dist < GRAB_RADIUS && dist < nearestDist) {
                nearest = item
                nearestDist = dist
            }
        }

        nearest?.let { item ->
            item.isGrabbed = true
            item.entity.components[RigidBodyComponent::class.java]?.rigidBodyMode = RigidBodyMode.KINEMATIC
            return true
        }

        return false
    }

    /**
     * 检测 [item] 是否与真实家具 mesh 发生碰撞，并更新材质颜色。
     */
    private fun updateCollisionWarning(item: FurnitureItem) {
        val itemPos = item.entity.components[TransformComponent::class.java]?.position ?: return
        val itemMin = itemPos - item.size * 0.5f
        val itemMax = itemPos + item.size * 0.5f

        val collides = RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.TABLE)
            .asSequence()
            .plus(RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.CABINET))
            .plus(RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.SOFA))
            .plus(RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.BED))
            .plus(RoomSurfaceManager.getFurnitureMeshes(SemanticLabelType.CHAIR))
            .any { mesh ->
                val meshPos = mesh.transform.position
                val meshSize = mesh.boundingBoxSize
                val meshMin = meshPos - meshSize * 0.5f
                val meshMax = meshPos + meshSize * 0.5f
                aabbIntersects(itemMin, itemMax, meshMin, meshMax)
            }

        val targetColor = if (collides) Color4(1f, 0.2f, 0.2f, 1f) else item.originalColor
        item.material.setBaseColor(targetColor)
    }

    private fun aabbIntersects(minA: Vector3, maxA: Vector3, minB: Vector3, maxB: Vector3): Boolean {
        return minA.x <= maxB.x && maxA.x >= minB.x &&
            minA.y <= maxB.y && maxA.y >= minB.y &&
            minA.z <= maxB.z && maxA.z >= minB.z
    }

    /**
     * 放下当前抓起的家具，恢复 Dynamic 受物理模拟。
     */
    fun release() {
        items.find { it.isGrabbed }?.let { item ->
            item.isGrabbed = false
            item.entity.components[RigidBodyComponent::class.java]?.rigidBodyMode = RigidBodyMode.DYNAMIC
        }
    }

    fun isGrabbing(): Boolean = items.any { it.isGrabbed }

    fun itemCount(): Int = items.size

    fun reset() {
        items.forEach { it.entity.destroy() }
        items.clear()
        floorEntity?.destroy()
        floorEntity = null
        root.components.remove(PhysicsWorldComponent::class.java)
    }

    private fun horizontalDistance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
