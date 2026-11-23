package com.moji.liquidglass.ui.component.coui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 顶栏动作：34+ 用 demo 同款 `TopAppBarButton`；33 用 demo 的 48dp Box 回退。
 * 必须位于 TopAppBar / LargeTopAppBar / CollapsingTopAppBar 的效果作用域内。
 */
@Composable
internal fun CouiTopBarAction(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    if (Build.VERSION.SDK_INT >= 34) {
        TopAppBarButton(
            onClick = onClick,
            enabled = enabled,
            style = CouiTopAppBarButtonDefaults.style(dark),
        ) {
            content()
        }
    } else {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CouiShape(24.dp, 2f))
                .couiButtonFeedback(dark, source, enabled)
                .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            content()
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
