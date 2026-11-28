package com.roomhero.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.WindowContainerScope

/**
 * 帮助 / 玩法说明窗口。
 *
 * 介绍三种模式的操作方式与评分规则。
 */
@Composable
fun WindowContainerScope.HelpWindow(
    onClose: () -> Unit = {},
) {
    PicoTheme {
        Column(
            modifier = Modifier
                .windowConstraints(minWidth = 640.dp, minHeight = 720.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "玩法说明", fontSize = 40.sp)

            Text(text = "🧹 清洁模式", fontSize = 28.sp)
            Text(
                text = "扫描地面后，彩带会覆盖真实地板。移动头部光标" +
                    "（或手持吸尘器靠近）即可吸走彩带，限时内清完得分。",
                fontSize = 20.sp,
            )

            Text(text = "📦 整理模式", fontSize = 28.sp)
            Text(
                text = "扫描桌面后，物品会出现在真实桌面上。把物品拖入" +
                    "虚线框完成整理，全部归位即获胜。",
                fontSize = 20.sp,
            )

            Text(text = "🛋️ 摆家具模式", fontSize = 28.sp)
            Text(
                text = "自由摆放家具模型，提前预览房间布局。抓起家具" +
                    "移动，捏合放下，满意后自动保存布局。",
                fontSize = 20.sp,
            )

            Text(text = "👌 手势提示", fontSize = 28.sp)
            Text(
                text = "伸出右手，拇指与食指捏合即可确认/开始/再玩一次。" +
                    "做家务时也可直接用手点击窗口按钮。",
                fontSize = 20.sp,
            )

            Button(onClick = onClose) {
                Text(text = "知道了", fontSize = 24.sp)
            }
        }
    }
}
