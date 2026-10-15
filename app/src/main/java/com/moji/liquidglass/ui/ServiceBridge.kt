package com.moji.liquidglass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.hook.TAG
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** App 侧与 LSPosed 服务的桥梁：绑定状态、远程配置写入（模式取自 vivoColoricon）。 */
object ServiceBridge {

    // 状态用 Compose 快照状态承载：组合期读取自动订阅变化，binder 到达即刷新 UI。
    var service: XposedService? by mutableStateOf(null)
        private set

    var bound: Boolean by mutableStateOf(false)
        private set

    /** 注册阶段异常信息（null = 注册正常，仅未收到推送）。 */
    var lastError: String? by mutableStateOf(null)
        private set

    /** BridgeProvider 收到管理器 binder 推送的次数（同一进程生命周期内累计）。 */
    var providerPushCount: Int by mutableStateOf(0)
        private set

    /** 最近一次推送的异常（null = 分发正常）。 */
    var lastPushError: String? by mutableStateOf(null)
        private set

    @Volatile
    private var listenerRegistered = false

    private fun log(msg: String) {
        android.util.Log.println(android.util.Log.INFO, TAG, "[ServiceBridge] $msg")
    }

    private fun logErr(msg: String, t: Throwable) {
        android.util.Log.println(android.util.Log.WARN, TAG, "[ServiceBridge] $msg: $t")
    }

    /** 幂等：可在 Application 与 Activity 各自调用。 */
    fun bind() {
        if (listenerRegistered) return
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(s: XposedService) {
                    service = s
                    bound = true
                    lastError = null
                    log(
                        "service bound: framework=${s.frameworkName} ${s.frameworkVersion}" +
                            " (api=${s.apiVersion})"
                    )
                }

                override fun onServiceDied(s: XposedService) {
                    service = null
                    bound = false
                    log("service died")
                }
            })
            listenerRegistered = true
            lastError = null
            log("listener registered, waiting for binder push from LSPosed manager")
        } catch (t: Throwable) {
            listenerRegistered = false
            lastError = t.toString()
            logErr("registerListener failed", t)
        }
    }

    /** 重新注册监听（管理器未推送时的自助恢复，配合强停本应用/重启手机使用）。 */
    fun rebind() {
        listenerRegistered = false
        bound = false
        service = null
        bind()
    }

    /** BridgeProvider 收到管理器推送时回调（provider 与本桥同进程）。 */
    fun onProviderPush() {
        providerPushCount++
        log("provider received binder push #$providerPushCount")
    }

    fun onProviderDispatchFailed(t: Throwable) {
        lastPushError = t.toString()
        logErr("provider dispatch failed", t)
    }

    /** 读取当前远程配置（无服务时返回默认值）。 */
    fun readConfig(): ModuleConfig = try {
        ModuleConfig.fromJson(
            service?.getRemotePreferences(ModuleConfig.PREF_NAME)
                ?.getString(ModuleConfig.PREF_KEY, null)
        )
    } catch (_: Throwable) {
        ModuleConfig.DEFAULT
    }

    /** 把配置写入 LSPosed 远程配置（宿主侧经 getRemotePreferences 读取）。需在后台线程调用。 */
    fun putConfig(config: ModuleConfig): Boolean = try {
        val s = service ?: return false
        s.getRemotePreferences(ModuleConfig.PREF_NAME)
            .edit()
            .putString(ModuleConfig.PREF_KEY, config.toJson())
            .commit()
    } catch (_: Throwable) {
        false
    }
}
