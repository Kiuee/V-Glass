@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.SearchStatus
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.component.material.SegmentedAppCheckboxItem
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem
import com.moji.liquidglass.ui.component.material.SegmentedSwitchItem
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 应用页 Material 版：顶栏（大标题折叠）正下方内嵌搜索触发框 + 全屏搜索覆盖层 + 总开关
 *  + 未适配红条（可关）+ 逐应用行（图标/应用名/包名/适配状态标签/勾选，整行点击展开版本适配信息）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsPagerMaterial(
    bottomInnerPadding: Dp,
    config: ConfigController,
    apps: List<TargetAppInfo>,
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    expandedPkgs: List<String>,
    onToggleExpand: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val filtered = remember(apps, searchStatus.searchText) {
        filterApps(apps, searchStatus.searchText)
    }
    val unadaptedCount = apps.count { it.adaptState == AdaptState.NotAdapted }
    val showUnadaptedBar = unadaptedCount > 0 &&
        ManagerPrefs.unadaptedDismissed != unadaptedSignature(apps)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                // 搜索触发框与顶栏同一 Surface 绑定，不随列表滚走（对齐参考项目 SearchAppBar 结构）
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        LargeFlexibleTopAppBar(
                            title = { Text(stringResource(R.string.apps)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            scrollBehavior = scrollBehavior,
                        )
                        SearchTriggerField(
                            label = searchStatus.label,
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
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SegmentedColumn(
                    title = stringResource(R.string.feature_group),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Apps,
                                title = stringResource(R.string.enable_glass_bar),
                                summary = stringResource(R.string.enable_glass_bar_summary),
                                checked = config.config.enabled,
                                enabled = ServiceBridge.bound,
                                onCheckedChange = { checked ->
                                    config.commit(config.config.copy(enabled = checked))
                                },
                            )
                        },
                    ),
                )

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
                                    .padding(start = 8.dp),
                            )
                        },
                    )
                }

                apps.forEach { app ->
                    AppGroupMaterial(
                        app = app,
                        expanded = app.packageName in expandedPkgs,
                        onToggleExpand = onToggleExpand,
                        config = config,
                    )
                }

                SegmentedColumn(
                    content = listOf(
                        { SegmentedNoteItem(text = stringResource(R.string.apps_note)) },
                    ),
                )

                Spacer(Modifier.height(bottomInnerPadding))
            }
        }

        searchStatus.SearchOverlay(
            onSearchStatusChange = onSearchStatusChange,
            style = SearchOverlayStyle(
                surface = MaterialTheme.colorScheme.surface,
                fieldContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface,
                placeholder = MaterialTheme.colorScheme.onSurfaceVariant,
                cancel = MaterialTheme.colorScheme.primary,
                hint = searchStatus.label,
                textStyle = MaterialTheme.typography.bodyLarge,
                searchIcon = Icons.Filled.Search,
                clearIcon = Icons.Filled.Close,
            ),
        ) {
            // 无关键词不透出应用列表（对齐参考项目搜索态）
            if (searchStatus.searchText.isNotBlank()) {
                SearchResultListMaterial(
                    filtered = filtered,
                    bottomInnerPadding = bottomInnerPadding,
                    expandedPkgs = expandedPkgs,
                    onToggleExpand = onToggleExpand,
                    config = config,
                )
            }
        }
    }
}

/** 顶栏下方的搜索触发框（占位样式，非真输入框）：记录窗口 Y 坐标作覆盖层飞出起点，仅收起态可点。 */
@Composable
private fun SearchTriggerField(
    label: String,
    active: Boolean,
    onPositioned: (Dp) -> Unit,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 13.dp)
            .onGloballyPositioned { coordinates ->
                with(density) { onPositioned(coordinates.positionInWindow().y.toDp()) }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .alpha(if (active) 1f else 0f)
                .then(
                    if (active) {
                        Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
    }
}

/** 搜索结果列表（覆盖层内容）：空结果显示空态组，否则渲染应用行。 */
@Composable
private fun SearchResultListMaterial(
    filtered: List<TargetAppInfo>,
    bottomInnerPadding: Dp,
    expandedPkgs: List<String>,
    onToggleExpand: (String) -> Unit,
    config: ConfigController,
) {
    if (filtered.isEmpty()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            SegmentedColumn(
                content = listOf(
                    { SegmentedNoteItem(text = stringResource(R.string.apps_search_empty)) },
                ),
            )
        }
        return
    }
    // 列表视口满屏，底部余量放进滚动 contentPadding：内容可滚入底栏/键盘下方区域
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = maxOf(bottomInnerPadding, imeBottom) + 12.dp,
        ),
    ) {
        items(filtered, key = { it.packageName }) { app ->
            AppGroupMaterial(
                app = app,
                expanded = app.packageName in expandedPkgs,
                onToggleExpand = onToggleExpand,
                config = config,
            )
        }
    }
}

/** 应用组：单条目分组卡（图标 + 应用名/包名 + 状态标签 + 展开箭头 + 勾选框），整行点击展开版本适配信息。 */
@Composable
private fun AppGroupMaterial(
    app: TargetAppInfo,
    expanded: Boolean,
    onToggleExpand: (String) -> Unit,
    config: ConfigController,
) {
    val expandable = app.adaptedVersions.isNotEmpty()
    SegmentedColumn(
        content = buildList {
            add(
                {
                    SegmentedAppCheckboxItem(
                        title = app.displayName,
                        summary = app.packageName,
                        leading = {
                            AppIconImage(
                                packageName = app.packageName,
                                contentDescription = app.displayName,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        trailingContent = {
                            AdaptStatusTag(app.adaptState)
                        },
                        checked = config.config.appEnabledFor(app.packageName),
                        checkboxEnabled = ServiceBridge.bound && config.config.enabled,
                        onClick = if (expandable) {
                            { onToggleExpand(app.packageName) }
                        } else {
                            null
                        },
                        onCheckedChange = { checked ->
                            config.commit(
                                config.config.copy(
                                    appEnabled = config.config.appEnabled + (app.packageName to checked)
                                )
                            )
                        },
                    )
                },
            )
            if (expanded) {
                add({ SegmentedNoteItem(text = stringResource(R.string.adapted_versions_title)) })
                app.adaptedVersions.forEach { version ->
                    add({ SegmentedNoteItem(text = version) })
                }
                add({ SegmentedNoteItem(text = installedVersionLine(app)) })
            }
        },
    )
}
