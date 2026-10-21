@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.component.material.SegmentedCheckboxItem
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem
import com.moji.liquidglass.ui.component.material.SegmentedSwitchItem
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 应用页 Material 版：总开关 + 搜索框 + 未适配红条（可关）+ 逐应用勾选行与适配 chip。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsPagerMaterial(bottomInnerPadding: Dp, config: ConfigController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val apps = rememberTargetApps()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    val unadaptedCount = apps.count { it.adaptState == AdaptState.NotAdapted }
    val showUnadaptedBar = unadaptedCount > 0 &&
        ManagerPrefs.unadaptedDismissed != unadaptedSignature(apps)
    // 展开查看已适配版本的应用包名列表（未适配 chip 点击展开/收起）
    var expandedPkgs by rememberSaveable { mutableStateOf(listOf<String>()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.apps)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = {
                    // 图标槽默认 12dp，补齐到列表行的 16dp 内容边距
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
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

            if (filtered.isEmpty()) {
                SegmentedColumn(
                    content = listOf(
                        { SegmentedNoteItem(text = stringResource(R.string.apps_search_empty)) },
                    ),
                )
            }

            filtered.forEach { app ->
                val pkg = app.packageName
                val expanded = pkg in expandedPkgs
                val expandable = app.adaptedVersions.isNotEmpty()
                val installedLine = if (app.installedVersion != null) {
                    stringResource(R.string.app_installed_version_fmt, app.installedVersion)
                } else {
                    stringResource(R.string.app_not_installed)
                }
                SegmentedColumn(
                    title = app.displayName,
                    content = buildList {
                        add(
                            {
                                SegmentedCheckboxItem(
                                    title = pkg,
                                    summary = if (!config.config.enabled) {
                                        stringResource(R.string.app_disabled_by_master)
                                    } else {
                                        app.summaryLine
                                    },
                                    trailingContent = {
                                        if (expandable) {
                                            Box(
                                                modifier = Modifier.clickable(
                                                    onClickLabel = stringResource(
                                                        if (expanded) {
                                                            R.string.apps_hide_adapted_versions
                                                        } else {
                                                            R.string.apps_view_adapted_versions
                                                        },
                                                    ),
                                                ) {
                                                    expandedPkgs = if (expanded) {
                                                        expandedPkgs - pkg
                                                    } else {
                                                        expandedPkgs + pkg
                                                    }
                                                },
                                            ) {
                                                AdaptChip(app.adaptState)
                                            }
                                        } else {
                                            AdaptChip(app.adaptState)
                                        }
                                    },
                                    checked = config.config.appEnabledFor(pkg),
                                    enabled = ServiceBridge.bound && config.config.enabled,
                                    onCheckedChange = { checked ->
                                        config.commit(
                                            config.config.copy(
                                                appEnabled = config.config.appEnabled + (pkg to checked)
                                            )
                                        )
                                    },
                                )
                            },
                        )
                        if (expanded) {
                            add({
                                SegmentedNoteItem(text = stringResource(R.string.adapted_versions_title))
                            })
                            app.adaptedVersions.forEach { version ->
                                add({ SegmentedNoteItem(text = version) })
                            }
                            add({ SegmentedNoteItem(text = installedLine) })
                        }
                    },
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
}
