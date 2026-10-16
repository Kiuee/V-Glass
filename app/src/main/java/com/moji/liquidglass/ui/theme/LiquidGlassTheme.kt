package com.moji.liquidglass.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/**
 * 应用主题入口：按 UiMode 分发 Material / Miuix 双风格实现，
 * 并通过 CompositionLocal 下发模糊、悬浮底栏等界面效果开关（低版本系统自动降级）。
 */
@Composable
fun LiquidGlassTheme(
    appSettings: AppSettings,
    uiMode: UiMode,
    content: @Composable () -> Unit,
) {
    // miuix-blur 的效果链基于 AGSL（API 33+），与 hook 注入门槛一致
    val blurCapable = Build.VERSION.SDK_INT >= 33
    CompositionLocalProvider(
        LocalUiMode provides uiMode,
        LocalColorMode provides appSettings.colorMode.value,
        LocalEnableBlur provides (ManagerPrefs.enableBlur && blurCapable),
        LocalEnableFloatingBottomBar provides ManagerPrefs.floatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides (ManagerPrefs.floatingBottomBarBlur && blurCapable),
    ) {
        when (uiMode) {
            UiMode.Material -> MaterialTemplateTheme(appSettings, content)
            UiMode.Miuix -> MiuixTemplateTheme(appSettings, content)
        }
    }
}
