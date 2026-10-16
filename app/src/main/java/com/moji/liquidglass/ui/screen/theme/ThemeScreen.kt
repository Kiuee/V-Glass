package com.moji.liquidglass.ui.screen.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.materialkolor.PaletteStyle
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.theme.ColorMode
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

/** 颜色模式候选（Material 端 ToggleButton 四组）。 */
internal val colorModeOptions: List<ColorMode> = listOf(
    ColorMode.SYSTEM,
    ColorMode.LIGHT,
    ColorMode.DARK,
    ColorMode.DARK_AMOLED,
)

@Composable
internal fun colorModeLabel(mode: ColorMode): String = stringResource(
    when (mode) {
        ColorMode.LIGHT -> R.string.color_mode_light
        ColorMode.DARK -> R.string.color_mode_dark
        ColorMode.DARK_AMOLED -> R.string.color_mode_dark_amoled
        else -> R.string.color_mode_system
    }
)

/** 主题设置页分发器。 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Material -> ThemeScreenMaterial(onBack)
        UiMode.Miuix -> ThemeScreenMiuix(onBack)
    }
}
