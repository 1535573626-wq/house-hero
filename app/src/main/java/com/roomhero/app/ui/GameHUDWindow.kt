package com.roomhero.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.WindowContainerScope
import com.roomhero.app.viewmodel.AppMode
import com.roomhero.app.viewmodel.GamePhase
import com.roomhero.app.viewmodel.GameStateManager

/**
 * 游戏主 HUD / 菜单窗口。
 *
 * 作为独立的 SpatialUI [WindowContainer] 显示在空中，支持手追踪/注视点交互。
 */
@Composable
fun WindowContainerScope.GameHUDWindow() {
    val gameState by GameStateManager.gameState.collectAsState()

    PicoTheme {
        Column(
            modifier = Modifier
                .windowConstraints(minWidth = 720.dp, minHeight = 480.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "House Hero", fontSize = 48.sp)

            // 模式选择（仅在未扫描/就绪阶段显示）
            if (gameState.phase == GamePhase.Scanning || gameState.phase == GamePhase.Ready) {
                ModeSelector(currentMode = gameState.mode)
            }

            when (gameState.mode) {
                AppMode.Clean -> CleanHUD(gameState)
                AppMode.Organize -> OrganizeHUD(gameState)
            }
        }
    }
}

@Composable
private fun ModeSelector(currentMode: AppMode) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = { GameStateManager.switchMode(AppMode.Clean) },
            enabled = currentMode != AppMode.Clean,
        ) {
            Text(text = "清洁模式", fontSize = 24.sp)
        }
        Button(
            onClick = { GameStateManager.switchMode(AppMode.Organize) },
            enabled = currentMode != AppMode.Organize,
        ) {
            Text(text = "整理模式", fontSize = 24.sp)
        }
    }
}

@Composable
private fun CleanHUD(gameState: com.roomhero.app.viewmodel.GameState) {
    when (gameState.phase) {
        GamePhase.Scanning -> {
            Text(text = "扫描地面中，请缓慢环顾四周…", fontSize = 28.sp)
        }
        GamePhase.Ready -> {
            Text(text = "已找到地面，共 ${gameState.totalTargets} 片彩带", fontSize = 24.sp)
            Button(onClick = { GameStateManager.startGame() }) {
                Text(text = "开始清洁", fontSize = 28.sp)
            }
        }
        GamePhase.Playing -> {
            Text(text = "时间: ${gameState.timeRemaining}s", fontSize = 32.sp)
            Text(text = "分数: ${gameState.score}", fontSize = 28.sp)
            Text(text = "清洁率: ${gameState.progressPercent}%", fontSize = 28.sp)
            val remaining = gameState.totalTargets - gameState.completedTargets
            Text(text = "剩余: $remaining", fontSize = 24.sp)
        }
        GamePhase.Finished -> {
            Text(text = "时间到！", fontSize = 36.sp)
            Text(text = "最终得分: ${gameState.score}", fontSize = 32.sp)
            Text(text = "清洁率: ${gameState.progressPercent}%", fontSize = 28.sp)
            Button(onClick = { GameStateManager.reset() }) {
                Text(text = "再玩一次", fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun OrganizeHUD(gameState: com.roomhero.app.viewmodel.GameState) {
    when (gameState.phase) {
        GamePhase.Scanning -> {
            Text(text = "扫描桌面中，请注视桌面…", fontSize = 28.sp)
        }
        GamePhase.Ready -> {
            Text(text = "已找到桌面，共 ${gameState.totalTargets} 件物品", fontSize = 24.sp)
            Text(text = "移动 HMD 光标把物品拖入虚线框", fontSize = 20.sp)
            Button(onClick = { GameStateManager.startGame() }) {
                Text(text = "开始整理", fontSize = 28.sp)
            }
        }
        GamePhase.Playing -> {
            Text(text = "时间: ${gameState.timeRemaining}s", fontSize = 32.sp)
            Text(text = "分数: ${gameState.score}", fontSize = 28.sp)
            Text(text = "整理进度: ${gameState.progressPercent}%", fontSize = 28.sp)
            val remaining = gameState.totalTargets - gameState.completedTargets
            Text(text = "剩余: $remaining", fontSize = 24.sp)
        }
        GamePhase.Finished -> {
            Text(text = "时间到！", fontSize = 36.sp)
            Text(text = "最终得分: ${gameState.score}", fontSize = 32.sp)
            Text(text = "整理进度: ${gameState.progressPercent}%", fontSize = 28.sp)
            Button(onClick = { GameStateManager.reset() }) {
                Text(text = "再玩一次", fontSize = 28.sp)
            }
        }
    }
}
