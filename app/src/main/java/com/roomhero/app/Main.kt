package com.roomhero.app

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.roomhero.app.content.GameStage
import com.roomhero.app.ui.AchievementWindow
import com.roomhero.app.ui.GameHUDWindow
import com.roomhero.app.ui.HelpWindow

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // 主游戏舞台：Mixed MR 3D 内容（使用命名 Stage，避免与 WindowContainer 默认入口冲突）
        Stage(id = "GameStage") {
            PicoTheme {
                GameStage()
            }
        }

        // 悬浮 HUD / 菜单窗口：支持手追踪交互
        WindowContainer(id = "HUD") {
            PicoTheme {
                GameHUDWindow()
            }
        }

        // 成就面板窗口
        WindowContainer(
            id = "Achievements",
            defaultSize = WindowContainerSize(width = 560.dp, height = 720.dp),
        ) {
            PicoTheme {
                AchievementWindow()
            }
        }

        // 帮助 / 玩法说明窗口
        WindowContainer(
            id = "Help",
            defaultSize = WindowContainerSize(width = 640.dp, height = 760.dp),
        ) {
            PicoTheme {
                HelpWindow()
            }
        }
    }
