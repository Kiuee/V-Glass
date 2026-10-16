package com.moji.liquidglass.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.ui.ConfigController
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

internal val uiModeLabels: List<String> = listOf("Miuix", "Material")

/** 设置页分发器。 */
@Composable
fun SettingPager(
    bottomInnerPadding: Dp,
    config: ConfigController,
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> SettingPagerMaterial(
            bottomInnerPadding = bottomInnerPadding,
            config = config,
            onOpenTheme = onOpenTheme,
            onOpenBarStyle = onOpenBarStyle,
        )
        UiMode.Miuix -> SettingPagerMiuix(
            bottomInnerPadding = bottomInnerPadding,
            config = config,
            onOpenTheme = onOpenTheme,
            onOpenBarStyle = onOpenBarStyle,
        )
    }
}

/** 玻璃栏样式入口的参数摘要（边距/间隙/栏高/模糊）。 */
@Composable
internal fun rememberBarStyleSummary(config: ConfigController): String {
    val c = config.config
    return remember(c) {
        listOf(c.barMarginDp, c.barBottomGapDp, c.barHeightDp, c.barBlurDp)
            .joinToString(" · ") { "%.0f dp".format(it) }
    }
}
