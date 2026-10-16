package com.moji.liquidglass.ui.screen.barstyle

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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/** 玻璃栏样式页 Miuix 版：四个滑条 + 文字标签开关，写入 LSPosed 远程配置。 */
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
                            Column(Modifier.padding(vertical = 6.dp)) {
                                MiuixSliderRow(
                                    title = stringResource(R.string.bar_margin),
                                    value = c.barMarginDp,
                                    range = 0f..40f,
                                    valueText = String.format(valueFmt, c.barMarginDp.roundToInt()),
                                    enabled = ServiceBridge.bound,
                                    onCommit = { config.commit(config.config.copy(barMarginDp = it)) },
                                )
                                MiuixSliderRow(
                                    title = stringResource(R.string.bar_bottom_gap),
                                    value = c.barBottomGapDp,
                                    range = 0f..24f,
                                    valueText = String.format(valueFmt, c.barBottomGapDp.roundToInt()),
                                    enabled = ServiceBridge.bound,
                                    onCommit = { config.commit(config.config.copy(barBottomGapDp = it)) },
                                )
                                MiuixSliderRow(
                                    title = stringResource(R.string.bar_height),
                                    value = c.barHeightDp,
                                    range = 48f..88f,
                                    valueText = String.format(valueFmt, c.barHeightDp.roundToInt()),
                                    enabled = ServiceBridge.bound,
                                    onCommit = { config.commit(config.config.copy(barHeightDp = it)) },
                                )
                                MiuixSliderRow(
                                    title = stringResource(R.string.bar_blur),
                                    value = c.barBlurDp,
                                    range = 0f..24f,
                                    valueText = String.format(valueFmt, c.barBlurDp.roundToInt()),
                                    enabled = ServiceBridge.bound,
                                    onCommit = { config.commit(config.config.copy(barBlurDp = it)) },
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

/** miuix 风格滑条行：拖动即时显示、松手提交。 */
@Composable
private fun MiuixSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 15.sp, color = colorScheme.onSurface)
            Text(valueText, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
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
