package com.roomhero.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.roomhero.app.viewmodel.GamePhase
import com.roomhero.app.viewmodel.GameStateManager

/**
 * 游戏主 HUD / 菜单窗口。
 *
 * 作为独立的 SpatialUI [WindowContainer] 显示在空中，支持手追踪/注视点交互：
 * - 扫描阶段：提示"扫描地面中…"
 * - 准备阶段：显示"开始清洁"按钮
 * - 游戏阶段：显示时间、分数、清洁率
 * - 结束阶段：显示最终得分与"再玩一次"按钮
 */
@Composable
fun WindowContainerScope.GameHUDWindow() {
    val gameState by GameStateManager.gameState.collectAsState()

    PicoTheme {
        Column(
            modifier = Modifier
                .windowConstraints(minWidth = 640.dp, minHeight = 360.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "House Hero", fontSize = 48.sp)

            when (gameState.phase) {
                GamePhase.Scanning -> {
                    Text(text = "扫描地面中，请缓慢环顾四周…", fontSize = 28.sp)
                }
                GamePhase.Ready -> {
                    Text(text = "已找到地面，共 ${gameState.totalConfetti} 片彩带", fontSize = 24.sp)
                    Button(onClick = { GameStateManager.startGame() }) {
                        Text(text = "开始清洁", fontSize = 28.sp)
                    }
                }
                GamePhase.Playing -> {
                    Text(text = "时间: ${gameState.timeRemaining}s", fontSize = 32.sp)
                    Text(text = "分数: ${gameState.score}", fontSize = 28.sp)
                    Text(text = "清洁率: ${gameState.cleanPercent}%", fontSize = 28.sp)
                    val remaining = gameState.totalConfetti - gameState.cleanedConfetti
                    Text(text = "剩余: $remaining", fontSize = 24.sp)
                }
                GamePhase.Finished -> {
                    Text(text = "时间到！", fontSize = 36.sp)
                    Text(text = "最终得分: ${gameState.score}", fontSize = 32.sp)
                    Text(text = "清洁率: ${gameState.cleanPercent}%", fontSize = 28.sp)
                    Button(onClick = { GameStateManager.reset() }) {
                        Text(text = "再玩一次", fontSize = 28.sp)
                    }
                }
            }
        }
    }
}
