package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text as M3Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/** 玻璃栏样式页 Miuix 版：「玻璃底栏外观」与「模糊效果」两卡片分组，写入 LSPosed 远程配置。
 *  滑杆数值拖动即时刷新，点击数值可键盘输入（显示单位，越界自动收敛）。 */
@Composable
fun BarStyleScreenMiuix(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val config = ConfigController.remember()
    val c = config.config
    val valueFmt = stringResource(R.string.bar_value_dp_fmt)
    val pctFmt = stringResource(R.string.bar_value_pct_fmt)
    val timesFmt = stringResource(R.string.bar_value_times_fmt)

    var editing by remember { mutableStateOf<BarValueEditor?>(null) }
    editing?.let { BarValueEditDialogMiuix(editor = it, onDismiss = { editing = null }) }

    // 右上角"还原默认"：二次确认后仅重置样式页参数（保留开关/按应用开关等非样式字段）
    var showResetDialog by remember { mutableStateOf(false) }
    if (showResetDialog) {
        val dismissState = LocalDismissState.current
        val dismiss: () -> Unit = { dismissState?.invoke() ?: run { showResetDialog = false } }
        WindowDialog(
            show = true,
            title = stringResource(R.string.bar_reset_defaults_title),
            summary = stringResource(R.string.bar_reset_defaults_message),
            onDismissRequest = { showResetDialog = false },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = dismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    enabled = ServiceBridge.bound,
                    onClick = {
                        config.commit(config.config.withBarStyleDefaults())
                        dismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.bar_style_page),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            androidx.compose.material3.Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = ServiceBridge.bound,
                            onClick = { showResetDialog = true },
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.bar_reset_defaults),
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
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
                        MiuixSectionTitle(stringResource(R.string.bar_section_appearance))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                val marginTitle = stringResource(R.string.bar_margin)
                                MiuixSliderRow(
                                    title = marginTitle,
                                    value = c.barMarginDp,
                                    range = 0f..40f,
                                    valueText = { String.format(valueFmt, it.roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = marginTitle,
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
                                val gapTitle = stringResource(R.string.bar_bottom_gap)
                                MiuixSliderRow(
                                    title = gapTitle,
                                    value = c.barBottomGapDp,
                                    range = -16f..24f,
                                    valueText = { String.format(valueFmt, it.roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = gapTitle,
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
                                val heightTitle = stringResource(R.string.bar_height)
                                MiuixSliderRow(
                                    title = heightTitle,
                                    value = c.barHeightDp,
                                    range = 48f..88f,
                                    valueText = { String.format(valueFmt, it.roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = heightTitle,
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
                                SwitchPreference(
                                    title = stringResource(R.string.bar_show_labels),
                                    checked = c.barShowLabels,
                                    enabled = ServiceBridge.bound,
                                    onCheckedChange = { config.commit(config.config.copy(barShowLabels = it)) },
                                )
                            }
                        }

                        MiuixSectionTitle(stringResource(R.string.bar_section_blur))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                val blurTitle = stringResource(R.string.bar_blur)
                                MiuixSliderRow(
                                    title = blurTitle,
                                    value = c.barBlurDp,
                                    range = 0f..24f,
                                    valueText = { String.format(valueFmt, it.roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = blurTitle,
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
                                val surfaceTitle = stringResource(R.string.bar_surface_alpha)
                                MiuixSliderRow(
                                    title = surfaceTitle,
                                    value = c.barSurfaceAlpha,
                                    range = 0f..1f,
                                    valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = surfaceTitle,
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
                                val saturationTitle = stringResource(R.string.bar_saturation)
                                MiuixSliderRow(
                                    title = saturationTitle,
                                    value = c.barSaturation,
                                    range = 1f..2f,
                                    valueText = { String.format(timesFmt, it) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = saturationTitle,
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
                                val refractionAmountTitle = stringResource(R.string.bar_refraction_amount)
                                MiuixSliderRow(
                                    title = refractionAmountTitle,
                                    value = c.barRefractionAmountScale,
                                    range = 0f..2f,
                                    valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = refractionAmountTitle,
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
                                val refractionHeightTitle = stringResource(R.string.bar_refraction_height)
                                MiuixSliderRow(
                                    title = refractionHeightTitle,
                                    value = c.barRefractionHeightScale,
                                    range = 0f..2f,
                                    valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                                    enabled = ServiceBridge.bound,
                                    onValueTextClick = {
                                        editing = BarValueEditor(
                                            title = refractionHeightTitle,
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
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                M3Text(
                                    stringResource(R.string.bar_style_note),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** 卡片分组小节标题。 */
@Composable
private fun MiuixSectionTitle(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        color = colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** miuix 风格滑条行：拖动即时显示、松手提交；数值文本可点击打开键盘输入。 */
@Composable
private fun MiuixSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    enabled: Boolean,
    onValueTextClick: (() -> Unit)? = null,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 15.sp, color = colorScheme.onSurface)
            Text(
                valueText(dragging),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = if (onValueTextClick != null && enabled) {
                    Modifier.clickable { onValueTextClick() }
                } else {
                    Modifier
                },
            )
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            valueRange = range,
            enabled = enabled,
            onValueChangeFinished = { onCommit(dragging) },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
