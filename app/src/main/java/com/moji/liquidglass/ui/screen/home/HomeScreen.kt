package com.moji.liquidglass.ui.screen.home

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

/** LSPosed 服务状态（对齐参考项目 HomeUiState 的三态结构：运行中 / 等待 / 异常）。 */
@Immutable
internal data class BridgeStatus(
    val connected: Boolean,
    /** 状态标题：运行中 / 未连接。 */
    val title: String,
    /** 一行说明：已连接（框架）/ 等待推送 / 注册失败原因。 */
    val summary: String,
    /** 状态卡上方的错误警告条文案（仅推送分发失败时非空）。 */
    val warning: String,
    /** 信息性诊断细节：推送计数（可空）。 */
    val detail: String,
    /** 运行模式标签（对齐 LKM/GKI 位）：已连接时为框架名，其余为空。 */
    val modeLabel: String,
    /** 图标语义：已连接 = 对勾；等待 = 警告；异常 = 禁止。 */
    val isError: Boolean,
    val isWaiting: Boolean,
)

@Composable
internal fun rememberBridgeStatus(): BridgeStatus {
    val bound = ServiceBridge.bound
    val lastError = ServiceBridge.lastError
    val pushCount = ServiceBridge.providerPushCount
    val pushError = ServiceBridge.lastPushError

    val ok = stringResource(R.string.status_ok)
    val error = stringResource(R.string.status_error)
    val connectedPlain = stringResource(R.string.status_connected)
    val connectedFmt = stringResource(R.string.status_connected_fmt)
    val registerFailedFmt = stringResource(R.string.status_register_failed)
    val waiting = stringResource(R.string.status_waiting)
    val pushCountFmt = stringResource(R.string.status_push_count_fmt)
    val pushErrorFmt = stringResource(R.string.status_push_error_fmt)

    return remember(bound, lastError, pushCount, pushError) {
        when {
            bound -> {
                val service = ServiceBridge.service
                val framework = service?.let { "${it.frameworkName} ${it.frameworkVersion}" } ?: ""
                BridgeStatus(
                    connected = true,
                    title = ok,
                    summary = if (framework.isEmpty()) connectedPlain else String.format(connectedFmt, framework),
                    warning = "",
                    detail = "",
                    // 运行模式位（对齐 KSU 的 LKM/GKI 位置）：显示连接的 LSPosed 服务 API 版本
                    modeLabel = service?.let { s ->
                        if (s.apiVersion >= 100) "API ${s.apiVersion}" else "legacy"
                    } ?: "",
                    isError = false,
                    isWaiting = false,
                )
            }

            lastError != null -> BridgeStatus(
                connected = false,
                title = error,
                summary = String.format(registerFailedFmt, lastError),
                warning = "",
                detail = "",
                modeLabel = "",
                isError = true,
                isWaiting = false,
            )

            else -> BridgeStatus(
                connected = false,
                title = error,
                summary = waiting,
                warning = pushError?.let { String.format(pushErrorFmt, it) } ?: "",
                detail = if (pushCount > 0) String.format(pushCountFmt, pushCount) else "",
                modeLabel = "",
                isError = false,
                isWaiting = true,
            )
        }
    }
}

/** 设备与环境信息（对齐参考项目 SystemInfo：应用版本 / 系统版本 / 设备型号 / 框架版本）。 */
@Immutable
internal data class HomeSystemInfo(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val frameworkVersion: String,
)

@Composable
internal fun rememberHomeSystemInfo(): HomeSystemInfo {
    val context = LocalContext.current
    val service = ServiceBridge.service
    return remember(context, service) {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        HomeSystemInfo(
            appVersion = appVersion,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            frameworkVersion = service?.let { "${it.frameworkName} ${it.frameworkVersion}" } ?: "",
        )
    }
}

/** 主页分发器。 */
@Composable
fun HomePager(bottomInnerPadding: Dp) {
    when (LocalUiMode.current) {
        UiMode.Material -> HomePagerMaterial(bottomInnerPadding)
        UiMode.Miuix -> HomePagerMiuix(bottomInnerPadding)
    }
}
