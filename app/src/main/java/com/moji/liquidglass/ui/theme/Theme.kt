package com.moji.liquidglass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 颜色模式：跟随系统 / 强制浅色 / 强制深色。
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(value: Int): ColorMode = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = this == SYSTEM
    val isDark: Boolean get() = this == DARK
}

/** 主题参数快照：由 ManagerPrefs 生成。 */
data class AppSettings(
    val colorMode: ColorMode,
    /** true = Material 主题配色（M3 系统动态色），false = 原生 COUI（CouiPresets）。 */
    val materialTheme: Boolean = false,
    /** COUI 轮廓光（CouiContourGlowEnabled）。 */
    val contour: Boolean = true,
    /** 顶栏渐进模糊：true = Compose 后端，false = COUI 后端（皆 33+）。 */
    val composeProgressiveBlur: Boolean = true,
)

/** 按当前颜色模式判断是否深色（强制浅深色优先于系统设置）。 */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalColorMode = staticCompositionLocalOf { 0 }
