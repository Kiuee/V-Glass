package com.moji.liquidglass.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

/*
 * 一比一移植自 Kyant0/AndroidLiquidGlass（kmp 分支）
 * app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTab.kt
 */
internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                // 不设 role：Role.Tab 会让 Compose a11y 委托经宿主 Resources 解析
                // androidx.compose.ui 的 string/tab（角色描述）——宿主资源表没有该 id，
                // 无障碍遍历即 Resources$NotFoundException 崩溃宿主（商店启动闪退根因）。
                // 玻璃栏是装饰层（真实交互经 performClick 转发给原生底栏），无需角色语义。
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
