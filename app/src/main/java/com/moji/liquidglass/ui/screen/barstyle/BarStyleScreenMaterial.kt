@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** 玻璃栏样式页 Material 版：「玻璃底栏外观」与「模糊效果」两卡片分组，写入 LSPosed 远程配置。
 *  滑杆数值拖动即时刷新，点击数值可键盘输入（显示单位，越界自动收敛）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarStyleScreenMaterial(onBack: () -> Unit) {
    val config = ConfigController.remember()
    val c = config.config
    val valueFmt = stringResource(R.string.bar_value_dp_fmt)
    val pctFmt = stringResource(R.string.bar_value_pct_fmt)
    val timesFmt = stringResource(R.string.bar_value_times_fmt)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editing by remember { mutableStateOf<BarValueEditor?>(null) }
    editing?.let { BarValueEditDialogMaterial(editor = it, onDismiss = { editing = null }) }

    // 右上角"还原默认"：二次确认后仅重置样式页参数（保留开关/按应用开关等非样式字段）
    var showResetDialog by remember { mutableStateOf(false) }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.bar_reset_defaults_title)) },
            text = { Text(stringResource(R.string.bar_reset_defaults_message)) },
            confirmButton = {
                TextButton(
                    enabled = ServiceBridge.bound,
                    onClick = {
                        config.commit(config.config.withBarStyleDefaults())
                        showResetDialog = false
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

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
                actions = {
                    IconButton(
                        enabled = ServiceBridge.bound,
                        onClick = { showResetDialog = true },
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.bar_reset_defaults),
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.bar_style_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )

            SegmentedColumn(
                title = stringResource(R.string.bar_section_appearance),
                content = listOf(
                    {
                        val title = stringResource(R.string.bar_margin)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barMarginDp,
                            valueRange = 0f..40f,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 0f..40f,
                                    format = { String.format(valueFmt, it.roundToInt()) },
                                    toEditable = { it.roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.coerceIn(0f, 40f) },
                                    current = c.barMarginDp,
                                    commit = { config.commit(config.config.copy(barMarginDp = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barMarginDp = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_bottom_gap)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barBottomGapDp,
                            valueRange = -16f..24f,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = -16f..24f,
                                    format = { String.format(valueFmt, it.roundToInt()) },
                                    toEditable = { it.roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.coerceIn(-16f, 24f) },
                                    current = c.barBottomGapDp,
                                    commit = { config.commit(config.config.copy(barBottomGapDp = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barBottomGapDp = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_height)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barHeightDp,
                            valueRange = 48f..88f,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 48f..88f,
                                    format = { String.format(valueFmt, it.roundToInt()) },
                                    toEditable = { it.roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.coerceIn(48f, 88f) },
                                    current = c.barHeightDp,
                                    commit = { config.commit(config.config.copy(barHeightDp = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barHeightDp = it)) },
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.bar_show_labels),
                            checked = c.barShowLabels,
                            enabled = ServiceBridge.bound,
                            onCheckedChange = { config.commit(config.config.copy(barShowLabels = it)) },
                        )
                    },
                ),
            )

            SegmentedColumn(
                title = stringResource(R.string.bar_section_blur),
                content = listOf(
                    {
                        val title = stringResource(R.string.bar_blur)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barBlurDp,
                            valueRange = 0f..24f,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 0f..24f,
                                    format = { String.format(valueFmt, it.roundToInt()) },
                                    toEditable = { it.roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.coerceIn(0f, 24f) },
                                    current = c.barBlurDp,
                                    commit = { config.commit(config.config.copy(barBlurDp = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barBlurDp = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_surface_alpha)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barSurfaceAlpha,
                            valueRange = 0f..1f,
                            valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 0f..1f,
                                    format = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    toEditable = { (it * 100).roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) },
                                    current = c.barSurfaceAlpha,
                                    commit = { config.commit(config.config.copy(barSurfaceAlpha = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barSurfaceAlpha = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_saturation)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barSaturation,
                            valueRange = 1f..2f,
                            valueText = { String.format(timesFmt, it) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 1f..2f,
                                    format = { String.format(timesFmt, it) },
                                    toEditable = { it.toString() },
                                    parse = { it.toFloatOrNull()?.coerceIn(1f, 2f) },
                                    current = c.barSaturation,
                                    commit = { config.commit(config.config.copy(barSaturation = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barSaturation = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_refraction_amount)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barRefractionAmountScale,
                            valueRange = 0f..2f,
                            valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 0f..2f,
                                    format = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    toEditable = { (it * 100).roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 2f) },
                                    current = c.barRefractionAmountScale,
                                    commit = { config.commit(config.config.copy(barRefractionAmountScale = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barRefractionAmountScale = it)) },
                        )
                    },
                    {
                        val title = stringResource(R.string.bar_refraction_height)
                        SegmentedSliderItem(
                            title = title,
                            value = c.barRefractionHeightScale,
                            valueRange = 0f..2f,
                            valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                            enabled = ServiceBridge.bound,
                            onValueTextClick = {
                                editing = BarValueEditor(
                                    title = title,
                                    range = 0f..2f,
                                    format = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    toEditable = { (it * 100).roundToInt().toString() },
                                    parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 2f) },
                                    current = c.barRefractionHeightScale,
                                    commit = { config.commit(config.config.copy(barRefractionHeightScale = it)) },
                                )
                            },
                            onCommit = { config.commit(config.config.copy(barRefractionHeightScale = it)) },
                        )
                    },
                ),
            )

            // 底部收尾间距（滚动到底不贴边）
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}
