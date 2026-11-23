package com.moji.liquidglass.ui.component.coui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.slider.Slider
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** COUI 滑杆行：标题 + 当前值（点击可键盘编辑）+ 滑杆；值变化即提交。 */
@Composable
internal fun CouiSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onValueTextClick: (() -> Unit)? = null,
) {
    val colorScheme = CouiTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText(value),
                kind = CouiTextKind.ItemValue,
                color = if (enabled) colorScheme.accent else colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = if (onValueTextClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onValueTextClick)
                } else {
                    Modifier
                },
            )
        }
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}
