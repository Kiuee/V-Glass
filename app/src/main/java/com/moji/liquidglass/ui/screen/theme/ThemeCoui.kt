package com.moji.liquidglass.ui.screen.theme

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Scaffold
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.component.coui.CouiPreferenceRow
import com.moji.liquidglass.ui.component.coui.CouiSelectRow
import com.moji.liquidglass.ui.component.coui.CouiSwitchRow
import com.moji.liquidglass.ui.component.coui.CouiTopBarAction
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.ColorMode

/** 主题设置页（对齐 demo `SettingsDemo`）：外观 / 主题（Material 主题、轮廓光）/ 顶栏 / 重置外观。 */
@Composable
internal fun ThemeCoui(onBack: () -> Unit) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.maxValue > 0 },
    )
    val backdrop = rememberCouiWindowBackdrop()
    var showReset by remember { mutableStateOf(false) }
    val colorMode = ManagerPrefs.colorMode
    val baseMode = colorMode
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(behavior.nestedScrollConnection),
        containerColor = colors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    androidx.compose.material3.Text(text = stringResource(R.string.theme_page), maxLines = 1)
                },
                navigationIcon = {
                    CouiTopBarAction(
                        description = stringResource(R.string.action_cancel),
                        onClick = onBack,
                    ) {
                        Icon(
                            imageVector = CouiIcons.Filled.Back,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    CouiTopBarAction(
                        description = stringResource(R.string.reset_appearance),
                        onClick = { showReset = true },
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = behavior,
                dark = dark,
            )
        },
    ) { padding ->
        val contentPadding = remember(behavior, padding, density) {
            behavior.state.contentPadding(padding, density)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .consumeWindowInsets(padding)
                .padding(bottom = CouiPageDefaults.groupSpacing),
        ) {
            Group(title = stringResource(R.string.theme_section_appearance), colors = colors) {
                CouiSelectRow(
                    title = stringResource(R.string.color_mode_system),
                    selected = baseMode == ColorMode.SYSTEM,
                    onSelect = { ManagerPrefs.setColorMode(ColorMode.SYSTEM) },
                )
                Divider()
                CouiSelectRow(
                    title = stringResource(R.string.color_mode_light),
                    selected = baseMode == ColorMode.LIGHT,
                    onSelect = { ManagerPrefs.setColorMode(ColorMode.LIGHT) },
                )
                Divider()
                CouiSelectRow(
                    title = stringResource(R.string.color_mode_dark),
                    selected = baseMode == ColorMode.DARK,
                    onSelect = { ManagerPrefs.setColorMode(ColorMode.DARK) },
                )
            }

            Group(title = stringResource(R.string.theme_section_theme), colors = colors) {
                CouiSwitchRow(
                    title = stringResource(R.string.material_theme),
                    summary = stringResource(R.string.material_theme_summary),
                    checked = ManagerPrefs.materialTheme,
                    onCheckedChange = { ManagerPrefs.setMaterialTheme(it) },
                )
                Divider()
                CouiSwitchRow(
                    title = stringResource(R.string.contour),
                    summary = stringResource(R.string.contour_summary),
                    checked = ManagerPrefs.contour,
                    onCheckedChange = { ManagerPrefs.setContour(it) },
                )
            }

            Group(title = stringResource(R.string.theme_section_top_bar), colors = colors) {
                CouiSwitchRow(
                    title = stringResource(R.string.compose_progressive_blur),
                    summary = stringResource(R.string.compose_progressive_blur_summary),
                    checked = ManagerPrefs.composeProgressiveBlur,
                    onCheckedChange = { ManagerPrefs.setComposeProgressiveBlur(it) },
                    enabled = Build.VERSION.SDK_INT >= 33,
                )
            }
        }
    }

    val material = if (CouiDialogDefaults.isMaterialSupported()) {
        CouiDialogDefaults.material(dark)
    } else {
        null
    }
    Dialog(
        visible = showReset,
        onDismissRequest = { showReset = false },
        dark = dark,
        title = stringResource(R.string.reset_appearance),
        message = stringResource(R.string.reset_appearance_message),
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.action_confirm),
            onClick = {
                ManagerPrefs.resetAppearance()
                showReset = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(colorScheme.primary),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { showReset = false },
        ),
        backdrop = backdrop,
        material = material,
    )
}
