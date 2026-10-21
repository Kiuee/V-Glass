package com.moji.liquidglass.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * 拖动液泡悬停预览的 tab 下标（-1 = 无预览）。由 LiquidBottomTabs 提供（拖动中
 * targetValue ≠ currentIndex 时为悬停下标），内容层据此绘制悬停反馈（如标签强调色）
 * ——与宿主 previewWholeTab/previewTabChange 同一语义来源，替代旧"折射副本整层
 * 染色"承担的悬停高亮（vivo 官网等关闭染色的应用依赖此通道）。
 */
val LocalTabPreviewIndex = staticCompositionLocalOf<State<Int>> { mutableIntStateOf(-1) }

/**
 * 液泡覆盖槽位判定（index → 该槽位当前是否被液泡覆盖）。由 LiquidBottomTabs 在可见
 * 内容行外提供（判定与液泡 graphicsLayer 同一动画状态源，绘制相读取、不触发重组），
 * GlassRoot 按 descriptor.suppressBubbleCoveredSlots 声明消费：被覆盖的可见槽位整槽
 * 不绘制，泡内折射副本成为唯一图标来源（重影修复，§1.2）。默认实现恒 false——
 * 无人声明 = 引擎渲染逐位不变。
 */
val LocalLiquidBottomTabBubbleCovered =
    staticCompositionLocalOf<(index: Int) -> Boolean> { { false } }

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
