@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem
import com.moji.liquidglass.ui.component.material.SegmentedSwitchItem

/** 应用页 Material 版：总开关 + 逐应用开关与适配说明。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsPagerMaterial(bottomInnerPadding: Dp, config: ConfigController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val apps = rememberTargetApps()

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

            apps.forEach { app ->
                val pkg = app.packageName
                SegmentedColumn(
                    title = app.displayName,
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                title = pkg,
                                summary = if (!config.config.enabled) {
                                    stringResource(R.string.app_disabled_by_master)
                                } else {
                                    app.note.takeIf { it.isNotEmpty() }
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
                    ),
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
