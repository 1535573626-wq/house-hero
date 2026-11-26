package com.roomhero.app.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.foundation.content.SpatialView
import com.roomhero.app.manager.DirtManager
import com.roomhero.app.manager.OrganizeManager
import com.roomhero.app.manager.RoomSurfaceManager
import com.roomhero.app.manager.VacuumManager
import com.roomhero.app.viewmodel.AppMode
import com.roomhero.app.viewmodel.GameStateManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun GameStage() {
    val sessionId by GameStateManager.sessionId.collectAsState()
    var hasPaused by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasPaused) {
                        hasPaused = false
                        GameStateManager.restartSession()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    key(sessionId) {
        MainStageContent()
    }
}

@Composable
private fun MainStageContent() {
    val mode = GameStateManager.gameState.value.mode
    when (mode) {
        AppMode.Clean -> CleanModeStage()
        AppMode.Organize -> OrganizeModeStage()
    }
}

@Composable
private fun CleanModeStage() {
    SpatialView(
        initial = { content, _ ->
            content.addEntity(DirtManager.root)
            content.addEntity(VacuumManager.root)

            val surface = RoomSurfaceManager.awaitFloor(timeoutMillis = 5000L)
            DirtManager.spawnDirtGrid(surface)
            VacuumManager.start()
            GameStateManager.setReady(DirtManager.totalCount())

            coroutineScope {
                launch {
                    while (isActive) {
                        val state = GameStateManager.gameState.value
                        if (state.isPlaying) {
                            val vacuumPos = VacuumManager.currentPosition
                            val cleaned = DirtManager.cleanAt(vacuumPos, radius = 0.25f)
                            if (cleaned > 0) {
                                GameStateManager.addScore(cleaned)
                            }
                        }
                        delay(50)
                    }
                }

                launch {
                    while (isActive) {
                        val state = GameStateManager.gameState.value
                        if (state.isPlaying && state.timeRemaining > 0) {
                            delay(1000)
                            GameStateManager.tick()
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            DirtManager.reset()
            VacuumManager.stop()
            RoomSurfaceManager.stop()
        }
    )
}

@Composable
private fun OrganizeModeStage() {
    SpatialView(
        initial = { content, _ ->
            content.addEntity(OrganizeManager.root)
            content.addEntity(VacuumManager.root)

            val surface = RoomSurfaceManager.awaitTable(timeoutMillis = 5000L)
            OrganizeManager.spawnItems(surface)
            VacuumManager.start()
            GameStateManager.setReady(OrganizeManager.totalCount())

            coroutineScope {
                launch {
                    while (isActive) {
                        val state = GameStateManager.gameState.value
                        if (state.isPlaying) {
                            val cursorPos = VacuumManager.currentPosition
                            val organized = OrganizeManager.checkPlacement(cursorPos)
                            if (organized > 0) {
                                GameStateManager.addScore(organized)
                            }
                        }
                        delay(50)
                    }
                }

                launch {
                    while (isActive) {
                        val state = GameStateManager.gameState.value
                        if (state.isPlaying && state.timeRemaining > 0) {
                            delay(1000)
                            GameStateManager.tick()
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            OrganizeManager.reset()
            VacuumManager.stop()
            RoomSurfaceManager.stop()
        }
    )
}
