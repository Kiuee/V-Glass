package com.moji.liquidglass.ui.screen.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 应用页 Miuix 版：总开关 + 搜索框 + 未适配红条（可关）+ 逐应用勾选行与适配 chip。 */
@Composable
fun AppsPagerMiuix(bottomInnerPadding: Dp, config: ConfigController) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
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
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.apps),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
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

                        Card(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.apps_search_hint),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        // miuix TextField 的 leadingIcon 槽位自带 0 边距，须对齐列表行 16dp 内容边距
                                        modifier = Modifier.padding(start = 16.dp, end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
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

                        if (filtered.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.apps_search_empty),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                CheckboxPreference(
                                    title = app.displayName,
                                    summary = if (!config.config.enabled) {
                                        stringResource(R.string.app_disabled_by_master)
                                    } else {
                                        app.summaryLine
                                    },
                                    checked = config.config.appEnabledFor(pkg),
                                    enabled = ServiceBridge.bound && config.config.enabled,
                                    checkboxLocation = CheckboxLocation.End,
                                    endActions = {
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
                                    onCheckedChange = { checked ->
                                        config.commit(
                                            config.config.copy(
                                                appEnabled = config.config.appEnabled + (pkg to checked)
                                            )
                                        )
                                    },
                                )
                                if (app.note.isNotEmpty() && config.config.enabled) {
                                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                        Text(
                                            app.note,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = expanded) {
                                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                        Text(
                                            stringResource(R.string.adapted_versions_title),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onSurface,
                                        )
                                        app.adaptedVersions.forEach { version ->
                                            Text(
                                                version,
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        Text(
                                            installedLine,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
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
