package com.moji.liquidglass.ui.component.coui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.InputDialog
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.screen.barstyle.BarValueEditor

/** 滑杆数值键盘输入弹窗（COUI InputDialog）：按显示单位预填、越界由 parse 收敛。 */
@Composable
internal fun CouiValueDialog(
    editor: BarValueEditor,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(editor.toEditable(editor.current)) }
    val parsed = editor.parse(text)
    val backdrop = rememberCouiWindowBackdrop()
    val dark = CouiTheme.isDark
    val material = if (CouiDialogDefaults.isMaterialSupported()) {
        CouiDialogDefaults.material(dark)
    } else {
        null
    }
    val cancelLabel = stringResource(R.string.action_cancel)
    val confirmLabel = stringResource(R.string.action_confirm)

    InputDialog(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { text = it.text },
        onDismissRequest = onDismiss,
        confirmAction = CouiDialogAction(
            text = confirmLabel,
            onClick = {
                parsed?.let {
                    editor.commit(it)
                    onDismiss()
                }
            },
            enabled = parsed != null,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
        ),
        dark = dark,
        clearContentDescription = stringResource(R.string.apps_search_clear),
        title = editor.title,
        message = stringResource(
            R.string.bar_value_edit_range,
            editor.format(editor.range.start),
            editor.format(editor.range.endInclusive),
        ),
        dismissAction = CouiDialogAction(cancelLabel, onDismiss),
        backdrop = backdrop,
        material = material,
        visible = true,
    )
}
