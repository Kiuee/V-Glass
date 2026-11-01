package com.moji.liquidglass.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 搜索状态机（复刻参考项目 KernelSU SearchStatus）：展开/收起是"进行中也有状态"的四态机，
 * 动画完成由覆盖层 animateDpAsState 的 finishedListener 回调 onAnimationComplete 推进，
 * 保证动画中途点击、返回手势、切页强制收起都落在正确状态。
 */
@Stable
data class SearchStatus(
    val label: String,
    val searchText: String = "",
    val current: Status = Status.COLLAPSED,
    /** 假搜索框在窗口中的 Y 坐标（全屏覆盖层飞出动画的起点）。 */
    val offsetY: Dp = 0.dp,
) {
    fun isExpand() = current == Status.EXPANDED

    fun isCollapsed() = current == Status.COLLAPSED

    fun shouldExpand() = current == Status.EXPANDED || current == Status.EXPANDING

    fun shouldCollapsed() = current == Status.COLLAPSED || current == Status.COLLAPSING

    fun isAnimatingExpand() = current == Status.EXPANDING

    fun onAnimationComplete(): SearchStatus = when (current) {
        Status.EXPANDING -> copy(current = Status.EXPANDED)
        Status.COLLAPSING -> copy(searchText = "", current = Status.COLLAPSED)
        else -> this
    }

    /** 顶栏内容随收起/展开淡入淡出：底层垫同色背景，内容 alpha 归零后只剩底色（或透出毛玻璃）。 */
    @Composable
    fun TopAppBarAnim(
        modifier: Modifier = Modifier,
        visible: Boolean = shouldCollapsed(),
        backgroundColor: Color,
        content: @Composable () -> Unit,
    ) {
        Box(modifier = modifier) {
            Box(Modifier.matchParentSize().background(backgroundColor))
            Box(Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }) {
                content()
            }
        }
    }

    enum class Status { EXPANDED, EXPANDING, COLLAPSED, COLLAPSING }
}
