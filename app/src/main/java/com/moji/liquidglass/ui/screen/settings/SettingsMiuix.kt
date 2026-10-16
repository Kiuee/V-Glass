package com.moji.liquidglass.ui.screen.settings

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.theme.UiMode
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 设置页 Miuix 版：界面（风格/主题入口）+ 玻璃栏样式入口 + 其他。 */
@Composable
fun SettingPagerMiuix(
    bottomInnerPadding: Dp,
    config: ConfigController,
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
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
                            OverlayDropdownPreference(
                                title = stringResource(R.string.ui_mode),
                                summary = stringResource(R.string.ui_mode_summary),
                                items = uiModeLabels,
                                startAction = {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.Dashboard,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                selectedIndex = if (ManagerPrefs.uiMode == UiMode.Material) 1 else 0,
                                onSelectedIndexChange = { index ->
                                    ManagerPrefs.setUiMode(if (index == 1) UiMode.Material else UiMode.Miuix)
                                },
                            )
                            ArrowPreference(
                                title = stringResource(R.string.theme_settings),
                                summary = stringResource(R.string.theme_settings_summary),
                                startAction = {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.Palette,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = onOpenTheme,
                            )
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.bar_style),
                                summary = stringResource(R.string.bar_style_summary),
                                startAction = {
                                    androidx.compose.material3.Icon(
                                        Icons.Rounded.Style,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = onOpenBarStyle,
                            )
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            MiuixNoteRow(text = stringResource(R.string.about_version_fmt, versionName))
                            MiuixNoteRow(text = stringResource(R.string.about_requirement))
                            MiuixNoteRow(text = stringResource(R.string.about_diagnostics))
                        }

                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

/** 单行说明行：小字副标题样式。 */
@Composable
private fun MiuixNoteRow(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
