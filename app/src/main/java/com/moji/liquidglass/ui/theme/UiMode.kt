package com.moji.liquidglass.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** 管理器界面风格：Miuix（悬浮玻璃）或 Material（Expressive）。 */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Material.value -> Material
            else -> Miuix
        }

        val DEFAULT_VALUE = Material.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
