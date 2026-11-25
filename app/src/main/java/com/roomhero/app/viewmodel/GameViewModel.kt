package com.roomhero.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val GAME_DURATION_SECONDS = 60

/**
 * 游戏状态数据类
 */
data class GameState(
    val isPlaying: Boolean = false,
    val timeRemaining: Int = GAME_DURATION_SECONDS,
    val score: Int = 0,
    val totalDirt: Int = 0,
    val cleanedDirt: Int = 0,
) {
    val cleanPercent: Int
        get() = if (totalDirt > 0) (cleanedDirt * 100 / totalDirt) else 0
}

/**
 * 纯 ViewModel，管理 Room Hero 的游戏状态。
 * 不依赖 Android ViewModel，便于在 Spatial 场景中直接使用。
 */
class GameViewModel {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    fun startGame(totalDirt: Int = 100) {
        _gameState.value = GameState(
            isPlaying = true,
            timeRemaining = GAME_DURATION_SECONDS,
            score = 0,
            totalDirt = totalDirt,
            cleanedDirt = 0,
        )
    }

    fun addScore(cleanedCount: Int) {
        _gameState.update { current ->
            current.copy(
                score = current.score + cleanedCount * 10,
                cleanedDirt = current.cleanedDirt + cleanedCount,
            )
        }
    }

    fun tick() {
        _gameState.update { current ->
            val nextTime = (current.timeRemaining - 1).coerceAtLeast(0)
            current.copy(
                timeRemaining = nextTime,
                isPlaying = nextTime > 0 && current.isPlaying,
            )
        }
    }

    fun endGame() {
        _gameState.update { it.copy(isPlaying = false) }
    }

    fun reset() {
        _gameState.value = GameState()
    }
}
