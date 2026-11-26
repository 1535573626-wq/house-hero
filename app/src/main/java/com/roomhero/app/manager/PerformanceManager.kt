package com.roomhero.app.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 简单性能监控器。
 *
 * 在 SpatialView [update] 回调中喂入 [onFrame]，用系统时间计算最近一秒的平均 FPS，
 * 同时统计当前活跃实体数量（彩带、物品、家具、奖励）。
 */
object PerformanceManager {

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _entityCount = MutableStateFlow(0)
    val entityCount: StateFlow<Int> = _entityCount

    private var frameCount = 0
    private var lastFpsTimeNs = 0L

    /**
     * 每帧调用一次。
     */
    fun onFrame() {
        frameCount++

        val now = System.nanoTime()
        if (lastFpsTimeNs == 0L) {
            lastFpsTimeNs = now
        }
        val elapsedSeconds = (now - lastFpsTimeNs) / 1_000_000_000.0
        if (elapsedSeconds >= 1.0) {
            _fps.value = frameCount
            frameCount = 0
            lastFpsTimeNs = now
        }

        _entityCount.value = estimateEntityCount()
    }

    fun reset() {
        frameCount = 0
        lastFpsTimeNs = 0L
        _fps.value = 0
        _entityCount.value = 0
    }

    /**
     * 估算当前活跃实体数量。用于提醒用户/开发者场景复杂度。
     */
    private fun estimateEntityCount(): Int {
        var count = 0
        count += DirtManager.totalCount()
        count += OrganizeManager.totalCount()
        count += FurnitureManager.itemCount()
        count += RewardManager.activeCount()
        // 吸尘器光标、粒子发射器等未计入，保持轻量
        return count
    }
}
