package com.moji.liquidglass.ui.screen.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.SearchStatus
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 应用页 Miuix 版：顶栏内嵌假搜索框（折叠联动）+ 全屏搜索覆盖层 + 总开关 + 未适配红条（可关）
 *  + 逐应用行（图标/应用名/包名/适配状态标签/勾选，整行点击展开版本适配信息）。 */
@Composable
internal fun AppsPagerMiuix(
    bottomInnerPadding: Dp,
    config: ConfigController,
    apps: List<TargetAppInfo>,
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    expandedPkgs: List<String>,
    onToggleExpand: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    // 顶栏折叠比例驱动假搜索框上移贴紧标题（对齐参考项目）
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val filtered = remember(apps, searchStatus.searchText) {
        filterApps(apps, searchStatus.searchText)
    }
    val unadaptedCount = apps.count { it.adaptState == AdaptState.NotAdapted }
    val showUnadaptedBar = unadaptedCount > 0 &&
        ManagerPrefs.unadaptedDismissed != unadaptedSignature(apps)

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.apps),
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            FakeSearchField(
                                label = searchStatus.label,
                                topPadding = dynamicTopPadding,
                                active = searchStatus.isCollapsed(),
                                onPositioned = { offsetY ->
                                    if (searchStatus.offsetY != offsetY) {
                                        onSearchStatusChange(searchStatus.copy(offsetY = offsetY))
                                    }
                                },
                                onClick = {
                                    onSearchStatusChange(
                                        searchStatus.copy(current = SearchStatus.Status.EXPANDING),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchOverlay(
                onSearchStatusChange = onSearchStatusChange,
                style = SearchOverlayStyle(
                    surface = colorScheme.surface,
                    fieldContainer = colorScheme.surfaceContainerHigh,
                    content = colorScheme.onSurface,
                    placeholder = colorScheme.onSurfaceVariantSummary,
                    cancel = colorScheme.primary,
                    hint = searchStatus.label,
                    textStyle = TextStyle(fontSize = 17.sp),
                    searchIcon = MiuixIcons.Basic.Search,
                    clearIcon = MiuixIcons.Basic.SearchCleanup,
                ),
            ) {
                // 无关键词不透出应用列表（对齐参考项目搜索态）
                if (searchStatus.searchText.isNotBlank()) {
                    SearchResultList(
                        filtered = filtered,
                        bottomInnerPadding = bottomInnerPadding,
                        expandedPkgs = expandedPkgs,
                        onToggleExpand = onToggleExpand,
                        config = config,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            // 搜索展开期间不渲染主列表（对齐参考项目 SearchBox 门控）
            if (searchStatus.shouldCollapsed()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = innerPadding,
                    overscrollEffect = null,
                ) {
                    item {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                SwitchPreference(
                                    title = stringResource(R.string.enable_glass_bar),
                                    summary = stringResource(R.string.enable_glass_bar_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Apps,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = null,
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                    checked = config.config.enabled,
                                    enabled = ServiceBridge.bound,
                                    onCheckedChange = { checked ->
                                        config.commit(config.config.copy(enabled = checked))
                                    },
                                )
                            }

                            if (showUnadaptedBar) {
                                WarningCard(
                                    message = stringResource(R.string.apps_unadapted_warning, unadaptedCount),
                                    level = WarningLevel.Error,
                                    action = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.apps_unadapted_dismiss),
                                            modifier = Modifier
                                                .clickable { ManagerPrefs.setUnadaptedDismissed(unadaptedSignature(apps)) }
                                                .padding(2.dp)
                                                .size(20.dp),
                                        )
                                    },
                                )
                            }

                            apps.forEach { app ->
                                AppItemMiuix(
                                    app = app,
                                    expanded = app.packageName in expandedPkgs,
                                    onToggleExpand = { onToggleExpand(app.packageName) },
                                    checked = config.config.appEnabledFor(app.packageName),
                                    checkboxEnabled = ServiceBridge.bound && config.config.enabled,
                                    onCheckedChange = { checked ->
                                        config.commit(
                                            config.config.copy(
                                                appEnabled = config.config.appEnabled + (app.packageName to checked)
                                            )
                                        )
                                    },
                                )
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.apps_note),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }

                            Spacer(Modifier.height(bottomInnerPadding))
                        }
                    }
                }
            }
        }
    }
}

/** 按应用名/包名过滤（大小写不敏感包含匹配）。 */
internal fun filterApps(apps: List<TargetAppInfo>, query: String): List<TargetAppInfo> {
    if (query.isBlank()) return apps
    return apps.filter {
        it.displayName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }
}

/** 顶栏内嵌的假搜索框（miuix InputField 禁用态占位）：仅收起态可点、记录窗口 Y 坐标作飞出起点。 */
@Composable
private fun FakeSearchField(
    label: String,
    topPadding: Dp,
    active: Boolean,
    onPositioned: (Dp) -> Unit,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .alpha(if (active) 1f else 0f)
            .then(
                if (active) {
                    Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }
                } else {
                    Modifier
                },
            ),
    ) {
        InputField(
            query = "",
            onQueryChange = { },
            onSearch = { },
            expanded = false,
            onExpandedChange = { },
            label = label,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = 6.dp, start = 12.dp, end = 12.dp)
                .onGloballyPositioned { coordinates ->
                    with(density) { onPositioned(coordinates.positionInWindow().y.toDp()) }
                },
        )
    }
}

/** 搜索结果列表（覆盖层内容）：空结果显示空态卡，否则渲染应用行。 */
@Composable
private fun SearchResultList(
    filtered: List<TargetAppInfo>,
    bottomInnerPadding: Dp,
    expandedPkgs: List<String>,
    onToggleExpand: (String) -> Unit,
    config: ConfigController,
) {
    if (filtered.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.apps_search_empty),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        return
    }
    // 列表视口满屏，底部余量放进滚动 contentPadding：内容可滚入底栏/键盘下方区域
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = maxOf(bottomInnerPadding, imeBottom) + 12.dp,
        ),
        overscrollEffect = null,
    ) {
        items(filtered, key = { it.packageName }) { app ->
            AppItemMiuix(
                app = app,
                expanded = app.packageName in expandedPkgs,
                onToggleExpand = { onToggleExpand(app.packageName) },
                checked = config.config.appEnabledFor(app.packageName),
                checkboxEnabled = ServiceBridge.bound && config.config.enabled,
                onCheckedChange = { checked ->
                    config.commit(
                        config.config.copy(
                            appEnabled = config.config.appEnabled + (app.packageName to checked)
                        )
                    )
                },
            )
        }
    }
}

/** 应用行：图标 + 应用名/包名 + 状态标签 + 展开箭头 + 勾选框；整行点击展开版本适配信息。 */
@Composable
private fun AppItemMiuix(
    app: TargetAppInfo,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    checked: Boolean,
    checkboxEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val expandable = app.adaptedVersions.isNotEmpty()
    val expandLabel = stringResource(
        if (expanded) R.string.apps_hide_adapted_versions else R.string.apps_view_adapted_versions
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = app.displayName,
            summary = app.packageName,
            startAction = {
                AppIconImage(
                    packageName = app.packageName,
                    contentDescription = app.displayName,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(48.dp),
                )
            },
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptStatusTag(app.adaptState)
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = if (checkboxEnabled) {
                            { onCheckedChange(!checked) }
                        } else {
                            null
                        },
                        enabled = checkboxEnabled,
                    )
                }
            },
            onClick = if (expandable) onToggleExpand else null,
            onClickLabel = if (expandable) expandLabel else null,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AdaptedVersionsContent(app)
        }
    }
}

/** 展开区："已适配版本"逐版本行 + 当前安装版本。 */
@Composable
private fun AdaptedVersionsContent(app: TargetAppInfo) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        Text(
            text = stringResource(R.string.adapted_versions_title),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        app.adaptedVersions.forEach { version ->
            Text(
                text = version,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = installedVersionLine(app),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}
