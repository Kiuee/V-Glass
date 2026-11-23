package com.moji.liquidglass

import android.app.Application
import com.itos.coui.material3.CouiMaterial3Style
import com.itos.coui.theme.Coui
import com.moji.liquidglass.hook.adapters.AdaptedVersionRegistry
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.prefs.AdaptedVersionsStore
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 进程创建即注册 LSPosed 服务监听（binder 由管理器在模块 App 进程创建时经 XposedProvider 送达）。 */
class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // COUI 默认外观策略：管理器 UI 跟随宿主 MaterialTheme（material-kolor 动态色板），
        // 必须在创建任何 Compose UI 之前且只调用一次。
        Coui.initialize(defaultStyle = CouiMaterial3Style())
        ServiceBridge.bind()
        ManagerPrefs.init(this)
        // 免适配版本表：载入 assets 内置表 + 回放远程覆盖表缓存（仅管理器 UI 进程，hook 侧不联网）
        AdaptedVersionRegistry.init(this)
        AdaptedVersionsStore.init(this)
    }
}
