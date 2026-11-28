package com.roomhero.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.roomhero.app.manager.Achievement
import com.roomhero.app.manager.AchievementManager

/**
 * 成就面板窗口。
 *
 * 以独立 [WindowContainer] 显示，列出所有成就及解锁状态。
 */
@Composable
fun WindowContainerScope.AchievementWindow(
    onClose: () -> Unit = {},
) {
    val achievements = AchievementManager.allAchievements()

    PicoTheme {
        Column(
            modifier = Modifier
                .windowConstraints(minWidth = 560.dp, minHeight = 640.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "成就墙", fontSize = 40.sp)

            Text(
                text = "已解锁 ${achievements.count { it.second }} / ${achievements.size}",
                fontSize = 24.sp,
            )

            achievements.forEach { (achievement, unlocked) ->
                AchievementRow(achievement, unlocked)
            }

            Button(onClick = onClose) {
                Text(text = "关闭", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun AchievementRow(
    achievement: Achievement,
    unlocked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${if (unlocked) "✅" else "🔒"} ${achievement.title}",
                fontSize = 24.sp,
            )
            Text(
                text = achievement.description,
                fontSize = 18.sp,
            )
        }
    }
}
