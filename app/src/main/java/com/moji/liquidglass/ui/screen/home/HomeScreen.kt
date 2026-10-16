package com.moji.liquidglass.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.moji.liquidglass.R
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.theme.LocalUiMode
import com.moji.liquidglass.ui.theme.UiMode

/** LSPosed 服务状态：标题 + 一行说明 + 状态角标 + 可选诊断细节。 */
internal data class BridgeStatus(
    val connected: Boolean,
    val headline: String,
    val summary: String,
    val detail: String,
    val chipLabel: String,
)

@Composable
internal fun rememberBridgeStatus(): BridgeStatus {
    val bound = ServiceBridge.bound
    val lastError = ServiceBridge.lastError
    val pushCount = ServiceBridge.providerPushCount
    val pushError = ServiceBridge.lastPushError

    val section = stringResource(R.string.status_section)
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
                val framework = ServiceBridge.service?.let { "${it.frameworkName} ${it.frameworkVersion}" } ?: ""
                BridgeStatus(
                    connected = true,
                    headline = section,
                    summary = if (framework.isEmpty()) connectedPlain else String.format(connectedFmt, framework),
                    detail = "",
                    chipLabel = ok,
                )
            }
            lastError != null -> BridgeStatus(
                connected = false,
                headline = section,
                summary = String.format(registerFailedFmt, lastError),
                detail = "",
                chipLabel = error,
            )
            else -> BridgeStatus(
                connected = false,
                headline = section,
                summary = waiting,
                detail = when {
                    pushError != null -> String.format(pushErrorFmt, pushError)
                    pushCount > 0 -> String.format(pushCountFmt, pushCount)
                    else -> ""
                },
                chipLabel = error,
            )
        }
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
