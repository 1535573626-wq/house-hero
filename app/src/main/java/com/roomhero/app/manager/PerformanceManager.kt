package com.roomhero.app.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 简单性能监控器。
 *
 * 在 SpatialView [update] 回调中喂入 [onFrame]，统计：
 * - 最近一秒的平均 FPS
 * - 上一帧耗时（毫秒）
 * - 当前活跃实体数量
 * - 预估 DrawCall 数
 * - 预估三角面数
 *
 * 数据通过 StateFlow 暴露给 HUD 实时显示。
 */
object PerformanceManager {

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _frameTimeMs = MutableStateFlow(0f)
    val frameTimeMs: StateFlow<Float> = _frameTimeMs

    private val _entityCount = MutableStateFlow(0)
    val entityCount: StateFlow<Int> = _entityCount

    private val _estimatedDrawCalls = MutableStateFlow(0)
    val estimatedDrawCalls: StateFlow<Int> = _estimatedDrawCalls

    private val _estimatedTriangles = MutableStateFlow(0)
    val estimatedTriangles: StateFlow<Int> = _estimatedTriangles

    private var frameCount = 0
    private var lastFpsTimeNs = 0L
    private var lastFrameTimeNs = 0L

    /**
     * 每帧调用一次。
     */
    fun onFrame() {
        val now = System.nanoTime()

        // 单帧耗时
        if (lastFrameTimeNs != 0L) {
            _frameTimeMs.value = (now - lastFrameTimeNs) / 1_000_000.0f
        }
        lastFrameTimeNs = now

        // FPS：每秒统计一次帧数
        frameCount++
        if (lastFpsTimeNs == 0L) {
            lastFpsTimeNs = now
        }
        val elapsedSeconds = (now - lastFpsTimeNs) / 1_000_000_000.0
        if (elapsedSeconds >= 1.0) {
            _fps.value = frameCount
            frameCount = 0
            lastFpsTimeNs = now
        }

        val entities = estimateEntityCount()
        _entityCount.value = entities
        _estimatedDrawCalls.value = estimateDrawCalls()
        _estimatedTriangles.value = estimateTriangles()
    }

    fun reset() {
        frameCount = 0
        lastFpsTimeNs = 0L
        lastFrameTimeNs = 0L
        _fps.value = 0
        _frameTimeMs.value = 0f
        _entityCount.value = 0
        _estimatedDrawCalls.value = 0
        _estimatedTriangles.value = 0
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

    /**
     * 粗略估算 DrawCall 数。
     * 每个可见实体约 1 个 draw call，加上 HUD/固定开销。
     */
    private fun estimateDrawCalls(): Int {
        val visible = estimateEntityCount()
        return visible + 3 // 3 为吸尘器光标、地板锚点、HUD 等固定开销的保守估算
    }

    /**
     * 粗略估算当前场景三角面总数。
     */
    private fun estimateTriangles(): Int {
        var triangles = 0
        // 彩带：plane = 2 triangles
        triangles += DirtManager.totalCount() * 2
        // 整理物品：box = 12 triangles
        triangles += OrganizeManager.totalCount() * 12
        // 家具占位：线框框 + ghost + tag，保守按 30 个三角形估算
        triangles += FurnitureManager.itemCount() * 30
        // 奖励奖杯：程序化五角星或 Tripo 模型，按 200 个三角形估算
        triangles += RewardManager.activeCount() * 200
        return triangles
    }
}
