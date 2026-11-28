package com.roomhero.app.manager

import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.AnchorUpdate
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.sense.base.TrackingState
import com.pico.spatial.sense.mesh.MeshAnchor
import com.pico.spatial.sense.mesh.MeshTrackingManager
import com.pico.spatial.sense.plane.PlaneAnchor
import com.pico.spatial.sense.plane.PlaneTrackingManager
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 描述检测到的地面表面。
 *
 * @param anchorUUID 真实平面锚点的 UUID；若是 fallback 则为 null
 * @param position 表面的世界位置
 * @param rotation 表面的世界旋转（四元数）
 * @param bounds 平面 bounding box 尺寸（x=宽，y=深，单位：米）
 * @param isFallback 是否为 Emulator/未检测到平面时的虚拟地面
 */
data class FloorSurface(
    val anchorUUID: UUID?,
    val position: Vector3,
    val rotation: Quat,
    val bounds: Vector2,
    val isFallback: Boolean = false,
) {
    /** 地面的世界 Y 高度。 */
    val floorY: Float get() = position.y
}

/**
 * 房间表面管理器。
 *
 * 负责启动 [PlaneTrackingManager] / [MeshTrackingManager] 并收集：
 * - 地面（FLOOR）/桌面（TABLE）平面
 * - 真实家具/物体的空间网格（SOFA、CHAIR、CABINET、BED 等语义）
 *
 * 提供 [awaitFloor] / [awaitTable] 在超时后返回 fallback 表面，保证无真机/Emulator 也能跑。
 */
object RoomSurfaceManager {

    private val floorAnchors = mutableMapOf<UUID, PlaneAnchor>()
    private val tableAnchors = mutableMapOf<UUID, PlaneAnchor>()
    private val furnitureMeshes = mutableMapOf<UUID, MeshAnchor>()
    private var planeSubscription: Cancellable? = null
    private var meshSubscription: Cancellable? = null

    /**
     * 启动平面追踪与空间网格追踪，并订阅锚点更新。
     */
    fun start() {
        startPlaneTracking()
        startMeshTracking()
    }

    private fun startPlaneTracking() {
        if (planeSubscription != null) return

        planeSubscription = PlaneTrackingManager.subscribeAnchorUpdate { event ->
            if (PlaneTrackingManager.state != TrackingState.RUNNING) return@subscribeAnchorUpdate
            val anchor = event.anchor
            when (event.event) {
                AnchorUpdate.Event.ADDED,
                AnchorUpdate.Event.UPDATED -> {
                    when (anchor.semantics) {
                        SemanticLabelType.FLOOR -> floorAnchors[anchor.anchorUUID] = anchor
                        SemanticLabelType.TABLE -> tableAnchors[anchor.anchorUUID] = anchor
                        else -> {}
                    }
                }
                AnchorUpdate.Event.REMOVED -> {
                    floorAnchors.remove(anchor.anchorUUID)
                    tableAnchors.remove(anchor.anchorUUID)
                }
                else -> {}
            }
        }
        PlaneTrackingManager.start()
    }

    private fun startMeshTracking() {
        if (meshSubscription != null) return

        meshSubscription = MeshTrackingManager.subscribeAnchorUpdate { event ->
            if (MeshTrackingManager.state != TrackingState.RUNNING) return@subscribeAnchorUpdate
            val anchor = event.anchor
            when (event.event) {
                AnchorUpdate.Event.ADDED,
                AnchorUpdate.Event.UPDATED -> {
                    furnitureMeshes[anchor.anchorUUID] = anchor
                }
                AnchorUpdate.Event.REMOVED -> {
                    furnitureMeshes.remove(anchor.anchorUUID)
                }
                else -> {}
            }
        }
        MeshTrackingManager.start()
    }

    /**
     * 停止追踪并清空缓存。
     */
    fun stop() {
        planeSubscription?.cancel()
        planeSubscription = null
        meshSubscription?.cancel()
        meshSubscription = null
        floorAnchors.clear()
        tableAnchors.clear()
        furnitureMeshes.clear()
        PlaneTrackingManager.stop()
        MeshTrackingManager.stop()
    }

    /**
     * 当前已缓存的真实家具网格数量（按语义过滤）。
     */
    fun furnitureMeshCount(type: SemanticLabelType): Int =
        furnitureMeshes.values.count { type in it.semantics }

    /**
     * 获取指定语义类型的所有家具网格。
     */
    fun getFurnitureMeshes(type: SemanticLabelType): List<MeshAnchor> =
        furnitureMeshes.values.filter { type in it.semantics }

    /**
     * 获取当前已缓存的所有空间网格（含家具、物体、未知语义等）。
     */
    fun getAllFurnitureMeshes(): List<MeshAnchor> = furnitureMeshes.values.toList()

    /**
     * 等待检测到指定语义的真实家具网格，[timeoutMillis] 内未检测到则返回 null。
     */
    suspend fun awaitFurnitureMesh(
        type: SemanticLabelType,
        timeoutMillis: Long = 5000L,
    ): MeshAnchor? {
        start()

        return withTimeoutOrNull(timeoutMillis) {
            var mesh: MeshAnchor? = null
            while (mesh == null) {
                mesh = getFurnitureMeshes(type).maxByOrNull {
                    it.boundingBoxSize.x * it.boundingBoxSize.y * it.boundingBoxSize.z
                }
                if (mesh == null) delay(200L)
            }
            mesh
        }
    }

    /**
     * 等待检测到地面平面，[timeoutMillis] 内未检测到则返回 fallback 虚拟地面。
     */
    suspend fun awaitFloor(timeoutMillis: Long = 5000L): FloorSurface {
        start()

        val detected: FloorSurface? = withTimeoutOrNull(timeoutMillis) {
            var floor: FloorSurface? = null
            while (floor == null) {
                floor = pickBestFloor()
                if (floor == null) delay(200L)
            }
            floor
        }

        return detected ?: createFallbackFloor()
    }

    /**
     * 等待检测到桌面平面，[timeoutMillis] 内未检测到则返回 fallback 虚拟桌面。
     */
    suspend fun awaitTable(timeoutMillis: Long = 5000L): FloorSurface {
        start()

        val detected: FloorSurface? = withTimeoutOrNull(timeoutMillis) {
            var table: FloorSurface? = null
            while (table == null) {
                table = pickBestTable()
                if (table == null) delay(200L)
            }
            table
        }

        return detected ?: createFallbackTable()
    }

    /**
     * 当前已缓存的真实地面数量。
     */
    fun floorCount(): Int = floorAnchors.size

    private fun pickBestFloor(): FloorSurface? = pickBestSurface(floorAnchors)
    private fun pickBestTable(): FloorSurface? = pickBestSurface(tableAnchors)

    private fun pickBestSurface(anchors: Map<UUID, PlaneAnchor>): FloorSurface? {
        val anchor = anchors.values.maxByOrNull {
            it.boundingBoxSize.x * it.boundingBoxSize.y
        } ?: return null
        return FloorSurface(
            anchorUUID = anchor.anchorUUID,
            position = anchor.transform.position,
            rotation = anchor.transform.rotation.toQuat(),
            bounds = anchor.boundingBoxSize,
            isFallback = false,
        )
    }

    private fun createFallbackFloor(): FloorSurface {
        return FloorSurface(
            anchorUUID = null,
            position = Vector3(0f, 0f, 0f),
            rotation = Quat.identity(),
            bounds = Vector2(4f, 4f),
            isFallback = true,
        )
    }

    private fun createFallbackTable(): FloorSurface {
        return FloorSurface(
            anchorUUID = null,
            position = Vector3(0f, 0.8f, 0f),
            rotation = Quat.identity(),
            bounds = Vector2(1.5f, 1f),
            isFallback = true,
        )
    }
}
