package com.roomhero.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val GAME_DURATION_SECONDS = 60

/**
 * 游戏阶段。
 */
enum class GamePhase {
    /** 初始/扫描地面中 */
    Scanning,
    /** 扫描完成，等待用户开始 */
    Ready,
    /** 游戏中 */
    Playing,
    /** 游戏结束 */
    Finished,
}

/**
 * 游戏状态数据类
 */
data class GameState(
    val phase: GamePhase = GamePhase.Scanning,
    val timeRemaining: Int = GAME_DURATION_SECONDS,
    val score: Int = 0,
    val totalConfetti: Int = 0,
    val cleanedConfetti: Int = 0,
) {
    val isPlaying: Boolean get() = phase == GamePhase.Playing
    val cleanPercent: Int
        get() = if (totalConfetti > 0) (cleanedConfetti * 100 / totalConfetti) else 0
}

/**
 * 单例游戏状态管理器。
 *
 * 同时被 [com.roomhero.app.content.GameStage] 和 [com.roomhero.app.ui.GameHUDWindow] 访问，
 * 作为空间舞台与 SpatialUI 窗口之间的共享状态源。
 */
object GameStateManager {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId

    fun setReady(totalConfetti: Int) {
        _gameState.value = GameState(
            phase = GamePhase.Ready,
            timeRemaining = GAME_DURATION_SECONDS,
            score = 0,
            totalConfetti = totalConfetti,
            cleanedConfetti = 0,
        )
    }

    fun startGame() {
        _gameState.update { current ->
            current.copy(phase = GamePhase.Playing)
        }
    }

    fun addScore(cleanedCount: Int) {
        _gameState.update { current ->
            current.copy(
                score = current.score + cleanedCount * 10,
                cleanedConfetti = current.cleanedConfetti + cleanedCount,
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
