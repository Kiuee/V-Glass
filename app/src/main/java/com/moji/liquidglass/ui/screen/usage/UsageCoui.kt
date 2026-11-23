package com.moji.liquidglass.ui.screen.usage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Scaffold
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.component.coui.CouiPreferenceRow
import com.moji.liquidglass.ui.component.coui.CouiTopBarAction

/** 使用方法（从首页折叠出来的三行步骤）。 */
@Composable
fun UsageCoui(onBack: () -> Unit) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = CouiTheme.colorScheme
    val scrollState = rememberScrollState()
    val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.maxValue > 0 },
    )
    val density = LocalDensity.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(behavior.nestedScrollConnection),
        containerColor = colors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.usage_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    CouiTopBarAction(
                        description = stringResource(R.string.action_cancel),
                        onClick = onBack,
                    ) {
                        Icon(
                            imageVector = CouiIcons.Filled.Back,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = behavior,
                dark = dark,
            )
        },
    ) { padding ->
        val contentPadding = remember(behavior, padding, density) {
            behavior.state.contentPadding(padding, density)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .consumeWindowInsets(padding)
                .padding(bottom = CouiPageDefaults.groupSpacing),
        ) {
            Group(title = stringResource(R.string.usage_title), colors = colors) {
                CouiPreferenceRow(stringResource(R.string.usage_step_1), showChevron = false)
                Divider()
                CouiPreferenceRow(stringResource(R.string.usage_step_2), showChevron = false)
                Divider()
                CouiPreferenceRow(stringResource(R.string.usage_step_3), showChevron = false)
            }
        }
    }
}
