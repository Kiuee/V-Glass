package com.moji.liquidglass.ui

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
import com.moji.liquidglass.ui.screen.MainScreen
import com.moji.liquidglass.ui.theme.LiquidGlassTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceBridge.bind()
        ManagerPrefs.init(this)
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
                                MainScreen(
                                    onOpenTheme = {
                                        ManagerSecondaryActivity.open(
                                            this@MainActivity,
                                            ManagerSecondaryActivity.PAGE_THEME,
                                        )
                                    },
                                    onOpenBarStyle = {
                                        ManagerSecondaryActivity.open(
                                            this@MainActivity,
                                            ManagerSecondaryActivity.PAGE_BARSTYLE,
                                        )
                                    },
                                    onOpenUsage = {
                                        ManagerSecondaryActivity.open(
                                            this@MainActivity,
                                            ManagerSecondaryActivity.PAGE_USAGE,
                                        )
                                    },
                                    onOpenAbout = {
                                        ManagerSecondaryActivity.open(
                                            this@MainActivity,
                                            ManagerSecondaryActivity.PAGE_ABOUT,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
