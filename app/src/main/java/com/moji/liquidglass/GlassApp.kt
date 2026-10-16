package com.moji.liquidglass

import android.app.Application
import com.moji.liquidglass.ui.ServiceBridge
import com.moji.liquidglass.ui.prefs.ManagerPrefs

/** 进程创建即注册 LSPosed 服务监听（binder 由管理器在模块 App 进程创建时经 XposedProvider 送达）。 */
class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceBridge.bind()
        ManagerPrefs.init(this)
    }
}
