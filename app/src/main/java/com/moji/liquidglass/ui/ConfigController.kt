package com.moji.liquidglass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.liquidglass.config.ModuleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 模块功能配置的 UI 侧控制器：本地即时生效 + 后台写 LSPosed 远程配置。
 * 服务绑定状态变化时自动重读（首次绑定前读到的是默认值）。
 */
class ConfigController private constructor(
    private val scope: CoroutineScope,
) {
    var config: ModuleConfig by mutableStateOf(ServiceBridge.readConfig())
        private set

    /** 乐观更新本地状态，再异步落远程配置。 */
    fun commit(next: ModuleConfig) {
        config = next
        scope.launch { ServiceBridge.putConfig(next) }
    }

    fun refresh() {
        config = ServiceBridge.readConfig()
    }

    companion object {
        private val appScope = CoroutineScope(Dispatchers.IO)

        @Composable
        fun remember(): ConfigController {
            val controller = androidx.compose.runtime.remember { ConfigController(appScope) }
            DisposableEffect(ServiceBridge.bound) {
                controller.refresh()
                onDispose { }
            }
            return controller
        }
    }
}
