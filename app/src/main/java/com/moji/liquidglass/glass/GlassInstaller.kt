package com.moji.liquidglass.glass

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 玻璃底栏装配器（覆盖层模式）。
 *
 * 应用内容视图**原位不动**（触摸路径与原生完全一致——重新挂载进 Compose 会让
 * 全部触摸经过 interop 转发管道，复杂手势体系下 Compose 会向嵌入子树发 CANCEL
 * 导致滑动失灵），ComposeView 仅作为透明覆盖层叠加在内容之上、只占底栏条带高度。
 * 玻璃经 ClippedViewBackdrop 跨分支绘制内容视图（view.draw 不要求同树）采样折射。
 */
object GlassInstaller {

    /** owner 与窗口绑定（见 [ComposeLifecycleOwner] 类注释），同一 Activity 重装时复用。 */
    private val owners = java.util.WeakHashMap<Activity, ComposeLifecycleOwner>()

    /**
     * 应用真实导航栏 inset 读取器（hook 层注入，三级回退：原容器 paddingBottom →
     * rootWindowInsets 的 navigationBars → 由 GlassRoot 回退 Compose insets；-1 = 不可用）。
     */
    @Volatile
    var navInsetProvider: (() -> Int)? = null

    /** 玻璃栏样式（hook 层从配置同步，GlassRoot 帧循环读取应用到布局）。 */
    @Volatile
    var glassStyle: GlassStyle = GlassStyle()

    fun install(activity: Activity, bar: View, tabViews: List<View>, extractor: TabExtractor): GlassSession? {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return null
        val appRoot = content.getChildAt(0) ?: return null
        if (appRoot is ComposeView) return null // 已装配

        val spec = extractor.extract(tabViews) ?: return null
        val owner = owners[activity] ?: ComposeLifecycleOwner().also {
            it.handleOnCreate()
            it.handleOnStart()
            it.handleOnResume()
            owners[activity] = it
        }

        val composeView = ComposeView(activity)
        // 在挂载前把 owner 写进视图树标签（根视图 + ComposeView 双处），
        // 否则 Compose 解析 window recomposer 时抛 ViewTreeLifecycleOwner not found 崩溃宿主
        runCatching {
            activity.window?.decorView?.let { decor ->
                decor.setViewTreeLifecycleOwner(owner)
                decor.setViewTreeViewModelStoreOwner(owner)
                decor.setViewTreeSavedStateRegistryOwner(owner)
            }
            composeView.setViewTreeLifecycleOwner(owner)
            composeView.setViewTreeViewModelStoreOwner(owner)
            composeView.setViewTreeSavedStateRegistryOwner(owner)
        }

        val session = GlassSession(
            appRoot = appRoot,
            tabContainer = bar,
            tabViews = tabViews,
            composeView = composeView,
            extractor = extractor,
        )

        composeView.setContent {
            GlassRoot(appRoot = appRoot, spec = spec, session = session)
        }

        // 覆盖层：内容视图原位不动，ComposeView 叠加在其上、只占底栏条带高度。
        // 关闭裁切：玻璃辉光/液泡放大可溢出条带边界，不再出现裁切边线
        composeView.clipChildren = false
        composeView.clipToPadding = false
        content.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        // 原生底栏"可见但隐形"：GONE 会让应用侧 isShown 类守卫不通过，
        // 点击转发的回顶+刷新分支失效（切页不受影响）
        session.suppressNatively()
        return session
    }

    /** Activity 生命周期 → owner（由 hook 层调用，owner 缺失时静默跳过）。 */
    fun dispatchResume(activity: Activity) {
        owners[activity]?.handleOnResume()
    }

    fun dispatchPause(activity: Activity) {
        owners[activity]?.handleOnPause()
    }
}
