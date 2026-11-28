package com.moji.liquidglass.ui.component.coui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.TopAppBarButtonGroup

/**
 * 顶栏动作：34+ 用 demo 同款 `TopAppBarButton`；33 用 demo 的 48dp 回退。
 * 必须位于 TopAppBar / LargeTopAppBar / CollapsingTopAppBar 的效果作用域内。
 *
 * [label] 非空时显示为"图标 + 文字"按钮（34+ 走 `TopAppBarButton(text = true)`，
 * 33 走最小 60×40dp 胶囊），否则保持原图标按钮。
 */
@Composable
internal fun CouiTopBarAction(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String? = null,
    labelColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    if (Build.VERSION.SDK_INT >= 34) {
        TopAppBarButton(
            onClick = onClick,
            enabled = enabled,
            text = label != null,
            style = CouiTopAppBarButtonDefaults.style(dark),
        ) {
            if (label != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    content()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        maxLines = 1,
                    )
                }
            } else {
                content()
            }
        }
    } else {
        val source = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .heightIn(min = if (label != null) 40.dp else 48.dp)
                .widthIn(min = if (label != null) 60.dp else 48.dp)
                .clip(CouiShape(if (label != null) 20.dp else 24.dp, 2f))
                .couiButtonFeedback(dark, source, enabled)
                .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = if (label != null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
            if (label != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CouiTopBarActionGroup(content: @Composable RowScope.() -> Unit) {
    if (Build.VERSION.SDK_INT >= 34) {
        TopAppBarButtonGroup(content = content)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
