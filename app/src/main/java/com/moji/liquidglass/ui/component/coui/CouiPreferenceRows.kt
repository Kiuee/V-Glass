package com.moji.liquidglass.ui.component.coui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.slider.CouiSliderDefaults
import com.itos.coui.slider.Slider
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** 入口/信息行（demo `DemoPreference`）：标题/摘要 + 右侧值（162dp 右对齐）+ 跳转箭头。 */
@Composable
internal fun CouiPreferenceRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    Item(
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.disabled,
            )
        },
        summary = summary?.takeIf { it.isNotEmpty() }?.let { text ->
            @Composable {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.summary else colors.disabled,
                )
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        kind = CouiTextKind.ItemValue,
                        color = if (enabled) colors.summary else colors.disabled,
                        modifier = Modifier
                            .widthIn(max = 162.dp)
                            .padding(start = 16.dp),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showChevron) {
                    Icon(
                        imageVector = CouiIcons.Filled.Next,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(width = 12.dp, height = 24.dp),
                        tint = Color(if (dark) 0x4DFFFFFF else 0x42000000),
                    )
                }
            }
        },
    )
}

/** 开关行（demo `DemoSwitchPreference`）：整行 toggle + 共享交互源的 Switch。 */
@Composable
internal fun CouiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    Item(
        modifier = Modifier
            .couiButtonFeedback(dark, source, enabled, list = true)
            .toggleable(checked, source, null, enabled, Role.Switch) { target ->
                view.performCouiHapticFeedback(CouiHapticFeedback.Switch, true)
                onCheckedChange(target)
            },
        icon = icon,
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.disabled,
            )
        },
        summary = summary?.takeIf { it.isNotEmpty() }?.let { text ->
            @Composable {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.summary else colors.disabled,
                )
            }
        },
        trailing = {
            Switch(
                modifier = Modifier.padding(start = 16.dp),
                checked = checked,
                onCheckedChange = null,
                dark = dark,
                colors = CouiSwitchDefaults.colors(colors.accent, dark),
                enabled = enabled,
                interactionSource = source,
                onDragCheckedChange = onCheckedChange,
            )
        },
    )
}

/** 单选行（demo `DemoSelectPreference`）：整行 selectable + 24dp RadioButton。 */
@Composable
internal fun CouiSelectRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val source = remember { MutableInteractionSource() }
    Item(
        modifier = Modifier
            .couiButtonFeedback(dark, source, enabled, list = true)
            .selectable(selected, source, null, enabled, Role.RadioButton, onSelect),
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.disabled,
            )
        },
        summary = summary?.takeIf { it.isNotEmpty() }?.let { text ->
            @Composable {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.summary else colors.disabled,
                )
            }
        },
        trailing = {
            RadioButton(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(24.dp),
                selected = selected,
                enabled = enabled,
            )
        },
    )
}

/** 滑条行（demo `DemoSliderPreference`）：标题/值行 + 16dp 滑条行；值可点击。 */
@Composable
internal fun CouiSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
    onValueTextClick: (() -> Unit)? = null,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    Column(Modifier.fillMaxWidth()) {
        CouiPreferenceRow(
            title = title,
            value = valueText,
            enabled = enabled,
            onClick = onValueTextClick,
            showChevron = false,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = title },
                value = value,
                onValueChange = onValueChange,
                dark = dark,
                enabled = enabled,
                colors = CouiSliderDefaults.colors(colors.accent, dark, enabled),
                shape = CouiCapsuleShape,
                valueRange = valueRange,
                onValueChangeFinished = onValueChangeFinished,
            )
        }
    }
}
