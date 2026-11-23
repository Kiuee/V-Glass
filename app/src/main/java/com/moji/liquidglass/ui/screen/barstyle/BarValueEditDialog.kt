package com.moji.liquidglass.ui.screen.barstyle

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
