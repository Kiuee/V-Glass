package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 样式页滑杆数值的键盘输入描述：点击滑杆旁的数值文本打开输入弹窗。
 *
 * @param title        弹窗标题（= 条目标题）
 * @param range        原始值允许范围（与滑杆一致）
 * @param format       显示格式（带单位）：滑杆旁数值与"允许范围"提示共用
 * @param toEditable   输入框预填的纯数字文本（无单位，便于直接改数字）
 * @param parse        输入解析：无法解析返回 null；可解析时收敛进 range
 * @param current      打开弹窗时的当前值
 * @param commit       确认提交
 */
internal class BarValueEditor(
    val title: String,
    val range: ClosedFloatingPointRange<Float>,
    val format: (Float) -> String,
    val toEditable: (Float) -> String,
    val parse: (String) -> Float?,
    val current: Float,
    val commit: (Float) -> Unit,
)

/** 数值键盘输入弹窗 Material 版：Material3 AlertDialog + OutlinedTextField。 */
@Composable
internal fun BarValueEditDialogMaterial(
    editor: BarValueEditor,
    onDismiss: () -> Unit,
) {
    // 无 key 的 remember：弹窗离场组合即丢弃，重开时以 current 重新预填
    var text by remember { mutableStateOf(editor.toEditable(editor.current)) }
    val parsed = editor.parse(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.bar_value_edit_range,
                                editor.format(editor.range.start),
                                editor.format(editor.range.endInclusive),
                            )
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    parsed?.let(editor.commit)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 数值键盘输入弹窗 Miuix 版：官方 WindowDialog + TextField（v0.9.2，窗口级无需 MiuixPopupHost），
 *  范围提示走 summary，按钮排布按官方示例（取消/确定等宽并排、确定主题主色）。 */
@Composable
internal fun BarValueEditDialogMiuix(
    editor: BarValueEditor,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(editor.toEditable(editor.current)) }
    val parsed = editor.parse(text)
    val dismissState = LocalDismissState.current
    val dismiss: () -> Unit = { dismissState?.invoke() ?: onDismiss() }

    WindowDialog(
        show = true,
        title = editor.title,
        summary = stringResource(
            R.string.bar_value_edit_range,
            editor.format(editor.range.start),
            editor.format(editor.range.endInclusive),
        ),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
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
                enabled = parsed != null,
                onClick = {
                    parsed?.let(editor.commit)
                    dismiss()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
