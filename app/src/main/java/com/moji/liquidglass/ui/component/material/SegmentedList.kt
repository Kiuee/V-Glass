@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.component.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 分组列表（对齐参考项目 SegmentedList）：material3 SegmentedListItem + 分段形状，
 * 组内 2dp 间距，条目通过 LocalListItemShapes 获取所在位置的分段圆角。
 */
val LocalListItemShapes = compositionLocalOf<ListItemShapes?> { null }

@Composable
private fun defaultSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors().copy(
    containerColor = colorScheme.surfaceColorAtElevation(1.dp),
    disabledContainerColor = colorScheme.surfaceColorAtElevation(1.dp),
    supportingContentColor = colorScheme.outline,
)

@Composable
private fun defaultSingleSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    return if (count == 1) {
        base.copy(shape = MaterialTheme.shapes.large)
    } else {
        base
    }
}

@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    visibleLen: Int = 0,
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalListItemShapes provides defaultSingleSegmentedShape(
                        index = index,
                        count = if (visibleLen > 0) visibleLen else content.size,
                    ),
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
private fun SegmentedItem(
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick ?: {},
        enabled = enabled,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = Modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        verticalAlignment = Alignment.CenterVertically,
        content = headlineContent,
    )
}

/** 开关条目：整行点击 + 触觉反馈 + ExpressiveSwitch。 */
@Composable
fun SegmentedSwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        content = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
    )
}

/** 勾选条目：整行点击 + 触觉反馈 + 勾选框，trailingContent（如适配 chip）置于勾选框左侧。 */
@Composable
fun SegmentedCheckboxItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        content = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent?.invoke()
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    interactionSource = interactionSource,
                )
            }
        },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
    )
}

/** 下拉条目：trailing 显示当前值文本，弹层为分组菜单（对齐参考项目）。 */
@Composable
fun SegmentedDropdownItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    items: List<String>,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    val hasItems = items.isNotEmpty()
    val safeIndex = if (hasItems) {
        selectedIndex.coerceIn(0, items.lastIndex)
    } else {
        -1
    }

    SegmentedItem(
        onClick = if (enabled) {
            {
                onClick?.invoke()
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                expanded = true
            }
        } else null,
        enabled = enabled,
        colors = colors,
        leadingContent = icon?.let { { Icon(it, title) } },
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        trailingContent = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                Text(
                    text = if (hasItems && safeIndex >= 0) items[safeIndex] else "",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(0.3f),
                    color = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
                DropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                        items.forEachIndexed { index, text ->
                            DropdownMenuItem(
                                text = { Text(text) },
                                selected = index == safeIndex,
                                onClick = {
                                    if (index in items.indices) {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        onItemSelected(index)
                                    }
                                    expanded = false
                                },
                                shapes = MenuDefaults.itemShape(index = index, count = items.size),
                                leadingIcon = {
                                    if (index == safeIndex) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 单选条目：前置 RadioButton（对齐参考项目）。 */
@Composable
fun SegmentedRadioItem(
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    SegmentedListItem(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        content = { Text(title) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
    )
}

/** 入口条目：整行点击跳转子页面，尾部箭头。 */
@Composable
fun SegmentedArrowItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        content = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        },
    )
}

/** 单行说明条目：一行正文，多点说明按条目拆分。 */
@Composable
fun SegmentedNoteItem(
    text: String,
    colors: ListItemColors = defaultSegmentedColors(),
    icon: ImageVector? = null,
) {
    SegmentedItem(
        colors = colors,
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant) },
        leadingContent = icon?.let { { Icon(it, text, tint = colorScheme.outline) } },
    )
}

/** 信息条目：图标 + 标题 + 内容副文本（对齐参考项目 InfoCardItem）。 */
@Composable
fun SegmentedInfoItem(
    icon: ImageVector,
    title: String,
    content: String,
    colors: ListItemColors = defaultSegmentedColors(),
) {
    SegmentedItem(
        colors = colors,
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, title) },
        supportingContent = {
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** 滑条条目：标题 + 当前值，拖动即时显示、松手提交（onCommit）；数值文本可点击（onValueTextClick，如键盘输入）。 */
@Composable
fun SegmentedSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
    onValueTextClick: (() -> Unit)? = null,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableStateOf(value) }
    SegmentedItem(
        colors = colors,
        headlineContent = {
            Column(Modifier.padding(vertical = 14.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        valueText(dragging),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.outline,
                        modifier = if (onValueTextClick != null && enabled) {
                            Modifier.clickable { onValueTextClick() }
                        } else {
                            Modifier
                        },
                    )
                }
                androidx.compose.material3.Slider(
                    value = dragging,
                    onValueChange = { dragging = it },
                    valueRange = valueRange,
                    enabled = enabled,
                    onValueChangeFinished = { onCommit(dragging) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        },
    )
}
