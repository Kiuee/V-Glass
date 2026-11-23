package com.moji.liquidglass.ui.component.coui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 图标彩色预设（参考 demo 组件页各图标不同颜色）；Material 主题开启后统一为强调色。 */
val CouiIconBlue = Color(0xFF2196F3)
val CouiIconGreen = Color(0xFF4CAF50)
val CouiIconPurple = Color(0xFF9C27B0)
val CouiIconOrange = Color(0xFFFF9800)
val CouiIconCyan = Color(0xFF00BCD4)
val CouiIconPink = Color(0xFFE91E63)

@Composable
internal fun couiIconColor(native: Color): Color =
    if (ManagerPrefs.materialTheme) MaterialTheme.colorScheme.primary else native
