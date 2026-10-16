package com.moji.liquidglass.ui.screen.barstyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

/** 玻璃栏样式设置页分发器。 */
@Composable
fun BarStyleScreen(onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Material -> BarStyleScreenMaterial(onBack)
        UiMode.Miuix -> BarStyleScreenMiuix(onBack)
    }
}
