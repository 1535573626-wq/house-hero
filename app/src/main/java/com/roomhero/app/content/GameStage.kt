package com.roomhero.app.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.roomhero.app.manager.DirtManager
import com.roomhero.app.manager.VacuumManager
import com.roomhero.app.viewmodel.GameViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun GameStage() {
    val viewModel = remember { GameViewModel() }
    val gameState by viewModel.gameState.collectAsState()

    var restartKey by remember { mutableIntStateOf(0) }
    var hasPaused by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasPaused) {
                        hasPaused = false
                        restartKey += 1
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    key(restartKey) {
        MainStageContent(viewModel)
    }

    if (!gameState.isPlaying && gameState.timeRemaining == 60) {
        StartScreen(onStart = { viewModel.startGame(DirtManager.totalCount()) })
    }
}

@Composable
private fun StartScreen(onStart: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "Room Hero")
        Text(text = "戴上 PICO，把枯燥家务变成游戏")
        Button(onClick = onStart) {
            Text("开始清洁")
        }
    }
}

@Composable
private fun MainStageContent(viewModel: GameViewModel) {
    SpatialView(
        initial = { content, _ ->
            content.addEntity(DirtManager.root)
            content.addEntity(VacuumManager.root)

            DirtManager.spawnDirtGrid(content)
            VacuumManager.start(content)

            coroutineScope {
                // 清洁循环：每 50ms 检查一次吸尘器与污渍的距离
                launch {
                    while (isActive) {
                        val state = viewModel.gameState.value
                        if (state.isPlaying) {
                            val vacuumPos = VacuumManager.currentPosition
                            val cleaned = DirtManager.cleanAt(vacuumPos, radius = 0.25f)
                            if (cleaned > 0) {
                                viewModel.addScore(cleaned)
                            }
                        }
                        delay(50)
                    }
                }

                // 计时循环：每秒倒计时
                launch {
                    while (isActive) {
                        val state = viewModel.gameState.value
                        if (state.isPlaying && state.timeRemaining > 0) {
                            delay(1000)
                            viewModel.tick()
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            DirtManager.reset()
            VacuumManager.stop()
        }
    )

    // HUD overlay
    GameHUD(viewModel)
}

@Composable
private fun GameHUD(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "时间: ${gameState.timeRemaining}s")
        Text(text = "分数: ${gameState.score}")
        Text(text = "清洁率: ${gameState.cleanPercent}%")
    }
}
