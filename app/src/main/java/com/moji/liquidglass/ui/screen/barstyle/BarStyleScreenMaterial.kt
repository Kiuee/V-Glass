@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedSliderItem
import com.moji.liquidglass.ui.component.material.SegmentedSwitchItem
import kotlin.math.roundToInt

/** 玻璃栏样式页 Material 版：四个滑条 + 文字标签开关，写入 LSPosed 远程配置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarStyleScreenMaterial(onBack: () -> Unit) {
    val config = ConfigController.remember()
    val c = config.config
    val valueFmt = stringResource(R.string.bar_value_dp_fmt)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.bar_style_page)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
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
            Text(
                stringResource(R.string.bar_style_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
            )

            SegmentedColumn(
                content = listOf(
                    {
                        SegmentedSliderItem(
                            title = stringResource(R.string.bar_margin),
                            value = c.barMarginDp,
                            valueRange = 0f..40f,
                            valueText = String.format(valueFmt, c.barMarginDp.roundToInt()),
                            enabled = ServiceBridge.bound,
                            onCommit = { config.commit(config.config.copy(barMarginDp = it)) },
                        )
                    },
                    {
                        SegmentedSliderItem(
                            title = stringResource(R.string.bar_bottom_gap),
                            value = c.barBottomGapDp,
                            valueRange = 0f..24f,
                            valueText = String.format(valueFmt, c.barBottomGapDp.roundToInt()),
                            enabled = ServiceBridge.bound,
                            onCommit = { config.commit(config.config.copy(barBottomGapDp = it)) },
                        )
                    },
                    {
                        SegmentedSliderItem(
                            title = stringResource(R.string.bar_height),
                            value = c.barHeightDp,
                            valueRange = 48f..88f,
                            valueText = String.format(valueFmt, c.barHeightDp.roundToInt()),
                            enabled = ServiceBridge.bound,
                            onCommit = { config.commit(config.config.copy(barHeightDp = it)) },
                        )
                    },
                    {
                        SegmentedSliderItem(
                            title = stringResource(R.string.bar_blur),
                            value = c.barBlurDp,
                            valueRange = 0f..24f,
                            valueText = String.format(valueFmt, c.barBlurDp.roundToInt()),
                            enabled = ServiceBridge.bound,
                            onCommit = { config.commit(config.config.copy(barBlurDp = it)) },
                        )
                    },
                ),
            )
        }
    }
}
