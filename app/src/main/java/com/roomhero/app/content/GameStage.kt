package com.roomhero.app.content

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.openWindowContainer
import com.roomhero.app.manager.DirtManager
import com.roomhero.app.manager.FeedbackManager
import com.roomhero.app.manager.FurnitureAnchorManager
import com.roomhero.app.manager.FurnitureManager
import com.roomhero.app.manager.HandTrackingManager
import com.roomhero.app.manager.OrganizeManager
import com.roomhero.app.manager.RewardManager
import com.roomhero.app.manager.RoomSurfaceManager
import com.roomhero.app.manager.VacuumManager
import com.roomhero.app.viewmodel.AppMode
import com.roomhero.app.viewmodel.GamePhase
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
    val context = LocalContext.current

    // Stage 启动后自动打开 HUD 窗口
    LaunchedEffect(Unit) {
        context.openWindowContainer("HUD")
    }

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
        AppMode.Furniture -> FurnitureModeStage()
    }
}

private val AnimationDt = 1f / 60f

/**
 * 处理手部捏合“确认”事件。
 *
 * - Ready 阶段：捏合 = 开始游戏
 * - Playing 阶段：仅家具模式且正抓取时，捏合 = 放下家具
 * - Finished 阶段：捏合 = 再玩一次
 */
private fun processHandClick() {
    if (!HandTrackingManager.onPinchDown) return

    val state = GameStateManager.gameState.value
    when (state.phase) {
        GamePhase.Ready -> GameStateManager.startGame()
        GamePhase.Playing -> {
            if (state.mode == AppMode.Furniture && FurnitureManager.isGrabbing()) {
                FurnitureManager.release()
            }
        }
        GamePhase.Finished -> GameStateManager.reset()
        else -> {}
    }
}

@Composable
private fun CleanModeStage() {
    SpatialView(
        update = { _, _ ->
            processHandClick()
            FeedbackManager.updateAnimations(AnimationDt)
            RewardManager.update(AnimationDt)
        },
        initial = { content, _ ->
            content.addEntity(DirtManager.root)
            content.addEntity(VacuumManager.root)
            content.addEntity(RewardManager.root)

            val surface = RoomSurfaceManager.awaitFloor(timeoutMillis = 5000L)
            DirtManager.spawnDirtGrid(surface)
            HandTrackingManager.start()
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
                        val previous = GameStateManager.gameState.value
                        if (previous.isPlaying && previous.timeRemaining > 0) {
                            delay(1000)
                            GameStateManager.tick()
                            val current = GameStateManager.gameState.value
                            if (!previous.isFinished && current.isFinished) {
                                FeedbackManager.playFinish(VacuumManager.currentPosition)
                                RewardManager.prepare(
                                    score = current.score,
                                    progress = current.progressPercent,
                                    hmdPosition = VacuumManager.currentPosition,
                                    floorY = surface.floorY,
                                )
                            }
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            DirtManager.reset()
            VacuumManager.stop()
            HandTrackingManager.stop()
            RewardManager.reset()
            RoomSurfaceManager.stop()
        }
    )
}

@Composable
private fun OrganizeModeStage() {
    SpatialView(
        update = { _, _ ->
            processHandClick()
            FeedbackManager.updateAnimations(AnimationDt)
            RewardManager.update(AnimationDt)
        },
        initial = { content, _ ->
            content.addEntity(OrganizeManager.root)
            content.addEntity(VacuumManager.root)
            content.addEntity(RewardManager.root)

            val surface = RoomSurfaceManager.awaitTable(timeoutMillis = 5000L)
            OrganizeManager.spawnItems(surface)
            HandTrackingManager.start()
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
                        val previous = GameStateManager.gameState.value
                        if (previous.isPlaying && previous.timeRemaining > 0) {
                            delay(1000)
                            GameStateManager.tick()
                            val current = GameStateManager.gameState.value
                            if (!previous.isFinished && current.isFinished) {
                                FeedbackManager.playFinish(VacuumManager.currentPosition)
                                RewardManager.prepare(
                                    score = current.score,
                                    progress = current.progressPercent,
                                    hmdPosition = VacuumManager.currentPosition,
                                    floorY = surface.position.y,
                                )
                            }
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            OrganizeManager.reset()
            VacuumManager.stop()
            HandTrackingManager.stop()
            RewardManager.reset()
            RoomSurfaceManager.stop()
        }
    )
}


@Composable
private fun FurnitureModeStage() {
    SpatialView(
        update = { _, _ ->
            processHandClick()
            FeedbackManager.updateAnimations(AnimationDt)
            RewardManager.update(AnimationDt)
        },
        initial = { content, _ ->
            content.addEntity(FurnitureManager.root)
            content.addEntity(VacuumManager.root)
            content.addEntity(RewardManager.root)

            val surface = RoomSurfaceManager.awaitFloor(timeoutMillis = 5000L)
            val savedLayout = FurnitureAnchorManager.loadLayout()
            FurnitureManager.spawnFurniture(surface, savedLayout)
            HandTrackingManager.start()
            VacuumManager.start()
            GameStateManager.setReady(0)

            coroutineScope {
                launch {
                    while (isActive) {
                        val state = GameStateManager.gameState.value
                        if (state.isPlaying) {
                            val cursorPos = VacuumManager.currentPosition
                            FurnitureManager.checkDrag(cursorPos)
                        }
                        delay(50)
                    }
                }

                launch {
                    while (isActive) {
                        val previous = GameStateManager.gameState.value
                        if (previous.isPlaying && previous.timeRemaining > 0) {
                            delay(1000)
                            GameStateManager.tick()
                            val current = GameStateManager.gameState.value
                            if (!previous.isFinished && current.isFinished) {
                                FeedbackManager.playFinish(VacuumManager.currentPosition)
                                FurnitureAnchorManager.saveLayout(FurnitureManager.snapshot())
                                RewardManager.prepare(
                                    score = current.score,
                                    progress = current.progressPercent,
                                    hmdPosition = VacuumManager.currentPosition,
                                    floorY = surface.floorY,
                                )
                            }
                        } else {
                            delay(100)
                        }
                    }
                }
            }

            FurnitureManager.reset()
            VacuumManager.stop()
            HandTrackingManager.stop()
            RewardManager.reset()
            RoomSurfaceManager.stop()
        }
    )
}
