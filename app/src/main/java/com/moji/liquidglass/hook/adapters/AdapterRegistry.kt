package com.moji.liquidglass.hook.adapters

import android.app.Activity

/**
 * 目标应用适配器注册表：新增应用适配 = 实现 [AppAdapter] + 在此注册 +
 * 在 LSPosed 勾选作用域。显式列表保证"一个应用的适配不会误跑到别的包里"。
 */
object AdapterRegistry {

    private val adapters: List<AppAdapter> = listOf(AppStoreAdapter, GalleryAdapter, VivoSpaceAdapter, WeiboAdapter, AmapAdapter, XhsAdapter, VivoNotesAdapter, FileManagerAdapter, VivoThemeAdapter, IqooSecureAdapter, VivoHealthAdapter, TaobaoAdapter, AlipayAdapter, MangoTVAdapter)

    val all: List<AppAdapter> get() = adapters

    fun byPackage(packageName: String): AppAdapter? =
        adapters.firstOrNull { it.targetPackage == packageName }

    fun forActivity(activity: Activity): AppAdapter? = byPackage(activity.packageName)

    fun isSupported(packageName: String): Boolean = byPackage(packageName) != null
}
