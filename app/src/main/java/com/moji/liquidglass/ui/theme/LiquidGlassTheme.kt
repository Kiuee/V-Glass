package com.moji.liquidglass.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 应用主题入口：material-kolor 动态色板 + M3 MaterialTheme + COUI 的 LocalCouiDarkTheme。
 * COUI 默认外观策略由 GlassApp 中的 Coui.initialize(CouiMaterial3Style()) 固定，
 * 组件在消费处读取本处 MaterialTheme，因此不需要（也不能）再包一层 CouiTheme 原生色板。
 */
@Composable
fun LiquidGlassTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalColorMode provides appSettings.colorMode.value) {
        MaterialTemplateTheme(appSettings, content)
    }
}
