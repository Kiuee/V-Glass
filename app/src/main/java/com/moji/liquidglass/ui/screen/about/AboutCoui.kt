package com.moji.liquidglass.ui.screen.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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

private const val REPO_NAME = "Kiuee/V-Glass"
private const val REPO_URL = "https://github.com/Kiuee/V-Glass"

/** 关于应用（仿 demo 关于页）：V-Glass 图标 + 软件版本 + GitHub 仓库入口。 */
@Composable
fun AboutCoui(onBack: () -> Unit) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val colorScheme = CouiTheme.colorScheme
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }
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
                        text = stringResource(R.string.about_app),
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
            AboutHero(versionName)

            Group(colors = colors) {
                CouiPreferenceRow(
                    title = stringResource(R.string.about_version_title),
                    value = versionName,
                    showChevron = false,
                )
                Divider()
                CouiPreferenceRow(
                    title = stringResource(R.string.about_coui_version),
                    value = "0.0.1-alpha16",
                    showChevron = false,
                )
                Divider()
                CouiPreferenceRow(
                    title = stringResource(R.string.about_github),
                    value = REPO_NAME,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                    },
                )
            }
        }
    }
}
