package com.moji.liquidglass.ui.screen.apps

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.BottomSheet
import com.itos.coui.sheet.CouiSheetButton
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.coui.CouiBanner
import com.moji.liquidglass.ui.component.coui.CouiPreferenceRow
import com.moji.liquidglass.ui.component.coui.CouiSwitchRow
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 应用页（COUI）：总开关 + 未适配警示 + 应用列表 + 半屏模糊版本面板。 */
@Composable
internal fun AppsCoui(
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    config: ConfigController,
    searchQuery: String,
) {
    val apps = rememberTargetApps()
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = MaterialTheme.colorScheme
    val filtered = remember(apps, searchQuery) { filterApps(apps, searchQuery) }
    val searchActive = searchQuery.isNotBlank()
    val unadaptedCount = apps.count { it.adaptState == AdaptState.NotAdapted }
    val showUnadaptedBar = unadaptedCount > 0 &&
        ManagerPrefs.unadaptedDismissed != unadaptedSignature(apps)
    val windowBackdrop = rememberCouiWindowBackdrop()
    val sheetVisible = remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<TargetAppInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .padding(bottom = CouiPageDefaults.groupSpacing),
    ) {
        if (!searchActive) {
            Group(colors = colors) {
                CouiSwitchRow(
                    title = stringResource(R.string.enable_glass_bar),
                    summary = stringResource(R.string.enable_glass_bar_summary),
                    checked = config.config.enabled,
                    onCheckedChange = { checked -> config.commit(config.config.copy(enabled = checked)) },
                    enabled = ServiceBridge.bound,
                    icon = {
                        Icon(
                            imageVector = CouiIcons.Filled.Grid,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = CouiTheme.colorScheme.accent,
                        )
                    },
                )
            }
            if (showUnadaptedBar) {
                CouiBanner(
                    message = stringResource(R.string.apps_unadapted_warning, unadaptedCount),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = CouiPageDefaults.groupSpacing),
                    onDismiss = { ManagerPrefs.setUnadaptedDismissed(unadaptedSignature(apps)) },
                )
            }
        }

        if (filtered.isEmpty()) {
            Group(colors = colors) {
                CouiPreferenceRow(
                    title = stringResource(
                        if (searchActive) R.string.apps_search_empty else R.string.apps_note
                    ),
                    showChevron = false,
                )
            }
        } else {
            Group(colors = colors) {
                filtered.forEachIndexed { index, app ->
                    AppRow(
                        app = app,
                        colors = colors,
                        checked = config.config.appEnabledFor(app.packageName),
                        switchEnabled = ServiceBridge.bound && config.config.enabled,
                        onCheckedChange = { checked ->
                            config.commit(
                                config.config.copy(
                                    appEnabled = config.config.appEnabled + (app.packageName to checked)
                                )
                            )
                        },
                        onClick = {
                            selectedApp = app
                            sheetVisible.value = true
                        },
                    )
                    if (index != filtered.lastIndex) {
                        Divider()
                    }
                }
            }
        }

        if (!searchActive) {
            Group(colors = colors) {
                CouiPreferenceRow(
                    title = stringResource(R.string.apps_note),
                    showChevron = false,
                )
            }
        }
    }

    val sheetApp = selectedApp ?: apps.firstOrNull()
    if (sheetApp != null) {
        BottomSheet(
            visible = sheetVisible,
            sheetHeight = LocalConfiguration.current.screenHeightDp.dp / 2,
            showDragHandle = true,
            dark = dark,
            backdrop = windowBackdrop,
            material = if (CouiDialogDefaults.isMaterialSupported()) {
                CouiDialogDefaults.material(dark)
            } else {
                null
            },
            leadingButton = CouiSheetButton(
                label = stringResource(R.string.action_cancel),
                onClick = { sheetVisible.value = false },
            ),
            titleContent = {
                Text(
                    text = sheetApp.displayName,
                    kind = CouiTextKind.SheetTitle,
                    color = colors.title,
                )
            },
            content = {
                Group(colors = colors) {
                    CouiPreferenceRow(
                        title = stringResource(R.string.adapted_versions_title),
                        showChevron = false,
                    )
                    sheetApp.adaptedVersions.forEach { version ->
                        Divider()
                        CouiPreferenceRow(title = version, showChevron = false)
                    }
                    Divider()
                    CouiPreferenceRow(
                        title = installedVersionLine(sheetApp),
                        showChevron = false,
                    )
                }
            },
        )
    }
}

@Composable
private fun AppRow(
    app: TargetAppInfo,
    colors: CouiPageColors,
    checked: Boolean,
    switchEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Item(
        onClick = onClick,
        iconSize = 40.dp,
        icon = {
            AppIconImage(
                packageName = app.packageName,
                contentDescription = app.displayName,
                modifier = Modifier.size(40.dp),
            )
        },
        summary = {
            Text(
                text = app.packageName,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdaptStatusTag(app.adaptState)
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = switchEnabled,
                )
            }
        },
    ) {
        Text(
            text = app.displayName,
            kind = CouiTextKind.ItemTitle,
            color = colors.title,
        )
    }
}
