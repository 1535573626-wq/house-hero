package com.roomhero.app.manager

import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.base.AnchorUpdate
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.sense.base.TrackingState
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
 * 负责启动 [PlaneTrackingManager] 并收集地面（FLOOR）平面。
 * 提供 [awaitFloor] 在超时后返回 fallback 虚拟地面，保证无真机/Emulator 也能跑。
 */
object RoomSurfaceManager {

    private val floorAnchors = mutableMapOf<UUID, PlaneAnchor>()
    private var subscription: Cancellable? = null

    /**
     * 启动平面追踪并开始订阅锚点更新。
     */
    fun start() {
        if (subscription != null) return

        subscription = PlaneTrackingManager.subscribeAnchorUpdate { event ->
            if (PlaneTrackingManager.state != TrackingState.RUNNING) return@subscribeAnchorUpdate
            val anchor = event.anchor
            when (event.event) {
                AnchorUpdate.Event.ADDED,
                AnchorUpdate.Event.UPDATED -> {
                    if (anchor.semantics == SemanticLabelType.FLOOR) {
                        floorAnchors[anchor.anchorUUID] = anchor
                    }
                }
                AnchorUpdate.Event.REMOVED -> {
                    floorAnchors.remove(anchor.anchorUUID)
                }
                else -> {}
            }
        }
        PlaneTrackingManager.start()
    }

    /**
     * 停止平面追踪并清空缓存。
     */
    fun stop() {
        subscription?.cancel()
        subscription = null
        floorAnchors.clear()
        PlaneTrackingManager.stop()
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
     * 当前已缓存的真实地面数量。
     */
    fun floorCount(): Int = floorAnchors.size

    private fun pickBestFloor(): FloorSurface? {
        val anchor = floorAnchors.values.maxByOrNull {
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
}
