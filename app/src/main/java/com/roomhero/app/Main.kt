package com.roomhero.app

import com.roomhero.app.content.GameStage
import com.roomhero.app.ui.GameHUDWindow
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // 主游戏舞台：Mixed MR 3D 内容
        DefaultStage {
            PicoTheme {
                GameStage()
            }
        }

        // 悬浮 HUD / 菜单窗口：支持手追踪交互
        DefaultWindowContainer {
            PicoTheme {
                GameHUDWindow()
            }
        }
    }
