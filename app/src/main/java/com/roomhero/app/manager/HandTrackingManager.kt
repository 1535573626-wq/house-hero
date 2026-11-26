package com.roomhero.app.manager

import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandTrackingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PINCH_THRESHOLD = 0.75f

/**
 * 手部追踪数据管理器。
 *
 * - 启动 [HandTrackingProvider] 获取右手骨骼与捏合数据
 * - 暴露当前手部位置、捏合状态与捏合按下/抬起事件
 * - [VacuumManager] 会优先使用本管理器提供的手部位置作为光标
 *
 * 注意：PICO Emulator 默认无法授予手部追踪权限，此时 [isTracking] 为 false，
 * 所有交互自动回退到 HMD 光标。
 */
object HandTrackingManager {

    private var provider: HandTrackingProvider? = null
    private var updateJob: Job? = null

    private var _currentHandPosition = Vector3(0f, 0f, 0f)
    private var _isPinching = false
    private var _wasPinching = false
    private var _isTracking = false

    /** 当前交互手（右手）捏合点/食指指尖的世界坐标。 */
    val currentHandPosition: Vector3 get() = _currentHandPosition

    /** 当前是否处于捏合状态。 */
    val isPinching: Boolean get() = _isPinching

    /** 本帧是否刚刚发生“捏合按下”事件（从非捏合变为捏合）。 */
    val onPinchDown: Boolean
        get() = _isPinching && !_wasPinching

    /** 本帧是否刚刚发生“捏合抬起”事件（从捏合变为非捏合）。 */
    val onPinchUp: Boolean
        get() = !_isPinching && _wasPinching

    /** 当前是否有手部追踪数据。 */
    val isTracking: Boolean get() = _isTracking

    /**
     * 启动手部追踪。
     */
    fun start() {
        stop()

        provider = HandTrackingProvider().apply { start() }

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                update()
                delay(16) // ~60fps
            }
        }
    }

    /**
     * 停止手部追踪。
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null
        provider?.stop()
        provider = null

        _isTracking = false
        _isPinching = false
        _wasPinching = false
    }

    private fun update() {
        val data = provider?.latestData ?: return
        val rightHand = data.right

        if (rightHand == null) {
            _isTracking = false
            _wasPinching = _isPinching
            _isPinching = false
            return
        }

        _isTracking = true

        // 优先使用捏合点作为交互位置；无捏合数据时用食指指尖
        val pinch = data.rightPinch
        val targetPosition = if (pinch != null && pinch.isReady) {
            pinch.pinchPose.position
        } else {
            rightHand.handJoints.find { it.index == HandJoint.Index.INDEX_TIP }?.position
                ?: rightHand.handJoints.firstOrNull()?.position
                ?: Vector3(0f, 0f, 0f)
        }

        _currentHandPosition = targetPosition

        // 更新捏合状态
        val pinchValue = data.rightPinch?.pinchValue ?: 0f
        _wasPinching = _isPinching
        _isPinching = pinchValue >= PINCH_THRESHOLD
    }
}
