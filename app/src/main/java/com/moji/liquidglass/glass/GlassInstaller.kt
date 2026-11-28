package com.moji.liquidglass.glass

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
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

    fun install(
        activity: Activity,
        bar: View,
        tabViews: List<View>,
        extractor: TabExtractor,
    ): GlassSession? {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return null
        // 采样源 appRoot = **包含底栏的 content 直接子视图**：merge 布局应用的 content 有
        // 多个子视图（原子笔记 = GONE 引导层 + 真实内容 + 闪屏），固定取 child(0) 会锚到
        // 空视图，采样条带恒空 = 玻璃"透明"（§8.7 真机根因）。沿 bar 的 parent 链匹配
        // content 直接子项；单子视图应用结果与 child(0) 相同，零回归。
        var node = bar.parent
        while (node is View && node.parent !== content) node = node.parent
        val appRoot = (node as? View)?.takeIf { it.parent === content }
            ?: content.getChildAt(0)
            ?: return null
        if (appRoot is ComposeView) return null // 已装配

        val spec = extractor.extract(tabViews) ?: return null
        val owner = owners[activity] ?: ComposeLifecycleOwner().also {
            it.handleOnCreate()
            it.handleOnStart()
            it.handleOnResume()
            owners[activity] = it
        }

        val composeView = ComposeView(activity)
        // 玻璃覆盖层对无障碍整树隐身：Compose 语义树发的 a11y 事件会触发系统侧对整窗
        // 节点树的 prefetch（沿祖先兄弟遍历），命中商店自带 TabView 有缺陷的无障碍节点
        // （解析自身缺失的 string 资源）→ 应用主线程 Resources$NotFoundException 闪退。
        // NO_HIDE_DESCENDANTS 让框架完全跳过本覆盖层，prefetch 不再被触发。
        composeView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        val session = GlassSession(
            appRoot = appRoot,
            tabContainer = bar,
            tabViews = tabViews,
            composeView = composeView,
            extractor = extractor,
        )

        // 在挂载前把 owner 写进视图树标签（根视图 + ComposeView 双处），
        // 否则 Compose 解析 window recomposer 时抛 ViewTreeLifecycleOwner not found 崩溃宿主
        runCatching {
            activity.window?.decorView?.let { decor ->
                // 记住装配前的原 owner，restore 时换回（避免模块 owner 永久覆盖应用自身的）
                session.attachDecorOwners(
                    decor,
                    decor.findViewTreeLifecycleOwner(),
                    decor.findViewTreeViewModelStoreOwner(),
                    decor.findViewTreeSavedStateRegistryOwner(),
                )
                decor.setViewTreeLifecycleOwner(owner)
                decor.setViewTreeViewModelStoreOwner(owner)
                decor.setViewTreeSavedStateRegistryOwner(owner)
            }
            composeView.setViewTreeLifecycleOwner(owner)
            composeView.setViewTreeViewModelStoreOwner(owner)
            composeView.setViewTreeSavedStateRegistryOwner(owner)
        }

        composeView.setContent {
            GlassRoot(appRoot = appRoot, spec = spec, session = session)
        }

        // 覆盖层：内容视图原位不动，ComposeView 叠加在其上、只占底栏条带高度。
        // 关闭裁切：玻璃辉光/液泡放大可溢出条带边界，不再出现裁切边线。
        // **overlay 必须挂在 content 层（appRoot 之外）**：backdrop 会跨分支 view.draw
        // 采样 appRoot——overlay 一旦进入 appRoot 子树，采样路径重入绘制
        // （RenderNode "Recording currently in progress" 主线程闪退，vivo 官网踩坑）。
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
