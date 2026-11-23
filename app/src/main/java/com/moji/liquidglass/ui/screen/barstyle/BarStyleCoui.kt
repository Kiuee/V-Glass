package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.coui.CouiSliderRow
import com.moji.liquidglass.ui.component.coui.CouiSwitchRow
import com.moji.liquidglass.ui.component.coui.CouiTopBarAction
import com.moji.liquidglass.ui.component.coui.CouiValueDialog
import kotlin.math.roundToInt

/** 玻璃栏样式页（COUI）：外观 / 模糊 / 边缘与内晕三组滑杆与开关 + 数值键盘编辑 + 还原默认。 */
@Composable
internal fun BarStyleCoui(onBack: () -> Unit) {
    val config = ConfigController.remember()
    val c = config.config
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = MaterialTheme.colorScheme
    val valueFmt = stringResource(R.string.bar_value_dp_fmt)
    val dp1Fmt = stringResource(R.string.bar_value_dp1_fmt)
    val pctFmt = stringResource(R.string.bar_value_pct_fmt)
    val timesFmt = stringResource(R.string.bar_value_times_fmt)
    val backdrop = rememberCouiWindowBackdrop()
    val dialogMaterial = if (CouiDialogDefaults.isMaterialSupported()) {
        CouiDialogDefaults.material(dark)
    } else {
        null
    }

    var editing by remember { mutableStateOf<BarValueEditor?>(null) }
    editing?.let { editor ->
        CouiValueDialog(editor = editor, onDismiss = { editing = null })
    }

    var showResetDialog by remember { mutableStateOf(false) }
    if (showResetDialog) {
        Dialog(
            onDismissRequest = { showResetDialog = false },
            confirmAction = CouiDialogAction(
                text = stringResource(R.string.action_confirm),
                onClick = {
                    config.commit(config.config.withBarStyleDefaults())
                    showResetDialog = false
                },
                enabled = ServiceBridge.bound,
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(colorScheme.primary),
            ),
            title = stringResource(R.string.bar_reset_defaults_title),
            message = stringResource(R.string.bar_reset_defaults_message),
            dismissAction = CouiDialogAction(
                text = stringResource(R.string.action_cancel),
                onClick = { showResetDialog = false },
            ),
            dark = dark,
            backdrop = backdrop,
            material = dialogMaterial,
            visible = true,
        )
    }

    val lazyState = rememberLazyListState()
    val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { lazyState.canScrollForward },
    )
    val density = LocalDensity.current
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(behavior.nestedScrollConnection),
        containerColor = colors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.bar_style_page),
                        maxLines = 1,
                    )
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
                        description = stringResource(R.string.bar_reset_defaults),
                        onClick = { showResetDialog = true },
                        enabled = ServiceBridge.bound,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
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
        LazyColumn(
            state = lazyState,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = contentPadding,
        ) {
            item {
                Column {
                    Group(title = stringResource(R.string.bar_section_appearance), colors = colors) {
                        SliderRow(
                            title = stringResource(R.string.bar_margin),
                            value = c.barMarginDp,
                            range = 0f..40f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            toEditable = { it.roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(0f, 40f) },
                            onCommit = { config.commit(config.config.copy(barMarginDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_bottom_gap),
                            value = c.barBottomGapDp,
                            range = -16f..24f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            toEditable = { it.roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(-16f, 24f) },
                            onCommit = { config.commit(config.config.copy(barBottomGapDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_height),
                            value = c.barHeightDp,
                            range = 48f..88f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            toEditable = { it.roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(48f, 88f) },
                            onCommit = { config.commit(config.config.copy(barHeightDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        CouiSwitchRow(
                            title = stringResource(R.string.bar_show_labels),
                            checked = c.barShowLabels,
                            onCheckedChange = { checked ->
                                config.commit(config.config.copy(barShowLabels = checked))
                            },
                            enabled = ServiceBridge.bound,
                        )
                    }

                    Group(title = stringResource(R.string.bar_section_blur), colors = colors) {
                        BlurSliders(
                            c = c,
                            config = config,
                            enabled = ServiceBridge.bound,
                            valueFmt = valueFmt,
                            pctFmt = pctFmt,
                            timesFmt = timesFmt,
                            onEdit = { editing = it },
                        )
                    }

                    Group(title = stringResource(R.string.bar_section_edge_glow), colors = colors) {
                        CouiSwitchRow(
                            title = stringResource(R.string.bar_edge_glow),
                            checked = c.barEdgeGlow,
                            onCheckedChange = { checked ->
                                config.commit(config.config.copy(barEdgeGlow = checked))
                            },
                            enabled = ServiceBridge.bound,
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_edge_width),
                            value = c.barEdgeWidthDp,
                            range = 0f..4f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(dp1Fmt, it) },
                            toEditable = { it.toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(0f, 4f) },
                            onCommit = { config.commit(config.config.copy(barEdgeWidthDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_edge_blur),
                            value = c.barEdgeBlurDp,
                            range = 0f..2f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(dp1Fmt, it) },
                            toEditable = { it.toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(0f, 2f) },
                            onCommit = { config.commit(config.config.copy(barEdgeBlurDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_edge_alpha),
                            value = c.barEdgeAlpha,
                            range = 0f..1f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                            toEditable = { (it * 100).roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) },
                            onCommit = { config.commit(config.config.copy(barEdgeAlpha = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_inner_glow_radius),
                            value = c.barInnerGlowRadiusDp,
                            range = 0f..24f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(valueFmt, it.roundToInt()) },
                            toEditable = { it.roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.coerceIn(0f, 24f) },
                            onCommit = { config.commit(config.config.copy(barInnerGlowRadiusDp = it)) },
                            onEdit = { editing = it },
                        )
                        Divider()
                        SliderRow(
                            title = stringResource(R.string.bar_inner_glow_alpha),
                            value = c.barInnerGlowAlpha,
                            range = 0f..1f,
                            enabled = ServiceBridge.bound,
                            valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
                            toEditable = { (it * 100).roundToInt().toString() },
                            parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) },
                            onCommit = { config.commit(config.config.copy(barInnerGlowAlpha = it)) },
                            onEdit = { editing = it },
                        )
                    }

                    Group(colors = colors) {
                        Item {
                            Text(
                                text = stringResource(R.string.bar_style_note),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueText: (Float) -> String,
    toEditable: (Float) -> String,
    parse: (String) -> Float?,
    onCommit: (Float) -> Unit,
    onEdit: (BarValueEditor) -> Unit,
) {
    CouiSliderRow(
        title = title,
        value = value,
        onValueChange = onCommit,
        valueText = valueText(value),
        valueRange = range,
        enabled = enabled,
        onValueChangeFinished = { onCommit(value) },
        onValueTextClick = {
            onEdit(
                BarValueEditor(
                    title = title,
                    range = range,
                    format = valueText,
                    toEditable = toEditable,
                    parse = parse,
                    current = value,
                    commit = onCommit,
                )
            )
        },
    )
}

@Composable
private fun BlurSliders(
    c: com.moji.liquidglass.config.ModuleConfig,
    config: ConfigController,
    enabled: Boolean,
    valueFmt: String,
    pctFmt: String,
    timesFmt: String,
    onEdit: (BarValueEditor) -> Unit,
) {
    SliderRow(
        title = stringResource(R.string.bar_blur),
        value = c.barBlurDp,
        range = 0f..24f,
        enabled = enabled,
        valueText = { String.format(valueFmt, it.roundToInt()) },
        toEditable = { it.roundToInt().toString() },
        parse = { it.toFloatOrNull()?.coerceIn(0f, 24f) },
        onCommit = { config.commit(config.config.copy(barBlurDp = it)) },
        onEdit = onEdit,
    )
    Divider()
    SliderRow(
        title = stringResource(R.string.bar_surface_alpha),
        value = c.barSurfaceAlpha,
        range = 0f..1f,
        enabled = enabled,
        valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
        toEditable = { (it * 100).roundToInt().toString() },
        parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) },
        onCommit = { config.commit(config.config.copy(barSurfaceAlpha = it)) },
        onEdit = onEdit,
    )
    Divider()
    SliderRow(
        title = stringResource(R.string.bar_saturation),
        value = c.barSaturation,
        range = 1f..2f,
        enabled = enabled,
        valueText = { String.format(timesFmt, it) },
        toEditable = { it.toString() },
        parse = { it.toFloatOrNull()?.coerceIn(1f, 2f) },
        onCommit = { config.commit(config.config.copy(barSaturation = it)) },
        onEdit = onEdit,
    )
    Divider()
    SliderRow(
        title = stringResource(R.string.bar_refraction_amount),
        value = c.barRefractionAmountScale,
        range = 0f..2f,
        enabled = enabled,
        valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
        toEditable = { (it * 100).roundToInt().toString() },
        parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 2f) },
        onCommit = { config.commit(config.config.copy(barRefractionAmountScale = it)) },
        onEdit = onEdit,
    )
    Divider()
    SliderRow(
        title = stringResource(R.string.bar_refraction_height),
        value = c.barRefractionHeightScale,
        range = 0f..2f,
        enabled = enabled,
        valueText = { String.format(pctFmt, (it * 100).roundToInt()) },
        toEditable = { (it * 100).roundToInt().toString() },
        parse = { it.toFloatOrNull()?.div(100f)?.coerceIn(0f, 2f) },
        onCommit = { config.commit(config.config.copy(barRefractionHeightScale = it)) },
        onEdit = onEdit,
    )
}
