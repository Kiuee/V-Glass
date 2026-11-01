package com.moji.liquidglass.ui.screen.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.component.SearchStatus

/** 覆盖层观感参数：两套界面风格各自解析成同一组语义色/字体/图标。 */
@Immutable
internal data class SearchOverlayStyle(
    val surface: Color,
    val fieldContainer: Color,
    val content: Color,
    val placeholder: Color,
    val cancel: Color,
    val hint: String,
    val textStyle: TextStyle,
    val searchIcon: ImageVector,
    val clearIcon: ImageVector,
)

/** 全屏搜索覆盖层（复刻参考项目 SearchPager）：搜索框从顶栏假框位置飞出到状态栏下方，背景淡入，右侧取消滑入。
 *  须常驻组合；收起态整体透明且不拦截输入。 */
@Composable
internal fun SearchStatus.SearchOverlay(
    onSearchStatusChange: (SearchStatus) -> Unit,
    style: SearchOverlayStyle,
    result: @Composable () -> Unit,
) {
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val topPadding by animateDpAsState(
        targetValue = if (shouldExpand()) systemBarsTopPadding + 5.dp else maxOf(offsetY, 0.dp),
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "searchTopPadding",
    ) { onSearchStatusChange(onAnimationComplete()) }
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (shouldExpand()) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "searchSurfaceAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
            .drawBehind { drawRect(color = style.surface.copy(alpha = surfaceAlpha)) }
            .then(
                if (!isCollapsed()) {
                    // 展开期间吞掉点击与横向拖动，防止误触内容/翻走 Pager
                    Modifier.pointerInput(Unit) { detectTapGestures { } }
                        .draggable(state = rememberDraggableState { }, orientation = Orientation.Horizontal)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isCollapsed()) {
                SearchField(
                    style = style,
                    onSearchStatusChange = onSearchStatusChange,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = isExpand() || isAnimatingExpand(),
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        fontWeight = FontWeight.Bold,
                        color = style.cancel,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 16.dp)
                            .clickable(enabled = isExpand()) {
                                onSearchStatusChange(copy(searchText = "", current = SearchStatus.Status.COLLAPSING))
                            },
                    )
                }
            }
        }
        AnimatedVisibility(visible = isExpand(), enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                result()
            }
        }
    }

    // 展开态系统返回 = 收起并清词（收起时交还给页面/应用本身）
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = !isCollapsed(),
        onBackCompleted = {
            onSearchStatusChange(copy(searchText = "", current = SearchStatus.Status.COLLAPSING))
        },
    )
}

/** 覆盖层顶部的真搜索框：圆角胶囊底 + 放大镜 + 清除按钮，展开动画落位后自动聚焦弹键盘。 */
@Composable
private fun SearchStatus.SearchField(
    style: SearchOverlayStyle,
    onSearchStatusChange: (SearchStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clearDescription = stringResource(R.string.apps_search_clear)
    LaunchedEffect(Unit) {
        if (isAnimatingExpand()) focusRequester.requestFocus()
    }
    Row(
        modifier = modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(45.dp)
                .clip(CircleShape)
                .background(style.fieldContainer)
                .focusRequester(focusRequester)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.searchIcon,
                contentDescription = null,
                tint = style.placeholder,
                modifier = Modifier.padding(end = 8.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchText.isEmpty()) {
                    Text(
                        text = style.hint,
                        style = style.textStyle,
                        color = style.placeholder,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = searchText,
                    onValueChange = { onSearchStatusChange(copy(searchText = it)) },
                    textStyle = style.textStyle.copy(color = style.content),
                    cursorBrush = SolidColor(style.cancel),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = searchText.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Icon(
                    imageVector = style.clearIcon,
                    contentDescription = clearDescription,
                    tint = style.placeholder,
                    modifier = Modifier
                        .clickable { onSearchStatusChange(copy(searchText = "")) }
                        .padding(4.dp),
                )
            }
        }
    }
}
