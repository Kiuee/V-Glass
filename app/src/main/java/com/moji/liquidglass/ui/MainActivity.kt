package com.moji.liquidglass.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.ui.component.bottombar.BottomBar
import com.moji.liquidglass.ui.component.bottombar.LocalMainPagerState
import com.moji.liquidglass.ui.component.bottombar.MAIN_PAGE_COUNT
import com.moji.liquidglass.ui.component.bottombar.MainPagerState
import com.moji.liquidglass.ui.component.bottombar.rememberMainPagerState
import com.moji.liquidglass.ui.navigation.Route
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.screen.apps.AppsPager
import com.moji.liquidglass.ui.screen.barstyle.BarStyleScreen
import com.moji.liquidglass.ui.screen.home.HomePager
import com.moji.liquidglass.ui.screen.settings.SettingPager
import com.moji.liquidglass.ui.screen.theme.ThemeScreen
import com.moji.liquidglass.ui.theme.LiquidGlassTheme
import com.moji.liquidglass.ui.theme.LocalColorMode
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.theme.LocalEnableFloatingBottomBar
import com.moji.liquidglass.ui.theme.LocalEnableFloatingBottomBarBlur
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceBridge.bind()
        ManagerPrefs.init(this)
        setContent {
            val prefs = ManagerPrefs
            val appSettings = prefs.appSettings
            val uiMode = prefs.uiMode
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

            LiquidGlassTheme(appSettings = appSettings, uiMode = uiMode) {
                AppRoot()
            }
        }
    }
}

/** 路由宿主：主界面与两个设置子页间的轻量导航（带横向转场 + 返回键处理）。 */
@Composable
private fun AppRoot() {
    var route by rememberSaveable { mutableStateOf(Route.Main) }
    // Pager 状态提升到路由层：进入子页再返回时，主界面停留在原页（如设置页）
    val pagerState = rememberPagerState(initialPage = 0) { MAIN_PAGE_COUNT }
    val mainPagerState = rememberMainPagerState(pagerState)

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val entering = slideInHorizontally(tween(300)) { if (targetState == Route.Main) -it / 4 else it / 4 } +
                fadeIn(tween(300))
            val exiting = slideOutHorizontally(tween(300)) { if (targetState == Route.Main) it / 4 else -it / 4 } +
                fadeOut(tween(300))
            entering togetherWith exiting
        },
        label = "appRoot",
    ) { current ->
        when (current) {
            Route.Main -> MainScreen(
                mainPagerState = mainPagerState,
                onOpenTheme = { route = Route.ThemeSettings },
                onOpenBarStyle = { route = Route.BarStyleSettings },
            )
            Route.ThemeSettings -> ThemeScreen(onBack = { route = Route.Main })
            Route.BarStyleSettings -> BarStyleScreen(onBack = { route = Route.Main })
        }
    }

    BackHandler(enabled = route != Route.Main) { route = Route.Main }
}

/** 主界面：外层 Scaffold（底栏）+ 三页 Pager（主页/应用/设置），双风格分发。 */
@Composable
private fun MainScreen(
    mainPagerState: MainPagerState,
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val uiMode = LocalUiMode.current

    val config = ConfigController.remember()

    // 顶栏毛玻璃的内容采样层（Miuix 风格用）
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    // 悬浮玻璃底栏的内容折射层：底栏开启模糊时对整个 Pager 内容采样
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        LaunchedEffect(mainPagerState) {
            snapshotFlow { mainPagerState.pagerState.currentPage }.collect {
                mainPagerState.syncPage()
            }
        }

        val pagerContent: @Composable (Dp) -> Unit = { bottomInnerPadding ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    state = mainPagerState.pagerState,
                    modifier = Modifier.then(
                        if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        }
                    ),
                ) { page ->
                    when (page) {
                        0 -> HomePager(bottomInnerPadding)
                        1 -> AppsPager(bottomInnerPadding, config)
                        2 -> SettingPager(
                            bottomInnerPadding = bottomInnerPadding,
                            config = config,
                            onOpenTheme = onOpenTheme,
                            onOpenBarStyle = onOpenBarStyle,
                        )
                    }
                }
            }
        }

        val bottomBar: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxWidth()) {
                BottomBar(blurBackdrop, backdrop, Modifier.align(Alignment.BottomCenter))
            }
        }

        when (uiMode) {
            UiMode.Material -> androidx.compose.material3.Scaffold(bottomBar = bottomBar) { innerPadding ->
                pagerContent(innerPadding.calculateBottomPadding())
            }
            UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Scaffold(bottomBar = bottomBar) { innerPadding ->
                pagerContent(innerPadding.calculateBottomPadding())
            }
        }
    }
}
