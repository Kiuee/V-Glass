@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.StatusTag
import com.moji.liquidglass.ui.component.WarningCard
import com.moji.liquidglass.ui.component.WarningLevel
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedInfoItem
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem

/** 主页 Material 版（对齐参考项目 HomePagerMaterial）：警告条 + 三态状态卡 + 带图标 InfoCard + 说明分组。 */
@Composable
fun HomePagerMaterial(bottomInnerPadding: Dp) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val status = rememberBridgeStatus()
    val systemInfo = rememberHomeSystemInfo()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (status.warning.isNotBlank()) {
                WarningCard(
                    message = status.warning,
                    level = WarningLevel.Error,
                )
            }
            StatusCardMaterial(status)
            InfoCardMaterial(systemInfo)

            // 使用方法：一行一步
            SegmentedColumn(
                title = stringResource(R.string.usage_title),
                content = listOf(
                    { SegmentedNoteItem(text = stringResource(R.string.usage_step_1)) },
                    { SegmentedNoteItem(text = stringResource(R.string.usage_step_2)) },
                    { SegmentedNoteItem(text = stringResource(R.string.usage_step_3)) },
                ),
            )

            // 其他：一行一条
            SegmentedColumn(
                title = stringResource(R.string.other_group),
                content = listOf(
                    { SegmentedNoteItem(text = stringResource(R.string.about_requirement)) },
                    { SegmentedNoteItem(text = stringResource(R.string.about_diagnostics)) },
                ),
            )

            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

/**
 * 模块状态卡三态（对齐参考项目 StatusCard Material 版）：
 * Surface 大圆角容器 + ListItem；已连接 = secondaryContainer + 对勾 + 框架名标签；
 * 未连接 = errorContainer + 警告/禁止图标 + 重连按钮；点击未连接卡片即重连。
 */
@Composable
private fun StatusCardMaterial(status: BridgeStatus) {
    val containerColor = if (status.connected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

    val statusIcon: ImageVector = when {
        status.connected -> Icons.Rounded.CheckCircle
        status.isError -> Icons.Rounded.Block
        else -> Icons.Rounded.Warning
    }

    val trailing: (@Composable () -> Unit)? = if (status.connected) {
        if (status.modeLabel.isNotEmpty()) {
            {
                StatusTag(
                    label = status.modeLabel,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        }
    } else {
        {
            Button(
                onClick = { ServiceBridge.rebind() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.reconnect))
            }
        }
    }

    if (status.connected) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
        ) {
            StatusListItem(status, statusIcon, trailing, contentColor)
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            onClick = { ServiceBridge.rebind() },
        ) {
            StatusListItem(status, statusIcon, trailing, contentColor)
        }
    }
}

@Composable
private fun StatusListItem(
    status: BridgeStatus,
    statusIcon: ImageVector,
    trailing: (@Composable () -> Unit)?,
    contentColor: Color,
) {
    ListItem(
        headlineContent = {
            Text(
                text = status.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        leadingContent = { Icon(statusIcon, contentDescription = status.title) },
        trailingContent = trailing,
        overlineContent = null,
        supportingContent = {
            Column {
                Text(text = status.summary, style = MaterialTheme.typography.bodyMedium)
                if (status.detail.isNotEmpty()) {
                    Text(
                        text = status.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            leadingContentColor = contentColor,
            trailingContentColor = contentColor,
            supportingContentColor = contentColor.copy(alpha = 0.7f),
        ),
    )
}

/** 设备信息卡（对齐参考项目 InfoCard Material 版：图标 + 标题 + 内容，双分组）。 */
@Composable
private fun InfoCardMaterial(systemInfo: HomeSystemInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SegmentedColumn(
            modifier = Modifier.fillMaxWidth(),
            content = listOf(
                {
                    SegmentedInfoItem(
                        icon = Icons.Filled.Tag,
                        title = stringResource(R.string.home_app_version),
                        content = systemInfo.appVersion,
                    )
                },
                {
                    SegmentedInfoItem(
                        icon = Icons.Filled.DeveloperBoard,
                        title = stringResource(R.string.home_system_version),
                        content = systemInfo.androidVersion,
                    )
                },
                {
                    SegmentedInfoItem(
                        icon = Icons.Filled.Smartphone,
                        title = stringResource(R.string.home_device_model),
                        content = systemInfo.deviceModel,
                    )
                },
            ),
        )
        if (systemInfo.frameworkVersion.isNotEmpty()) {
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content = listOf(
                    {
                        SegmentedInfoItem(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.home_framework),
                            content = systemInfo.frameworkVersion,
                        )
                    },
                ),
            )
        }
    }
}
