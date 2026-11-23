package com.moji.liquidglass.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiDarkTheme

/**
 * 管理器主题（对齐 COUI demo `DemoTheme`）：
 * - Material 主题配色：M3 系统动态色，页面背景/表面用 surfaceContainer；
 * - 原生 COUI：CouiPresets 映射出 M3 兼容色 + CouiTheme 原生色板。
 * 两分支共用 LocalCouiDarkTheme / LocalContentColor；切换用 movableContentOf 保留页面状态。
 */
@Composable
fun MaterialTemplateTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDark)

    val colors = if (appSettings.materialTheme) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynamic.copy(
            surface = dynamic.surfaceContainer,
            background = dynamic.surfaceContainer,
        )
    } else {
        val native = if (dark) CouiPresets.Dark else CouiPresets.Light
        val inverse = if (dark) CouiPresets.Light else CouiPresets.Dark
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = native.accent,
            onPrimary = native.onAccent,
            primaryContainer = native.container,
            onPrimaryContainer = native.onSurface,
            inversePrimary = inverse.accent,
            secondary = native.onSurfaceVariant,
            onSecondary = native.surface,
            secondaryContainer = native.container,
            onSecondaryContainer = native.onSurface,
            tertiary = native.accentOnSurface,
            onTertiary = native.onAccent,
            tertiaryContainer = native.surface,
            onTertiaryContainer = native.onSurface,
            background = native.background,
            onBackground = native.onSurface,
            surface = native.surface,
            onSurface = native.onSurface,
            surfaceVariant = native.container,
            onSurfaceVariant = native.onSurfaceVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = inverse.surface,
            inverseOnSurface = inverse.onSurface,
            outline = native.onSurfaceVariant,
            outlineVariant = native.outlineVariant,
            surfaceBright = native.container,
            surfaceDim = native.background,
            surfaceContainer = native.background,
            surfaceContainerLow = native.surface,
            surfaceContainerLowest = native.background,
            surfaceContainerHigh = native.container,
            surfaceContainerHighest = native.container,
        )
    }

    LaunchedEffect(dark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    val currentContent by rememberUpdatedState(content)
    val scopedContent = remember { movableContentOf { currentContent() } }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalCouiDarkTheme provides dark,
            LocalContentColor provides colors.onBackground,
        ) {
            if (appSettings.materialTheme) {
                scopedContent()
            } else {
                CouiTheme(
                    darkTheme = dark,
                    content = scopedContent,
                )
            }
        }
    }
}
