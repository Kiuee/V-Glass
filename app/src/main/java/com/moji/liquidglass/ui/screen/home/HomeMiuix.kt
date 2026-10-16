package com.moji.liquidglass.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 主页 Miuix 版（对齐参考项目 HomePagerMiuix）：大状态卡 + 版本 InfoCard + 单行说明条目。 */
@Composable
fun HomePagerMiuix(bottomInnerPadding: Dp) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val status = rememberBridgeStatus()
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.app_name),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCardMiuix(status)
                        InfoCardMiuix(versionName)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                MiuixNoteRow(text = stringResource(R.string.usage_step_1))
                                MiuixNoteRow(text = stringResource(R.string.usage_step_2))
                                MiuixNoteRow(text = stringResource(R.string.usage_step_3))
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                MiuixNoteRow(text = stringResource(R.string.about_requirement))
                                MiuixNoteRow(text = stringResource(R.string.about_diagnostics))
                            }
                        }
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

/** 模块状态卡（参考 PermissionCardMiuix）：164dp 大卡 + 右下大图标 + 底部状态行，未连接点击重连。 */
@Composable
private fun StatusCardMiuix(status: BridgeStatus) {
    val connected = status.connected
    val iconColor = if (connected) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (connected) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val textColor = Color(0xFF111111)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = { if (!connected) ServiceBridge.rebind() },
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 70.dp, y = 44.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(182.dp),
                    imageVector = if (connected) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 28.dp, end = 148.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = status.headline,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    Text(
                        text = status.summary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                    if (status.detail.isNotEmpty()) {
                        Text(
                            text = status.detail,
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.66f),
                        )
                    }
                }
                Text(
                    text = status.chipLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/** 版本信息卡（参考 InfoCard / InfoText）。 */
@Composable
private fun InfoCardMiuix(versionName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_app_version),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = versionName,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 单行说明行：小字副标题样式。 */
@Composable
private fun MiuixNoteRow(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
