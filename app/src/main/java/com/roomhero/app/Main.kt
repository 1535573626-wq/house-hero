package com.roomhero.app

import com.roomhero.app.content.GameStage
import com.roomhero.app.ui.GameHUDWindow
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.ui.foundation.dsl.WindowContainer

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
    }
