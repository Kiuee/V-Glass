package com.moji.liquidglass.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.topappbar.ProvideCouiTopAppBarBlur
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.screen.about.AboutCoui
import com.moji.liquidglass.ui.screen.barstyle.BarStyleCoui
import com.moji.liquidglass.ui.screen.theme.ThemeCoui
import com.moji.liquidglass.ui.screen.usage.UsageCoui
import com.moji.liquidglass.ui.theme.LiquidGlassTheme

/**
 * 二级页面宿主（对齐 demo `DemoSecondaryActivity`）：主题 / 玻璃栏样式各为同一任务内的真实
 * Activity，启动/结束转场交由系统 ROM 处理；返回直接 finish()。
 */
class ManagerSecondaryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceBridge.bind()
        ManagerPrefs.init(this)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_THEME
        setContent {
            val appSettings = ManagerPrefs.appSettings
            val darkMode = appSettings.colorMode.isDark ||
                (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            LiquidGlassTheme(appSettings = appSettings) {
                ProvideCouiTopAppBarBlur(useComposeBlur = appSettings.composeProgressiveBlur) {
                    CompositionLocalProvider(LocalCouiContourGlowEnabled provides appSettings.contour) {
                        ProvideCouiMenuBackdrop(isolateContent = false) { windowSource ->
                            Box(Modifier.fillMaxSize().then(windowSource)) {
                                when (page) {
                                    PAGE_BARSTYLE -> BarStyleCoui(onBack = { finish() })
                                    PAGE_ABOUT -> AboutCoui(onBack = { finish() })
                                    PAGE_USAGE -> UsageCoui(onBack = { finish() })
                                    else -> ThemeCoui(onBack = { finish() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PAGE = "com.moji.liquidglass.EXTRA_PAGE"
        const val PAGE_THEME = "theme"
        const val PAGE_BARSTYLE = "barstyle"
        const val PAGE_ABOUT = "about"
        const val PAGE_USAGE = "usage"

        fun open(activity: Activity, page: String) {
            activity.startActivity(
                Intent(activity, ManagerSecondaryActivity::class.java).putExtra(EXTRA_PAGE, page)
            )
        }
    }
}
