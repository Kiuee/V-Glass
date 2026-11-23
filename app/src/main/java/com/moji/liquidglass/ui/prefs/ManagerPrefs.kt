package com.moji.liquidglass.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.liquidglass.ui.theme.AppSettings
import com.moji.liquidglass.ui.theme.ColorMode

/**
 * 管理器外观设置：本地 SharedPreferences + Compose 快照状态同步读写。
 * 只影响管理器自身 UI，与模块功能配置（LSPosed RemotePreferences）完全分离。
 *
 * COUI 收口后只保留颜色模式 / Material 主题 / 轮廓光 / 顶栏渐进模糊；
 * 旧 ui_mode / key_color / color_style / color_spec / 悬浮 / 模糊 / miuix_monet 键不再读取。
 */
object ManagerPrefs {

    private const val PREFS_NAME = "settings"
    private const val KEY_COLOR_MODE = "color_mode"
    private const val KEY_MATERIAL_THEME = "material_theme"
    private const val KEY_CONTOUR = "contour"
    private const val KEY_COMPOSE_BLUR = "compose_progressive_blur"
    private const val KEY_UNADAPTED_DISMISSED = "unadapted_dismissed_signature"

    private lateinit var prefs: SharedPreferences

    private var colorModeValueState by mutableStateOf(ColorMode.SYSTEM.value)
    private var materialThemeState by mutableStateOf(false)
    private var contourState by mutableStateOf(true)
    private var composeBlurState by mutableStateOf(true)
    private var unadaptedDismissedState by mutableStateOf("")

    /** 幂等，Application 与 Activity 均可调用。 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        colorModeValueState = prefs.getInt(KEY_COLOR_MODE, ColorMode.SYSTEM.value)
        materialThemeState = prefs.getBoolean(KEY_MATERIAL_THEME, false)
        contourState = prefs.getBoolean(KEY_CONTOUR, true)
        composeBlurState = prefs.getBoolean(KEY_COMPOSE_BLUR, true)
        unadaptedDismissedState = prefs.getString(KEY_UNADAPTED_DISMISSED, null) ?: ""
    }

    val colorMode: ColorMode get() = ColorMode.fromValue(colorModeValueState)
    val materialTheme: Boolean get() = materialThemeState
    val contour: Boolean get() = contourState
    val composeProgressiveBlur: Boolean get() = composeBlurState

    /** 应用页未适配红条的已关闭签名：与当前未适配集签名一致时不显示，集合变化后重现。 */
    val unadaptedDismissed: String get() = unadaptedDismissedState

    /** 当前生效的主题参数。 */
    val appSettings: AppSettings
        get() = AppSettings(
            colorMode = ColorMode.fromValue(colorModeValueState),
            materialTheme = materialThemeState,
            contour = contourState,
            composeProgressiveBlur = composeBlurState,
        )

    /** 基础深浅色模式（SYSTEM/LIGHT/DARK）。 */
    fun setColorMode(mode: ColorMode) {
        colorModeValueState = mode.value
        prefs.edit().putInt(KEY_COLOR_MODE, mode.value).apply()
    }

    fun setMaterialTheme(enabled: Boolean) {
        materialThemeState = enabled
        prefs.edit().putBoolean(KEY_MATERIAL_THEME, enabled).apply()
    }

    fun setContour(enabled: Boolean) {
        contourState = enabled
        prefs.edit().putBoolean(KEY_CONTOUR, enabled).apply()
    }

    fun setComposeProgressiveBlur(enabled: Boolean) {
        composeBlurState = enabled
        prefs.edit().putBoolean(KEY_COMPOSE_BLUR, enabled).apply()
    }

    /** 主题页「重置外观」：颜色模式回系统、Material 主题关、轮廓光/渐进模糊开。 */
    fun resetAppearance() {
        setColorMode(ColorMode.SYSTEM)
        setMaterialTheme(false)
        setContour(true)
        setComposeProgressiveBlur(true)
    }

    /** 记住「已关闭未适配红条」时的未适配集签名。 */
    fun setUnadaptedDismissed(signature: String) {
        unadaptedDismissedState = signature
        prefs.edit().putString(KEY_UNADAPTED_DISMISSED, signature).apply()
    }
}
