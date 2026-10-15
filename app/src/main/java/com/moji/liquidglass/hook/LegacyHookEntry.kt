package com.moji.liquidglass.hook

import android.util.Log
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 经典 API 入口（assets/xposed_init），兜底老版本 LSPosed。
 * 与 HookEntry 使用同一套注入逻辑。
 */
class LegacyHookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (!AdapterRegistry.isSupported(pkg)) return
        Log.println(Log.INFO, TAG, "loaded into $pkg (legacy entry)")
        XposedBridge.log("[$TAG] loaded into $pkg (legacy entry)")
        Thread { TargetHook.install(LegacyFacade(), ::readConfig, lpparam.classLoader, pkg) }.start()
    }

    private fun newPrefs(): XSharedPreferences? = try {
        XSharedPreferences("com.moji.liquidglass", ModuleConfig.PREF_NAME)
    } catch (_: Throwable) {
        null
    }

    private fun readConfig(): ModuleConfig = try {
        newPrefs()?.reload()
        ModuleConfig.fromJson(newPrefs()?.getString(ModuleConfig.PREF_KEY, null))
    } catch (_: Throwable) {
        ModuleConfig.DEFAULT
    }
}
