package com.moji.liquidglass.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.theme.LocalEnableBlur
import com.moji.liquidglass.ui.theme.isInDarkTheme
import com.moji.liquidglass.ui.util.BlurredBar
import com.moji.liquidglass.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 主页 Miuix 版（对齐参考项目 HomePagerMiuix）：警告条 + 三态状态卡 + 带图标 InfoCard + 单行说明条目。 */
@Composable
fun HomePagerMiuix(bottomInnerPadding: Dp) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val status = rememberBridgeStatus()
    val systemInfo = rememberHomeSystemInfo()

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
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (status.warning.isNotBlank()) {
                            WarningCard(
                                message = status.warning,
                                level = WarningLevel.Error,
                            )
                        }
                        StatusCardMiuix(status)
                        InfoCardMiuix(systemInfo)
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

/**
 * 模块状态卡三态（对齐参考项目 StatusCard Miuix 版）：
 * 已连接 = 大绿卡（右下大图标 + 左下框架名 + 顶部标题/说明）；
 * 未连接 = BasicComponent 小卡 + 右侧重连按钮；点击卡片即重连。
 */
@Composable
private fun StatusCardMiuix(status: BridgeStatus) {
    when {
        status.connected -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = when {
                        isDynamicColor -> colorScheme.secondaryContainer
                        isInDarkTheme() -> Color(0xFF1A3825)
                        else -> Color(0xFFDFFAE4)
                    },
                ),
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(27.dp, 31.dp),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Icon(
                            modifier = Modifier.size(110.dp),
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            tint = if (isDynamicColor) {
                                colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                Color(0xFF36D167)
                            },
                            contentDescription = null,
                        )
                    }
                    if (status.modeLabel.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(16.dp, 10.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(
                                text = status.modeLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(16.dp, 14.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Column {
                            Text(
                                text = status.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = status.summary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }

        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = { ServiceBridge.rebind() },
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Tilt,
                ) {
                    BasicComponent(
                        title = status.title,
                        summary = if (status.detail.isEmpty()) status.summary else "${status.summary}\n${status.detail}",
                        startAction = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                status.title,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        },
                        endActions = {
                            TextButton(
                                text = stringResource(R.string.reconnect),
                                onClick = { ServiceBridge.rebind() },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 设备信息卡（对齐参考项目 InfoCard Miuix 版：图标 + 标题 + 内容，双卡分组）。 */
@Composable
private fun InfoCardMiuix(systemInfo: HomeSystemInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoText(
                    icon = Icons.Filled.Tag,
                    title = stringResource(R.string.home_app_version),
                    content = systemInfo.appVersion,
                )
                InfoText(
                    icon = Icons.Filled.DeveloperBoard,
                    title = stringResource(R.string.home_system_version),
                    content = systemInfo.androidVersion,
                )
                InfoText(
                    icon = Icons.Filled.Smartphone,
                    title = stringResource(R.string.home_device_model),
                    content = systemInfo.deviceModel,
                    bottomPadding = 0.dp,
                )
            }
        }
        if (systemInfo.frameworkVersion.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoText(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.home_framework),
                        content = systemInfo.frameworkVersion,
                        bottomPadding = 0.dp,
                    )
                }
            }
        }
    }
}

/** 信息行：24dp 图标 + 标题/内容两行（对齐参考项目 InfoText）。 */
@Composable
private fun InfoText(
    icon: ImageVector,
    title: String,
    content: String,
    bottomPadding: Dp = 24.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
            tint = colorScheme.onSurface,
        )
        Column {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = content,
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
