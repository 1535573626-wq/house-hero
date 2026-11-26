package com.roomhero.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val GAME_DURATION_SECONDS = 60

/**
 * 应用模式。
 */
enum class AppMode {
    /** 清洁模式 */
    Clean,
    /** 整理模式 */
    Organize,
    /** 自由摆放家具模式 */
    Furniture,
}

/**
 * 游戏阶段。
 */
enum class GamePhase {
    /** 初始/扫描中 */
    Scanning,
    /** 扫描完成，等待用户开始 */
    Ready,
    /** 游戏中 */
    Playing,
    /** 游戏结束 */
    Finished,
}

/**
 * 应用状态数据类。
 *
 * 同时支持清洁模式与整理模式。
 */
data class GameState(
    val mode: AppMode = AppMode.Clean,
    val phase: GamePhase = GamePhase.Scanning,
    val timeRemaining: Int = GAME_DURATION_SECONDS,
    val score: Int = 0,
    val totalTargets: Int = 0,
    val completedTargets: Int = 0,
) {
    val isPlaying: Boolean get() = phase == GamePhase.Playing
    val isFinished: Boolean get() = phase == GamePhase.Finished
    val progressPercent: Int
        get() = if (totalTargets > 0) (completedTargets * 100 / totalTargets) else 0
}

/**
 * 单例游戏状态管理器。
 *
 * 同时被空间舞台与 SpatialUI 窗口访问，作为共享状态源。
 */
object GameStateManager {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId

    /**
     * 切换应用模式。切换后重置状态并触发 SpatialView 重建。
     */
    fun switchMode(mode: AppMode) {
        _gameState.value = GameState(mode = mode)
        restartSession()
    }

    fun setReady(totalTargets: Int) {
        _gameState.update { current ->
            current.copy(
                phase = GamePhase.Ready,
                timeRemaining = GAME_DURATION_SECONDS,
                score = 0,
                totalTargets = totalTargets,
                completedTargets = 0,
            )
        }
    }

    fun startGame() {
        _gameState.update { current ->
            current.copy(phase = GamePhase.Playing)
        }
    }

    /**
     * 完成 [count] 个目标，每个 +10 分。
     */
    fun addScore(count: Int) {
        _gameState.update { current ->
            current.copy(
                score = current.score + count * 10,
                completedTargets = current.completedTargets + count,
            )
        }
    }

    fun tick() {
        _gameState.update { current ->
            val nextTime = (current.timeRemaining - 1).coerceAtLeast(0)
            current.copy(
                timeRemaining = nextTime,
                phase = if (nextTime <= 0) GamePhase.Finished else current.phase,
            )
        }
    }

    fun reset() {
        _gameState.value = GameState()
        restartSession()
    }

    fun restartSession() {
        _sessionId.update { it + 1 }
    }
}
