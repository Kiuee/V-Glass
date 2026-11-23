package com.moji.liquidglass.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.coui.CouiIconGreen
import com.moji.liquidglass.ui.component.coui.CouiTag
import com.moji.liquidglass.ui.component.coui.couiIconColor

/**
 * 主页状态大卡片：保留原 Miuix 的「大卡片 + 颜色区分」观感，图标/色彩/字号/圆角全部走 COUI。
 * 已连接 = 强调容器色 + 大图标 + 模式标签；异常 = error 容器色；等待 = 中性容器色。
 */
@Composable
internal fun HomeStatusCard(status: BridgeStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val dark = CouiTheme.isDark
    val accent = CouiTheme.colorScheme.accent
    val (container, content) = when {
        status.connected -> accent.copy(alpha = 0.16f) to colorScheme.onSurface
        status.isError -> colorScheme.errorContainer to colorScheme.onErrorContainer
        else -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CouiPageDefaults.shape)
            .background(container)
            .padding(16.dp),
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomEnd) {
            Icon(
                imageVector = if (status.connected) {
                    CouiIcons.Filled.ShieldCheck
                } else {
                    CouiIcons.Filled.Info
                },
                contentDescription = null,
                modifier = Modifier.size(110.dp),
                tint = if (status.connected) accent.copy(alpha = 0.35f) else content.copy(alpha = 0.22f),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = status.title,
                kind = CouiTextKind.TopBar,
                color = content,
            )
            Text(
                text = status.summary,
                kind = CouiTextKind.ItemSummary,
                color = content.copy(alpha = 0.8f),
            )
            if (status.detail.isNotEmpty()) {
                Text(
                    text = status.detail,
                    kind = CouiTextKind.ItemSummary,
                    color = content.copy(alpha = 0.7f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status.modeLabel.isNotEmpty()) {
                    CouiTag(
                        text = status.modeLabel,
                        backgroundColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer,
                    )
                }
                Box(Modifier.weight(1f))
                if (!status.connected) {
                    val reconnectSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .clip(CouiShape(24.dp, 2f))
                            .background(colorScheme.primary)
                            .couiButtonFeedback(dark, reconnectSource, true)
                            .clickable(reconnectSource, indication = null, role = Role.Button) {
                                ServiceBridge.rebind()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reconnect),
                            kind = CouiTextKind.ItemValue,
                            color = colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
