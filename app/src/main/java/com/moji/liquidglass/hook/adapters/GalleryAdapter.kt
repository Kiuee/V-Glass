package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade

/**
 * vivo 相册（com.vivo.gallery）适配器。
 *
 * - 底栏：`com.android.gallery3d.vivo.main.base.GalleryBottomBar`
 *   （extends com.originui.widget.navigation.VBottomNavigationView），类名非混淆。
 * - 每 tab 视图：内部 menuLayout 的 `com.originui.widget.navigation.VNavMenuItem`
 *   （FrameLayout，含 Lottie/静态图标 + 双 VMenuTextView 标签 + 角标），选中态 = 标准
 *   View.isSelected（setChecked → setSelected）→ 选中同步/点击转发与商店完全同构。
 * - 图标镜像：WHOLE_TAB 整视图直绘（图标/标签/角标一体，应用更新免疫；无刷新变形场景）。
 * - 内容延伸：暂未发现列表底部留白问题，Noop（反编译确认需要时再补专属策略）。
 */
object GalleryAdapter : AppAdapter {

    override val targetPackage: String = "com.vivo.gallery"
    override val displayName: String = "vivo 相册"
    override val tabActivityClassName: String? = "com.android.gallery3d.vivo.GalleryTabActivity"

    private const val BAR_CLASS = "com.android.gallery3d.vivo.main.base.GalleryBottomBar"
    private const val ITEM_CLASS = "com.originui.widget.navigation.VNavMenuItem"

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
    )

    override fun findTabBar(content: ViewGroup): View? {
        val found = ArrayList<View>(1)
        fun walk(v: View?) {
            if (v == null || found.isNotEmpty()) return
            if (v.javaClass.name == BAR_CLASS) {
                found.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(content)
        return found.firstOrNull()
    }

    override fun tabViewsOf(bar: View): List<View> {
        val items = ArrayList<View>()
        fun walk(v: View?) {
            if (v == null) return
            if (v.javaClass.name == ITEM_CLASS) {
                items.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(bar)
        return items
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }
}
