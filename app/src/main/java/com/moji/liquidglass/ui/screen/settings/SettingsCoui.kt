package com.moji.liquidglass.ui.screen.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.theme.CouiTheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.component.coui.CouiIconCyan
import com.moji.liquidglass.ui.component.coui.CouiIconOrange
import com.moji.liquidglass.ui.component.coui.CouiPreferenceRow
import com.moji.liquidglass.ui.component.coui.couiIconColor

/** 设置页（COUI）：主题 / 玻璃栏样式入口。 */
@Composable
internal fun SettingsCoui(
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    onOpenTheme: () -> Unit,
    onOpenBarStyle: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .padding(bottom = CouiPageDefaults.groupSpacing),
    ) {
        Group(colors = colors) {
            CouiPreferenceRow(
                title = stringResource(R.string.theme_settings),
                summary = stringResource(R.string.theme_settings_summary),
                onClick = onOpenTheme,
                icon = {
                    Icon(
                        imageVector = CouiIcons.Filled.DarkLight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = couiIconColor(CouiIconOrange),
                    )
                },
            )
            Divider()
            CouiPreferenceRow(
                title = stringResource(R.string.bar_style),
                summary = stringResource(R.string.bar_style_summary),
                onClick = onOpenBarStyle,
                icon = {
                    Icon(
                        imageVector = CouiIcons.Filled.GridSliders,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = couiIconColor(CouiIconCyan),
                    )
                },
            )
        }

        Group(colors = colors) {
            CouiPreferenceRow(
                title = stringResource(R.string.about_app),
                onClick = onOpenAbout,
            )
        }
    }
}
