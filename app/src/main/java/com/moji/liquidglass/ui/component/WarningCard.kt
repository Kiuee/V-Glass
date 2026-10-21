package com.moji.liquidglass.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode
import com.moji.liquidglass.ui.theme.isInDarkTheme
import com.moji.liquidglass.ui.component.material.TonalCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 告警级别：错误（红）与提示（黄），配色对齐参考项目 WarningCard。 */
enum class WarningLevel { Error, Notice }

/** 警告条卡（对齐参考项目 WarningCard）：按当前界面风格分发。 */
@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> WarningCardMiuix(message, modifier, level, onClick, action)
        UiMode.Material -> WarningCardMaterial(message, modifier, level, onClick, action)
    }
}

@Composable
private fun WarningCardMiuix(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        colors = CardDefaults.defaultColors(
            color = level.miuixContainerColor(),
            contentColor = level.miuixContentColor(),
        ),
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixText(
                text = message,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun WarningCardMaterial(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val containerColor = level.materialContainerColor()
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor),
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
            )
            action?.invoke()
        }
    }
    if (onClick != null) {
        TonalCard(containerColor = containerColor, onClick = onClick, content = content)
    } else {
        TonalCard(containerColor = containerColor, content = content)
    }
}

@Composable
private fun WarningLevel.miuixContainerColor(): Color = when {
    isDynamicColor -> when (this) {
        WarningLevel.Error -> colorScheme.errorContainer
        WarningLevel.Notice -> colorScheme.tertiaryContainer
    }

    isInDarkTheme() -> when (this) {
        WarningLevel.Error -> Color(0xFF310808)
        WarningLevel.Notice -> Color(0xFF3E2F1B)
    }

    else -> when (this) {
        WarningLevel.Error -> Color(0xFFF8E2E2)
        WarningLevel.Notice -> Color(0xFFFFF0DB)
    }
}

@Composable
private fun WarningLevel.miuixContentColor(): Color = when {
    isDynamicColor -> when (this) {
        WarningLevel.Error -> colorScheme.onErrorContainer
        WarningLevel.Notice -> colorScheme.onTertiaryContainer
    }

    else -> when (this) {
        WarningLevel.Error -> Color(0xFFF72727)
        WarningLevel.Notice -> Color(0xFFF5A623)
    }
}

@Composable
private fun WarningLevel.materialContainerColor(): Color = when (this) {
    WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
    WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
}
