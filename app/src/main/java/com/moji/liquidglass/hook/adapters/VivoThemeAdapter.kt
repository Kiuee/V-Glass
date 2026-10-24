package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * vivo 主题（com.bbk.theme，桌面名"主题"）适配器。
 *
 * - 主页链路：主 Tab Activity = [TAB_ACTIVITY]（launcher），布局 `main_layout`：
 *   `root_layout`（RelativeLayout）> `container` > `fragment`（FrameLayout，match_parent
 *   全屏）+ ViewStub `bottom_nav_layout_stub` 展开为 [BAR_CLASS]（`bottom_nav_layout`，
 *   贴底 overlay 在 fragment 之上，内容天然延伸到底，无内容延伸诉求）。
 *   底栏由 Activity `q()` 惰性展开（onCreate 流程，晚于装配探测时由 TargetHook 重试兜住）。
 * - 底栏结构：[BAR_CLASS]（自研封装壳，非混淆，内含 originui
 *   [INNER_BAR_CLASS]（id `bottom_navigation_view`）+ `vip_tip_text` TextView（默认 GONE，
 *   VIP 专区运营 tip））。压制壳 = 连 tip 一起隐藏，玻璃 overlay 挂 content 层不受影响。
 * - 每 tab 视图：内部 menuLayout（com.originui.widget.navigation.VMenuViewLayout）的
 *   直接子项 = `com.originui.widget.navigation.navigation.VNavMenuItem`（与相册/文件管理
 *   同库同构；本应用 originui 非混淆：setChecked 完整存在、末尾 setSelected(z11)，选中
 *   读数 = 标准 isSelected）。tab 数动态：3 或 4（推荐/分类/[VIP 专区]/本地，VIP tab 按
 *   设备形态 + 远程运营配置插入）。
 * - 图标：LottieDrawable（动态符号 tint"Fill 1"路径，选中 = *_IN json / 非选中 = *_OUT
 *   json）或静态 symbol drawable（低版本系统动态字体不可用/节日皮肤路径），`setIcon`
 *   挂到图标 ImageView（`vivo_navigation_bar_item_icon_view`），元素级直绘天然跟随；
 *   角标 VBadgeDrawable 挂图标 ImageView 的 ViewOverlay（引擎 overlayContent 通道承接）。
 * - 选中机制（文件管理 discolor 同族，换图与翻转双通道分离）：`setChecked(boolean)` 纯
 *   视觉翻转（标签缩放/显隐，末尾 setSelected）；换图由应用选择路径
 *   `VBottomNavigationView.setItemSelected(int)`/`f(item)` 驱动——图标对存底栏 `B` 映射
 *   （VNavMenuItem → uj.a：a/b = 静态 normal/selected、c/d = Lottie normal/selected、
 *   e/f = 第二组 normal/selected，checked 取 b→d(z(1.0f))→f、unchecked 取 e→a→c(l())→
 *   d(z(0.0f))）。拖动悬停预览只翻 setChecked 不换图（残留旧选中图标/悬停图标停留非
 *   选中形态），故注入 [session.previewIconSwapper]：每次换图前实时反射读 `B` 映射
 *   （系统色更新/节日皮肤/基础服务模式会整体重放映射，缓存会过期），按底栏自身
 *   `f()`/`setItemSelected` 同语义换图（不调用带 e() onItemSelected 切页回调的路径）。
 * - 可见性门控：未发现确证的底栏隐藏路径（主页为单 Activity 多 Fragment，详情页独立
 *   Activity；promcard/mask 全屏覆盖层压在 bar 之上时 bar 照常绘制，玻璃同层被覆盖，
 *   无需门控）——按 §0.4 不预设，真机反馈确认后再补。mirrorVisibleGate 注入恒返回
 *   true，仅借帧循环逐帧重申压制（应用搜索链路会把 alpha 写回 1，§10.7 压制重申条）。
 * - 采样冻结：VIP 专区页（Theme 内唯一 WebView tab，H5 体系）对引擎外部 view.draw 重绘
 *   不相容（Chromium 间歇输出空白帧 = 整页闪白），经 `backdropFreezeGate`（§1.2）在
 *   WebView 页可见时冻结采样——进入时一次性录制、冻结期间纯回放，离开恢复实时采样
 *   （§10.7 采样冻结条）；非冻结页保持引擎直绘路径（§10.7 采样去重回放条）。
 * - 早期压制：壳由 Activity q() 惰性 inflate（Choreographer 首帧回调），探测重试 500ms
 *   节奏下冷启动原栏可见约半秒——hook `onFinishInflate` 在 inflate 完成即压制（8s 兜底
 *   还原，§10.7 早期压制条）。
 */
object VivoThemeAdapter : AppAdapter {

    override val targetPackage: String = "com.bbk.theme"
    override val displayName: String = "vivo 主题"
    override val tabActivityClassName: String? = "com.bbk.theme.Theme"
    override val baselineVersions: List<String> = listOf("23.8.5.1")

    // 底栏容器（自研封装壳，非混淆）与其在 main_layout 中的资源 id（类名失配兜底）
    private const val BAR_CLASS = "com.bbk.theme.widget.VBottomNavLayout"
    private const val BAR_ID_NAME = "bottom_nav_layout"
    // 壳内 originui 底栏（非混淆库类）：图标对 B 映射的持有者
    private const val INNER_BAR_CLASS = "com.originui.widget.navigation.VBottomNavigationView"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/官网/文件管理同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"

    /** 早期压制兜底还原时限（未被会话认领时还原 alpha，防永久隐形）。 */
    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐相册/文件管理）
        elementIds = mapOf(
            "icon" to ICON_ID_NAME,
            "labels" to LABELS_ID_NAME,
        ),
        // 选中外观翻转方法 = 默认 setChecked（本应用 originui 非混淆：纯视觉翻转、
        // 末尾 setSelected，无切页副作用），无需声明 itemCheckedSetterMethodName。
        // 标签色 = setItemTextColor 真选择器（state_selected 差异色），取色即得
        // 强调色/内容色，无需 labelAccentFallbackToBubble。
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun findTabBar(content: ViewGroup): View? {
        var found: View? = null
        fun walk(v: View?) {
            if (v == null || found != null) return
            if (v.javaClass.name == BAR_CLASS) {
                found = v
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(content)
        if (found != null) return found
        // 结构兜底：类名失配时按 id 名找壳
        val id = content.resources.getIdentifier(BAR_ID_NAME, "id", content.context.packageName)
        if (id != 0) {
            fun walkId(v: View?): View? {
                if (v == null) return null
                if (v.id == id) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) {
                    walkId(v.getChildAt(i))?.let { return it }
                }
                return null
            }
            return walkId(content)
        }
        return null
    }

    override fun tabViewsOf(bar: View): List<View> {
        // 首选：menuLayout 的直接子项（顺序 = tab 顺序，VNavMenuItem）
        val menuLayouts = ArrayList<ViewGroup>(1)
        fun walk(v: View?) {
            if (v == null) return
            if (v is ViewGroup && v.javaClass.simpleName == MENU_LAYOUT_SIMPLE_NAME) {
                menuLayouts.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(bar)
        if (menuLayouts.isNotEmpty()) {
            val items = ArrayList<View>()
            for (layout in menuLayouts) {
                for (i in 0 until layout.childCount) items.add(layout.getChildAt(i))
            }
            if (items.size >= 2) return items
        }
        // 结构兜底：找同时含 icon+labels 元素的最深视图（menuLayout 改名后的稳定判据）
        val iconId = idByName(bar, ICON_ID_NAME)
        val labelsId = idByName(bar, LABELS_ID_NAME)
        if (iconId == 0 || labelsId == 0) return emptyList()
        val candidates = ArrayList<View>()
        fun walkItems(v: View) {
            var hasIcon = false
            var hasLabels = false
            fun check(c: View) {
                if (c.id == iconId) hasIcon = true
                if (c.id == labelsId) hasLabels = true
            }
            fun scan(c: View?) {
                if (c == null) return
                check(c)
                if (c is ViewGroup) for (i in 0 until c.childCount) scan(c.getChildAt(i))
            }
            scan(v)
            if (hasIcon && hasLabels) {
                candidates.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walkItems(v.getChildAt(i))
        }
        walkItems(bar)
        // 候选里含其它候选的是祖先容器，只保留不被任何其它候选包含的最深节点
        return candidates.filter { c -> candidates.none { it !== c && it.isAncestorOf(c) } }
    }

    private fun View.isAncestorOf(other: View): Boolean {
        var p = other.parent
        while (p is View) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    private fun idByName(v: View, name: String): Int =
        v.resources.getIdentifier(name, "id", v.context.packageName)

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
        // 搜索返回路径应用会把底栏 alpha 写回 1（应用搜索链路写回 alpha=1，见 §10.7 压制重申条）：恢复期第一帧前
        // 同步重申压制；帧循环恢复后由 mirrorVisibleGate 逐帧接管
        containerRef?.get()?.takeIf { it.isAttachedToWindow }?.let { if (it.alpha != 0f) it.alpha = 0f }
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 预览图标换图（双 drawable 图标对）：引擎 setTabChecked 翻转后调用——悬停 tab
        // 换选中态 drawable、被拖离的原选中换非选中态，与底栏 setItemSelected/f() 换图
        // 同语义。图标对不缓存：每次换图前实时反射读 B 映射（系统色更新/节日皮肤/基础
        // 服务模式都会整体重放映射）。
        session.previewIconSwapper = { index, checked ->
            swapPreviewIcon(session, index, checked)
        }
        // WebView 页采样冻结（VIP 专区页闪白修复，§10.7 采样冻结条）：进入冻结时一次性录制
        // 条带、冻结期间纯回放（零外部 WebView draw），离开恢复实时采样。非冻结页保持
        // 引擎直绘路径——采样去重（RenderNode 回放）跨 Surface 重建的显示列表会被 HWUI
        // 丢弃，表现为"玻璃轮廓在、内容空"（§10.7 采样去重回放条）。
        session.backdropFreezeGate = { visibleWebViewOrNull(session) != null }
        // 压制重申（搜索返回原栏重现修复，§10.7 压制重申条）：应用在搜索页建立后 300ms 把
        // 底栏 alpha 写回 1（h8/c JumpSearchPageEventMessage 路径），Theme 暂停期间帧循
        // 环停止、无纠错——帧循环每帧经 mirrorVisibleGate 重申（仅失配时写入，防每帧
        // setAlpha 触发失效重绘），恢复后第一帧压回 0。恒返回 true（本应用无隐藏路径，
        // barWanted 不变）。
        containerRef = WeakReference(session.tabContainer)
        session.mirrorVisibleGate = {
            containerRef?.get()?.let { bar -> if (bar.alpha != 0f) bar.alpha = 0f }
            true
        }
        // 早期压制认领（冷启动闪现修复，见 installAppHooks）：装配即压制，原栏从 inflate
        // 起全程不可见；此后压制/还原由会话接管（restore 恢复 alpha=1）
        earlySuppressed.remove(session.tabContainer)
    }

    @Volatile private var containerRef: WeakReference<View>? = null
    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        runCatching {
            // initialize=false：VBottomNavLayout 的 <clinit> 调 ThemeApp.getInstance()
            // .getString()，进程加载期 Application 未建，连锁初始化会闪退宿主（微博
            // §5.7 全局纪律）；hook 注册不需要类初始化。
            val barClass = Class.forName(BAR_CLASS, false, loader)
            val onFinishInflate = barClass.getDeclaredMethod("onFinishInflate")
            facade.around(onFinishInflate, null, { call ->
                (call.thisObj as? View)?.let { suppressEarly(it, facade) }
                null
            })
            facade.log(android.util.Log.INFO, "hooked VBottomNavLayout.onFinishInflate: early suppress")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook VBottomNavLayout.onFinishInflate failed (app updated?)", it)
        }
    }

    /** 底栏早期压制（冷启动闪现修复，§10.7 早期压制条）：壳由 Activity `q()` 在 Choreographer
     *  首帧回调才 inflate，探测首轮（onResume 后）落空、重试间隔 500ms——inflate 到装
     *  配之间原栏可见约半秒。inflate 完成即压制 alpha=0；会话装配时认领（此后由会话压
     *  制/restore 接管）；[EARLY_SUPPRESS_FALLBACK_MS] 未认领（探测放弃/模块中途关闭）
     *  则兜底还原 alpha=1，避免永久隐形。 */
    private fun suppressEarly(view: View, facade: HookFacade) {
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        facade.log(android.util.Log.INFO, "theme early-suppress bar (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "theme early-suppress fallback restore (session never claimed)",
                )
            }
        }, EARLY_SUPPRESS_FALLBACK_MS)
    }

    /** appRoot 子树中 attached + isShown 的 WebView（VIP 页等 H5 页面的可见判定）。
     *  帧循环 draw 阶段每 tick 调用：缓存 WeakReference，缓存失效才全树遍历（early-exit，
     *  开销相对引擎既有的每帧 3 次全树 draw 可忽略）。 */
    private fun visibleWebViewOrNull(session: GlassSession): View? {
        webViewRef?.get()?.let { if (it.isAttachedToWindow && it.isShown) return it }
        webViewRef = null
        var found: View? = null
        fun walk(v: View?) {
            if (v == null || found != null) return
            if (v is android.webkit.WebView) {
                found = v
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(session.appRoot)
        found?.let { webViewRef = WeakReference(it) }
        return found
    }

    @Volatile private var webViewRef: WeakReference<View>? = null

    /** 预览换图：从底栏 `B` 映射取该条目的图标对（uj.a），checked → 选中态、否则非
     *  选中态，反射 `VNavMenuItem.setIcon(Drawable)` 换图。任何反射环节失败静默跳过
     *  （表现 = 预览期间图标停留原 drawable，无崩溃）。 */
    private fun swapPreviewIcon(session: GlassSession, index: Int, checked: Boolean) {
        val item = session.tabViews.getOrNull(index) ?: return
        val pair = iconPairOf(session.tabContainer, item) ?: return
        val icon = (if (checked) pair.selected() else pair.unselected()) ?: return
        runCatching {
            item.javaClass
                .getMethod("setIcon", Drawable::class.java)
                .apply { isAccessible = true }
                .invoke(item, icon)
        }
    }

    /** 反射读底栏 `B` 映射（VBottomNavigationView → VNavMenuItem → uj.a）。
     *  真实字段名取证：jadx `renamed from` 注释（uj.a 字段 a~f 因与包名冲突被改名）；
     *  VBottomNavigationView.B 未冲突即真名。 */
    private fun iconPairOf(container: View, item: View): IconPair? {
        // B 映射在壳内 originui 底栏上（VBottomNavigationView）
        var inner: View? = null
        fun walk(v: View?) {
            if (v == null || inner != null) return
            if (v.javaClass.name == INNER_BAR_CLASS) {
                inner = v
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(container)
        val bar = inner ?: return null
        return runCatching {
            val map = bar.javaClass.getField("B").get(bar) as? Map<*, *> ?: return null
            val pair = map[item] ?: return null
            IconPair(
                normal = drawable(pair, "a") ?: drawable(pair, "e"),
                selected = drawable(pair, "b") ?: drawable(pair, "f"),
                normalLottie = drawable(pair, "c"),
                selectedLottie = drawable(pair, "d"),
            )
        }.getOrNull()
    }

    private fun drawable(holder: Any, field: String): Drawable? =
        runCatching {
            holder.javaClass.getField(field).get(holder) as? Drawable
        }.getOrNull()

    /** uj.a 图标对的换图取值（与底栏 `setItemSelected`/`f()` 同语义）：
     *  checked = b（静态选中）→ d（Lottie 选中，setProgress(1.0f) 跳末帧）→ f；
     *  unchecked = e → a（静态非选中）→ c（Lottie 非选中，playAnimation）→
     *  d(setProgress(0.0f) 兜底)。Lottie 方法真名（本 APK 混淆版）：
     *  z(float) = setProgress、l() = playAnimation（jadx LottieDrawable 实证）；
     *  进度驱动失败时仍换图（帧位停留旧进度，不崩溃）。 */
    private class IconPair(
        val normal: Drawable?,
        val selected: Drawable?,
        val normalLottie: Drawable?,
        val selectedLottie: Drawable?,
    ) {
        private fun driveProgress(lottie: Drawable, progress: Float) {
            runCatching {
                lottie.javaClass
                    .getMethod("z", Float::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(lottie, progress)
            }
        }

        private fun play(lottie: Drawable) {
            runCatching {
                lottie.javaClass
                    .getMethod("l")
                    .apply { isAccessible = true }
                    .invoke(lottie)
            }
        }

        fun selected(): Drawable? = selected
            ?: selectedLottie?.also { driveProgress(it, 1.0f) }
        fun unselected(): Drawable? = normal
            ?: normalLottie?.also { play(it) }
            ?: selectedLottie?.also { driveProgress(it, 0.0f) }
    }
}
