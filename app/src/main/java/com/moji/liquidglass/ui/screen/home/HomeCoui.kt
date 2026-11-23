package com.moji.liquidglass.ui.screen.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.theme.CouiTheme
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.prefs.ManagerPrefs
import com.moji.liquidglass.ui.component.coui.CouiBanner
import com.moji.liquidglass.ui.component.coui.CouiIconBlue
import com.moji.liquidglass.ui.component.coui.CouiIconGreen
import com.moji.liquidglass.ui.component.coui.CouiIconPurple
import com.moji.liquidglass.ui.component.coui.couiIconColor
import com.moji.liquidglass.ui.component.coui.CouiPreferenceRow

/** 主页（COUI）：警示条 + 状态大卡片 + 信息 / 使用方法 / GitHub 分组。 */
private const val REPO_NAME = "Kiuee/V-Glass"
private const val REPO_URL = "https://github.com/Kiuee/V-Glass"
@Composable
internal fun HomeCoui(
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    onOpenUsage: () -> Unit,
) {
    val status = rememberBridgeStatus()
    val systemInfo = rememberHomeSystemInfo()
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .padding(bottom = CouiPageDefaults.groupSpacing),
    ) {
        if (status.warning.isNotBlank()) {
            CouiBanner(
                message = status.warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            HomeStatusCard(status)
        }

        Group(colors = colors) {
            InfoRow(CouiIcons.Filled.Lab, R.string.home_app_version, systemInfo.appVersion, colors, tint = couiIconColor(CouiIconGreen))
            Divider()
            InfoRow(CouiIcons.Filled.SettingDetail, R.string.home_system_version, systemInfo.androidVersion, colors, tint = couiIconColor(CouiIconBlue))
            Divider()
            InfoRow(CouiIcons.Filled.Phone, R.string.home_device_model, systemInfo.deviceModel, colors, tint = couiIconColor(CouiIconGreen))
        }

        if (systemInfo.frameworkVersion.isNotEmpty()) {
            Group(colors = colors) {
                InfoRow(CouiIcons.Filled.Certificate, R.string.home_framework, systemInfo.frameworkVersion, colors, tint = couiIconColor(CouiIconPurple))
            }
        }

        Group(colors = colors) {
            CouiPreferenceRow(
                title = stringResource(R.string.usage_title),
                onClick = onOpenUsage,
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

@Composable
private fun InfoRow(
    icon: ImageVector,
    titleRes: Int,
    value: String,
    colors: CouiPageColors,
    tint: androidx.compose.ui.graphics.Color = colors.title,
) {
    CouiPreferenceRow(
        title = stringResource(titleRes),
        value = value,
        showChevron = false,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        },
    )
}
