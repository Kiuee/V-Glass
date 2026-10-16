package com.moji.liquidglass.ui.screen.apps

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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 应用页 Miuix 版：总开关 + 逐应用开关与适配说明。 */
@Composable
fun AppsPagerMiuix(bottomInnerPadding: Dp, config: ConfigController) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val apps = rememberTargetApps()

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
                                    androidx.compose.material3.Icon(
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

                        apps.forEach { app ->
                            val pkg = app.packageName
                            Card(modifier = Modifier.fillMaxWidth()) {
                                SwitchPreference(
                                    title = app.displayName,
                                    summary = if (!config.config.enabled) {
                                        stringResource(R.string.app_disabled_by_master)
                                    } else {
                                        pkg
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
                                if (app.note.isNotEmpty() && config.config.enabled) {
                                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                        androidx.compose.material3.Text(
                                            app.note,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
