package com.moji.liquidglass.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.moji.liquidglass.ui.theme.AppSettings
import com.moji.liquidglass.ui.theme.ColorMode
import com.moji.liquidglass.ui.theme.UiMode

/**
 * 管理器外观设置：本地 SharedPreferences + Compose 快照状态同步读写。
 * 只影响管理器自身 UI，与模块功能配置（LSPosed RemotePreferences）完全分离。
 *
 * 颜色模式采用参考项目（KernelSU Style UI Kit）的「写入时换算，存储即生效」模型：
 * themeMode 存储的就是当前生效模式，MONET_* 只在「Miuix + 动态取色开」时出现；
 * 读取侧（[appSettings]）仅按 miuixMonet 做防御性纠偏。
 */
object ManagerPrefs {

    private const val PREFS_NAME = "settings"
    private const val KEY_UI_MODE = "ui_mode"
    private const val KEY_COLOR_MODE = "color_mode"
    private const val KEY_KEY_COLOR = "key_color"
    private const val KEY_COLOR_STYLE = "color_style"
    private const val KEY_COLOR_SPEC = "color_spec"
    private const val KEY_ENABLE_BLUR = "enable_blur"
    private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar"
    private const val KEY_FLOATING_BOTTOM_BAR_BLUR = "floating_bottom_bar_blur"
    private const val KEY_MIUIX_MONET = "miuix_monet"
    private const val KEY_UNADAPTED_DISMISSED = "unadapted_dismissed_signature"

    private lateinit var prefs: SharedPreferences

    private var uiModeState by mutableStateOf(UiMode.fromValue(UiMode.DEFAULT_VALUE))
    private var colorModeValueState by mutableStateOf(ColorMode.SYSTEM.value)
    private var keyColorState by mutableStateOf(0)
    private var paletteStyleState by mutableStateOf(PaletteStyle.TonalSpot)
    private var colorSpecState by mutableStateOf(ColorSpec.SpecVersion.Default)
    private var enableBlurState by mutableStateOf(false)
    private var floatingBottomBarState by mutableStateOf(true)
    private var floatingBottomBarBlurState by mutableStateOf(true)
    private var miuixMonetState by mutableStateOf(false)
    private var unadaptedDismissedState by mutableStateOf("")

    /** 幂等，Application 与 Activity 均可调用。 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        uiModeState = UiMode.fromValue(prefs.getString(KEY_UI_MODE, null) ?: UiMode.DEFAULT_VALUE)
        colorModeValueState = prefs.getInt(KEY_COLOR_MODE, ColorMode.SYSTEM.value)
        keyColorState = prefs.getInt(KEY_KEY_COLOR, 0)
        paletteStyleState = try {
            PaletteStyle.valueOf(
                prefs.getString(KEY_COLOR_STYLE, null) ?: PaletteStyle.TonalSpot.name
            )
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        colorSpecState = try {
            ColorSpec.SpecVersion.valueOf(
                prefs.getString(KEY_COLOR_SPEC, null) ?: ColorSpec.SpecVersion.Default.name
            )
        } catch (_: Exception) {
            ColorSpec.SpecVersion.Default
        }
        enableBlurState = prefs.getBoolean(KEY_ENABLE_BLUR, false)
        floatingBottomBarState = prefs.getBoolean(KEY_FLOATING_BOTTOM_BAR, true)
        floatingBottomBarBlurState = prefs.getBoolean(KEY_FLOATING_BOTTOM_BAR_BLUR, true)
        miuixMonetState = prefs.getBoolean(KEY_MIUIX_MONET, false)
        unadaptedDismissedState = prefs.getString(KEY_UNADAPTED_DISMISSED, null) ?: ""
    }

    val uiMode: UiMode get() = uiModeState
    val colorMode: ColorMode get() = ColorMode.fromValue(colorModeValueState)
    val keyColor: Int get() = keyColorState
    val paletteStyle: PaletteStyle get() = paletteStyleState
    val colorSpec: ColorSpec.SpecVersion get() = colorSpecState
    val enableBlur: Boolean get() = enableBlurState
    val floatingBottomBar: Boolean get() = floatingBottomBarState
    val floatingBottomBarBlur: Boolean get() = floatingBottomBarBlurState
    val miuixMonet: Boolean get() = miuixMonetState

    /** 应用页未适配红条的已关闭签名：与当前未适配集签名一致时不显示，集合变化后重现。 */
    val unadaptedDismissed: String get() = unadaptedDismissedState

    /**
     * 当前生效的主题参数。读取侧只做与参考 ThemeController.getAppSettings 相同的
     * 防御性纠偏（Miuix 下按 miuixMonet 把模式归到 Monet/非 Monet 一致态），
     * 正常流程下写入侧已保证存储即生效，此处为 no-op。
     */
    val appSettings: AppSettings
        get() {
            var value = colorModeValueState
            if (uiModeState == UiMode.Miuix) {
                val mode = ColorMode.fromValue(value)
                value = when {
                    !miuixMonetState && mode.isMonet -> mode.toNonMonetMode()
                    miuixMonetState && !mode.isMonet -> mode.toMonetMode()
                    else -> value
                }
            }
            return AppSettings(
                ColorMode.fromValue(value),
                keyColorState,
                paletteStyleState,
                colorSpecState,
            )
        }

    /** 切换界面风格：跨风格换算颜色模式（参考 SettingsViewModel.setUiMode）。 */
    fun setUiMode(mode: UiMode) {
        val oldMode = uiModeState
        val current = colorModeValueState
        val newThemeMode = when {
            oldMode == UiMode.Material && mode == UiMode.Miuix -> {
                val colorMode = ColorMode.fromValue(current)
                val base = if (colorMode == ColorMode.DARK_AMOLED) ColorMode.DARK.value else current
                when {
                    miuixMonetState && !colorMode.isMonet ->
                        ColorMode.fromValue(base).toMonetMode()
                    !miuixMonetState && colorMode.isMonet ->
                        ColorMode.fromValue(base).toNonMonetMode()
                    else -> base
                }
            }
            oldMode == UiMode.Miuix && mode == UiMode.Material -> {
                val colorMode = ColorMode.fromValue(current)
                if (colorMode.isMonet) colorMode.toNonMonetMode() else current
            }
            else -> current
        }
        uiModeState = mode
        colorModeValueState = newThemeMode
        prefs.edit()
            .putString(KEY_UI_MODE, mode.value)
            .putInt(KEY_COLOR_MODE, newThemeMode)
            .apply()
    }

    /** Miuix TabRow 切换：动态取色开启时写入 MONET_* 变体（参考 setThemeMode）。 */
    fun setThemeModeIndex(index: Int) {
        val effective = if (uiModeState == UiMode.Miuix && miuixMonetState) index + 3 else index
        colorModeValueState = effective
        prefs.edit().putInt(KEY_COLOR_MODE, effective).apply()
    }

    /** Material ToggleButton 切换：直接写值。 */
    fun setColorMode(mode: ColorMode) {
        colorModeValueState = mode.value
        prefs.edit().putInt(KEY_COLOR_MODE, mode.value).apply()
    }

    /** Miuix 动态取色开关：切换时同步把颜色模式换算到 Monet/非 Monet（参考 setMiuixMonet）。 */
    fun setMiuixMonet(enabled: Boolean) {
        val colorMode = ColorMode.fromValue(colorModeValueState)
        val newThemeMode = when {
            enabled && !colorMode.isMonet -> colorMode.toMonetMode()
            !enabled && colorMode.isMonet -> colorMode.toNonMonetMode()
            else -> colorModeValueState
        }
        miuixMonetState = enabled
        colorModeValueState = newThemeMode
        prefs.edit()
            .putBoolean(KEY_MIUIX_MONET, enabled)
            .putInt(KEY_COLOR_MODE, newThemeMode)
            .apply()
    }

    fun setKeyColor(value: Int) {
        keyColorState = value
        prefs.edit().putInt(KEY_KEY_COLOR, value).apply()
    }

    fun setPaletteStyle(style: PaletteStyle) {
        paletteStyleState = style
        prefs.edit().putString(KEY_COLOR_STYLE, style.name).apply()
    }

    fun setColorSpec(spec: ColorSpec.SpecVersion) {
        colorSpecState = spec
        prefs.edit().putString(KEY_COLOR_SPEC, spec.name).apply()
    }

    fun setEnableBlur(on: Boolean) {
        enableBlurState = on
        prefs.edit().putBoolean(KEY_ENABLE_BLUR, on).apply()
    }

    fun setFloatingBottomBar(on: Boolean) {
        floatingBottomBarState = on
        prefs.edit().putBoolean(KEY_FLOATING_BOTTOM_BAR, on).apply()
    }

    fun setFloatingBottomBarBlur(on: Boolean) {
        floatingBottomBarState = on
        prefs.edit().putBoolean(KEY_FLOATING_BOTTOM_BAR_BLUR, on).apply()
    }

    /** 记住「已关闭未适配红条」时的未适配集签名。 */
    fun setUnadaptedDismissed(signature: String) {
        unadaptedDismissedState = signature
        prefs.edit().putString(KEY_UNADAPTED_DISMISSED, signature).apply()
    }
}
