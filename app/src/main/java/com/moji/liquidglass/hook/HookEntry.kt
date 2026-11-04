package com.moji.liquidglass.hook

import android.util.Log
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * LSPosed (libxposed API 102, META-INF/xposed/java_init.list) 模块入口。
 * 作用域内已注册适配器的应用（见 AdapterRegistry）注入玻璃底栏。
 */
class HookEntry : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val pkg = param.packageName
        if (!AdapterRegistry.isSupported(pkg)) return
        // 双通道写日志，保证无论框架如何改写 tag 都能被 logcat 过滤到。构建指纹由
        // TargetHook.install 统一输出（dex 路径/大小/mtime + 重复注入检测）。
        log(Log.INFO, TAG, "[$TAG] loaded into $pkg (modern entry)")
        Log.println(Log.INFO, TAG, "loaded into $pkg (modern entry)")
        Thread {
            TargetHook.install(ModernFacade(this), ::readConfig, param.defaultClassLoader, pkg, "modern")
        }.start()
    }

    private fun readConfig(): ModuleConfig = try {
        ModuleConfig.fromJson(
            getRemotePreferences(ModuleConfig.PREF_NAME).getString(ModuleConfig.PREF_KEY, null)
        )
    } catch (_: Throwable) {
        ModuleConfig.DEFAULT
    }
}
