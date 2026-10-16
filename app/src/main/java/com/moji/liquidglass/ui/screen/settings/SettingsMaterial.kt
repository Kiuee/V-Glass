@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.settings

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.component.material.SegmentedArrowItem
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedDropdownItem
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.theme.UiMode

/** 设置页 Material 版：界面（风格/主题入口）+ 玻璃栏样式入口 + 其他。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPagerMaterial(
    bottomInnerPadding: Dp,
    config: ConfigController,
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                title = stringResource(R.string.interface_group),
                content = listOf(
                    {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Widgets,
                            title = stringResource(R.string.ui_mode),
                            summary = stringResource(R.string.ui_mode_summary),
                            items = uiModeLabels,
                            selectedIndex = if (ManagerPrefs.uiMode == UiMode.Material) 1 else 0,
                            onItemSelected = { index ->
                                ManagerPrefs.setUiMode(if (index == 1) UiMode.Material else UiMode.Miuix)
                            },
                        )
                    },
                    {
                        SegmentedArrowItem(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.theme_settings),
                            summary = stringResource(R.string.theme_settings_summary),
                            onClick = onOpenTheme,
                        )
                    },
                ),
            )

            SegmentedColumn(
                title = stringResource(R.string.bar_style),
                content = listOf(
                    {
                        SegmentedArrowItem(
                            icon = Icons.Filled.Style,
                            title = stringResource(R.string.bar_style),
                            summary = stringResource(R.string.bar_style_summary),
                            onClick = onOpenBarStyle,
                        )
                    },
                ),
            )

            SegmentedColumn(
                title = stringResource(R.string.other_group),
                content = listOf(
                    {
                        SegmentedNoteItem(
                            text = stringResource(R.string.about_version_fmt, versionName),
                            icon = Icons.Filled.Info,
                        )
                    },
                    {
                        SegmentedNoteItem(
                            text = stringResource(R.string.about_requirement),
                            icon = Icons.Filled.Info,
                        )
                    },
                    {
                        SegmentedNoteItem(
                            text = stringResource(R.string.about_diagnostics),
                            icon = Icons.Filled.Info,
                        )
                    },
                ),
            )

            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}
