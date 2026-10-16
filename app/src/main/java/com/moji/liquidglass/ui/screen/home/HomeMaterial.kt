@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.moji.liquidglass.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.component.material.SegmentedColumn
import com.moji.liquidglass.ui.component.material.SegmentedNoteItem
import com.moji.liquidglass.ui.component.material.TonalCard

/** 主页 Material 版（对齐参考项目 HomePagerMaterial）：状态卡 + 版本 InfoCard + 单行说明条目。 */
@Composable
fun HomePagerMaterial(bottomInnerPadding: Dp) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCardMaterial(status)

            // 版本信息卡（参考 InfoCard / InfoCardItem）
            TonalCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
                ) {
                    Text(stringResource(R.string.home_app_version), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

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

/** 模块状态卡（参考 PermissionCard）：状态配色 + 右侧 AssistChip。 */
@Composable
private fun StatusCardMaterial(status: BridgeStatus) {
    val contentColor = if (status.connected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.connected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = status.summary, style = MaterialTheme.typography.bodySmall)
                    if (status.detail.isNotEmpty()) {
                        Text(text = status.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                AssistChip(
                    onClick = { },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = contentColor,
                        leadingIconContentColor = contentColor,
                    ),
                    label = { Text(status.chipLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (status.connected) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                        )
                    },
                )
            }
            if (!status.connected) {
                TextButton(
                    onClick = { ServiceBridge.rebind() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.reconnect))
                }
            }
        }
    }
}
