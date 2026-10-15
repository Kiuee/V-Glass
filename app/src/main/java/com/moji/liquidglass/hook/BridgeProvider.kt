package com.moji.liquidglass.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.moji.liquidglass.ui.ServiceBridge
import io.github.libxposed.service.RemotePreferences
import io.github.libxposed.service.XposedServiceHelper

/**
 * LSPosed 服务 binder 接收入口（authority = ${applicationId}.XposedService）。
 *
 * 库自带的 io.github.libxposed.service.XposedProvider 是 final 类无法继承，这里按其
 * 官方实现复刻一份并插入可观测性：每次收到管理器推送时计数并打日志，设置页据此
 * 区分"守护进程从未推送"与"已推送但未绑定"，不再依赖 logcat 过滤。
 * 与库实现的差异：SEND_BINDER 方法名走字面量（"SendBinder"，AID 常量不对编译期公开）、
 * shouldNotifyCleared 经反射置位、onBinderReceived 为包私有经反射分发
 * （keep 规则已整体保留 io.github.libxposed.**）。
 */
class BridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val targetSdk = context?.applicationInfo?.targetSdkVersion ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && targetSdk >= Build.VERSION_CODES.R) {
            runCatching {
                RemotePreferences::class.java
                    .getDeclaredField("shouldNotifyCleared")
                    .apply { isAccessible = true }
                    .set(null, true)
            }
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "SendBinder" && extras != null) {
            val binder = extras.getBinder("binder")
            if (binder != null) {
                ServiceBridge.onProviderPush()
                runCatching {
                    XposedServiceHelper::class.java.getDeclaredMethod(
                        "onBinderReceived",
                        IBinder::class.java
                    ).apply { isAccessible = true }.invoke(null, binder)
                }.onFailure {
                    Log.w("BridgeProvider", "dispatch onBinderReceived failed", it)
                    ServiceBridge.onProviderDispatchFailed(it)
                }
            }
            return Bundle()
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
