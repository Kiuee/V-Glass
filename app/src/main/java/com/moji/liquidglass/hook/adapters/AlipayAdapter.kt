package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassBarState
import com.moji.liquidglass.glass.GlassInstaller
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * 支付宝（com.eg.android.AlipayGphone，版本 12.12.16.7200 取证）适配器。
 *
 * - 主宿主链路：launcher = [TAB_ACTIVITY]（quinox LauncherActivity）。主控制器 =
 *   `com.alipay.android.launcher.TabLauncherFragment`（onCreateView inflate
 *   `launcher_tab`），支付宝全部二级页面为独立 micro-app Activity（无底栏），
 *   主底栏仅存在于本宿主。
 * - 底栏结构（launcher_tab 布局，0x45 合并 bundle 包 res-69）：平台 `TabHost`
 *   （`android.R.id.tabhost`，clipChildren=false）下两个直接子项——`android.R.id.tabcontent`
 *   （内容容器，marginBottom = 54dp + 导航条 inset 为应用内容储备）与
 *   [BAR_ID_NAME] `tab_bar_container_fl`（贴底 FrameLayout，高 54dp，clipChildren=false，
 *   内含 `tab_bar_bg_iv` 背景 + TabWidget（`android.R.id.tabs`，bottomMargin =
 *   导航条 inset 运行时写入，应用自抬到小白条上方）+ 两个 AI tab ViewStub）。
 * - 每 tab 视图 = `com.alipay.android.launcher.tab.TabWidgetItemView`（FrameLayout 子类，
 *   inflate `tab_widget_item`：`tab_icon` ImageView + `tab_description` TextView +
 *   `alipay_tab_flag` BadgeView + GIF/cdp 两个懒加载 ViewStub）；首页 = `HomeTabWidgetItemView`
 *   （`home_tab_widget_item` 同构）、生活 = `LifeTabWidgetItemView`。经
 *   `TabSpec.setIndicator(view)` 直接 addView 进平台 TabWidget。
 * - 选中机制：AOSP `TabHost.addTab` 给每个 indicator 挂 `OnTabClickListener` →
 *   `setCurrentTab` → 框架 `TabWidget.setCurrentTab` 对全部条目 `setSelected`——框架轨即
 *   外观轨：图标 = StateListDrawable{state_selected}（setTabIcon 构造）、文字 =
 *   ColorStateList{state_selected}（refreshTabNameColor/tab_text_color 资源选择器）。
 *   引擎 isSelected 读数成立；预览翻转走 [descriptor.itemCheckedApplier] = 标准视图
 *   `setSelected`（纯视觉瞬时切图，无切页副作用）。
 * - 点击链路：条目上有框架 `TabHost.OnTabClickListener`——`performClick` 转发即完整
 *   切页链（已选中回顶/刷新语义经 onTabChanged），零适配、零混淆锚点，无需
 *   tabClickHandler（区别于淘宝的自定义 GestureDetector 条目）。
 * - 内容延伸：`tabcontent` marginBottom 是唯一内容储备（= `getContentBottomMargin()`：
 *   54dp + 导航条 inset + jinni margin），写入口 attachTab 直写与
 *   `TabBarManager.setTabContentBottomMargin()`（insets reLayout / refreshTabBarInternal）——
 *   帧循环失配归零（基准记录，仅失配写入）+ content 链底部 padding 清除
 *   （框架 decor-fit 的导航栏 padding，淘宝同款 bound 防误清）。
 * - 导航栏透明：应用两条设色路径（`NavigationBarManager.setNavigationBarTransparent` /
 *   `refreshNavigationBarInternal` 按页设色）全部收口到框架 `PhoneWindow`——吞非透明色
 *   与对比度蒙层（商店/淘宝同款 hook）+ onResume/逐帧重申。
 * - 底栏隐藏跟随：`TabBarViewModel.isTabBarHidden()` 语义 = visibility != VISIBLE ||
 *   alpha < 0.1 || |translationY| > 1（显隐由各 tab 页 widget group 驱动）——
 *   mirrorVisibleGate 按可见 + isShown + 平移过半即藏（微博式）；装配压制 alpha=0 与
 *   应用的 alpha 语义冲突已核对无害（isTabBarHidden=true 分支走设色路径，被吞色 hook 收口）。
 * - 冷启动闪现：容器为平台 FrameLayout 无构造器可 hook——早期压制锚定
 *   `TabBarViewModel`（五参构造器，onCreateView inflate 后立即调用，参数[1] 即底栏
 *   容器）after alpha=0（淘宝/健康三段式：8s 兜底 + 会话认领）。
 */
object AlipayAdapter : AppAdapter {

    override val targetPackage: String = "com.eg.android.AlipayGphone"
    override val displayName: String = "支付宝"
    override val tabActivityClassName: String? = TAB_ACTIVITY
    override val baselineVersions: List<String> = listOf("12.12.16.7200")

    private const val TAB_ACTIVITY = "com.eg.android.AlipayGphone.AlipayLogin"
    private const val BAR_ID_NAME = "tab_bar_container_fl"
    private const val VIEW_MODEL_CLASS = "com.alipay.android.launcher.tab.TabBarViewModel"
    private const val TL_COMMON_UTILS_CLASS = "com.alipay.mobile.tablauncher.util.TLCommonUtils"
    private const val ITEM_PACKAGE_PREFIX = "com.alipay.android.launcher.tab."

    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L
    private const val BOTTOM_RESERVE_SLACK_PX = 8

    // —— 运行期状态（弱引用防泄漏） ——
    @Volatile private var activityRef: WeakReference<Activity>? = null
    @Volatile private var activeSessionRef: WeakReference<GlassSession>? = null
    @Volatile private var cachedNavInsetLift: Int = -1
    private val clearedBottomPaddings = ArrayList<Pair<View, Int>>(2)
    @Volatile private var tabContentRef: WeakReference<View>? = null
    @Volatile private var tabContentBaseBottomMargin: Int = -1

    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    /** 受压制容器的"应用期望 alpha"镜像：拦截吞掉的非零写入记录在此（写 0 放行也记录），
     *  供 mirrorVisibleGate 按应用语义判定显隐——应用若走纯 alpha 渐隐隐藏底栏，
     *  渲染虽被压制、玻璃侧仍能跟随隐藏，不滞留。会话装配时置 1（延续可见态）。 */
    private val suppressedAlphaByView: MutableMap<View, Float> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Float>())

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = null,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = false,
        // 条目外观全量由标准视图 selected 态驱动（图标 StateListDrawable + 文字
        // ColorStateList 均 state_selected 双态），标准 setSelected 即纯视觉落定翻转
        itemCheckedApplier = { item, checked -> item.isSelected = checked },
        // 条目自配色（图标/文字选择器随主题），整层染色会盖掉原生配色
        tintRefractionContent = false,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == TAB_ACTIVITY

    override fun findTabBar(content: ViewGroup): View? {
        // 首选：tab_bar_container_fl 资源 id（合并 bundle 包资源名未混淆，getIdentifier 可查）
        val barId = content.resources.getIdentifier(BAR_ID_NAME, "id", content.context.packageName)
        if (barId != 0) {
            var found: View? = null
            fun walkId(v: View?) {
                if (v == null || found != null) return
                if (v.id == barId) {
                    found = v
                    return
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walkId(v.getChildAt(i))
            }
            walkId(content)
            if (found != null) return found
        }
        // 兜底：TabWidget（平台 id，免疫资源名漂移）的父容器即底栏容器
        return findTabWidget(content)?.parent as? View
    }

    override fun tabViewsOf(bar: View): List<View> {
        val widget = findTabWidget(bar) ?: return emptyList()
        val items = ArrayList<View>(widget.childCount)
        for (i in 0 until widget.childCount) {
            val child = widget.getChildAt(i) ?: continue
            // 防御性过滤：条目类全部在 launcher.tab 包（TabWidgetItemView 及其子类）。
            // 过滤失效（类名漂移）时退回全部子项——TabWidget 子项本就只有条目。
            if (child.javaClass.name.startsWith(ITEM_PACKAGE_PREFIX) || items.isEmpty()) {
                items.add(child)
            }
        }
        return items
    }

    private fun findTabWidget(root: View): ViewGroup? {
        if (root is ViewGroup && root.id == android.R.id.tabs) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTabWidget(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
        cachedNavInsetLift = -1
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // 早期压制（三段式）：TabBarViewModel 五参构造器在 onCreateView inflate 完成后立即
        // 调用（早于首帧），参数[1] = tab_bar_container_fl 容器——构造即 alpha=0 压掉
        // inflate 到装配之间的原栏闪现窗口。8s 兜底还原 + 会话认领（onSessionCreated 移除）。
        // initialize=false：进程加载期宿主 Application 未建，连锁初始化会闪退（微博 §5.7 纪律）。
        runCatching {
            val modelClass = Class.forName(VIEW_MODEL_CLASS, false, loader)
            val ctor = modelClass.getDeclaredConstructor(
                android.widget.TabHost::class.java,
                android.widget.FrameLayout::class.java,
                android.widget.FrameLayout::class.java,
                android.widget.TabWidget::class.java,
                android.widget.FrameLayout::class.java,
            )
            facade.around(ctor, null, { call ->
                (call.args.getOrNull(1) as? View)?.let { suppressEarly(it, facade) }
                null
            })
            facade.log(
                android.util.Log.INFO,
                "hooked TabBarViewModel.<init>(5-arg): early suppress",
            )
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook TabBarViewModel ctor failed (app updated?)", it)
        }
        // 原栏 alpha 写入拦截（真机三症状根因修复）：应用自己会写回容器 alpha=1——
        // TabLauncherFragment.onResume 尾部 resetTabBarAlpha() 对 getTabBarViews()（含底栏
        // 容器）setAlpha(1.0f)+setTranslationY(0)（冷启动/切 tab 恢复期触发；触摸路径亦
        // 有其他写入口），把早期压制/装配压制的 alpha=0 冲掉，帧循环 gate 下一帧才压回
        // ——间隔一帧渲染即"原底栏闪现"（冷启动出现 / 切首页出现一会 / 触摸瞬间闪现）。
        // 对平台 View.setAlpha 做全局 hook，回调按实例 identity 判定：仅受压制容器
        // （早期压制集合 + 活跃会话容器）的非零写入被吞，其余视图全部直通；
        // 写 0（压制自身）永远放行；restore 通道经 onAuxGlassRestore 清活跃引用后
        // 引擎的 alpha=1 还原天然放行。hook 安装失败降级为仅帧循环压制（闪现残留）。
        runCatching {
            val setAlpha = View::class.java.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType)
            facade.around(setAlpha, { call ->
                val view = call.thisObj as? View ?: return@around null
                val alpha = call.args.firstOrNull() as? Float ?: return@around null
                if (!isSuppressedContainer(view)) return@around null
                suppressedAlphaByView[view] = alpha
                if (alpha != 0f) false else null
            }, null)
            facade.log(android.util.Log.INFO, "hooked View.setAlpha: suppressed-container non-zero writes swallowed")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook View.setAlpha failed (flash-back may remain)", it)
        }
        // 内容储备 margin 写入点拦截（深色模式切 tab 闪白根因修复）：tabcontent 的
        // 54dp+inset 储备归零靠帧循环重申是"隔一帧"——切 tab 链路（widget group
        // onResume → refreshTabBar → setTabContentBottomMargin，attachTab 亦同口）经
        // TLCommonUtils.setViewMargin 把大 margin 写回，一帧留白在深色下呈白闪。
        // hook 该静态方法：目标（id = android.R.id.tabcontent，平台 id 唯一，无会话
        // 依赖、覆盖装配前窗口）且 bottom > 0 时同栈以 0 重入原方法（重入 bottom=0
        // 不再命中，收敛），原调用跳过。restore 走 lp 直改不经过此方法，不受影响；
        // 模块停用时 ConfigCheckedFacade 惰性直通恢复应用原生 margin。
        runCatching {
            val utilsClass = Class.forName(TL_COMMON_UTILS_CLASS, false, loader)
            val setViewMargin = utilsClass.getDeclaredMethod(
                "setViewMargin",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            facade.around(setViewMargin, { call ->
                val view = call.args.getOrNull(0) as? View ?: return@around null
                val bottom = call.args.getOrNull(4) as? Int ?: return@around null
                if (bottom > 0 && view.id == android.R.id.tabcontent) {
                    runCatching {
                        setViewMargin.invoke(
                            null,
                            view,
                            call.args.getOrNull(1) ?: 0,
                            call.args.getOrNull(2) ?: 0,
                            call.args.getOrNull(3) ?: 0,
                            0,
                        )
                    }
                    0 // 非 null 且非 Unit → 跳过原调用（大 margin 不落地）
                } else null
            }, null)
            facade.log(android.util.Log.INFO, "hooked TLCommonUtils.setViewMargin: tabcontent reserve zeroed in-stack")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook TLCommonUtils.setViewMargin failed (content flash may remain)", it)
        }
        // 导航栏透明（商店 §2.2 / 淘宝 §13.2 同款）：应用全部设色路径收口到框架
        // PhoneWindow（setNavigationBarTransparent 与 refreshNavigationBarInternal 最终都调
        // window.setNavigationBarColor），吞非透明色与对比度蒙层 true；透明色与蒙层关闭
        // 另由逐帧重申兜底。停用全部放行（重启还原）。
        for (method in listOf("setNavigationBarColor", "setNavigationBarDividerColor")) {
            val m = runCatching {
                Class.forName("com.android.internal.policy.PhoneWindow", true, loader)
                    .getDeclaredMethod(method, Int::class.javaPrimitiveType)
            }.getOrNull()
            if (m == null) {
                facade.log(android.util.Log.WARN, "hook $method failed: PhoneWindow method not found")
                continue
            }
            facade.swallowArgIf(m, 0) { arg ->
                config().enabled && arg != android.graphics.Color.TRANSPARENT
            }
        }
        runCatching {
            val contrast = Class.forName("com.android.internal.policy.PhoneWindow", true, loader)
                .getDeclaredMethod("setNavigationBarContrastEnforced", Boolean::class.javaPrimitiveType)
            facade.swallowArgIf(contrast, 0) { arg ->
                config().enabled && arg == true
            }
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook setNavigationBarContrastEnforced failed", it)
        }
    }

    private fun suppressEarly(view: View, facade: HookFacade) {
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        facade.log(android.util.Log.INFO, "alipay early-suppress tab bar container (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                // 先移出拦截集合再写 1：setAlpha hook 按 identity 判定，此时写入天然放行
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "alipay early-suppress fallback restore (session never claimed)",
                )
            }
        }, EARLY_SUPPRESS_FALLBACK_MS)
    }

    /** setAlpha 拦截判定：受压制容器 = 早期压制集合成员或活跃会话的底栏容器（identity）。
     *  restore 通道：onAuxGlassRestore 先清活跃引用，引擎随后的 alpha=1 还原自然放行。 */
    private fun isSuppressedContainer(view: View): Boolean {
        if (synchronized(earlySuppressed) { earlySuppressed.contains(view) }) return true
        return activeSessionRef?.get()?.tabContainer === view
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        activityRef = WeakReference(activity)
        earlySuppressed.remove(session.tabContainer)
        activeSessionRef = WeakReference(session)
        suppressedAlphaByView[session.tabContainer] = 1f
        if (session.originalContainerVisibility != View.VISIBLE) {
            session.tabContainer.visibility = session.originalContainerVisibility
        }
        GlassInstaller.navInsetProvider = { computeNavInsetLift(session, activity) }
        // restore() 首个回调即清活跃引用与 alpha 镜像：引擎随后的 tabContainer.alpha = 1f
        // 还原写入不再命中 setAlpha 拦截（identity 已不匹配），模块停用/会话重建时原栏正常恢复
        session.onAuxGlassRestore = {
            activeSessionRef = null
            suppressedAlphaByView.remove(session.tabContainer)
            restoreBottomNormalizations()
        }
        session.mirrorVisibleGate = { mirrorGate(session) }
    }

    /**
     * 逐帧门控 + 底部归一化。isTabBarHidden 语义（应用侧同款）：可见 + isShown +
     * 平移未过半 + 应用期望 alpha（setAlpha 拦截镜像值）未低于阈值；平移分量写
     * GlassBarState 让玻璃随应用渐隐位移跟随。稳态零写入，仅失配时重申内容延伸
     * （tabcontent 储备归零 / content 链 padding 清除）。
     */
    private fun mirrorGate(session: GlassSession): Boolean {
        val bar = session.tabContainer
        val ty = bar.translationY
        if (GlassBarState.translationY != ty.coerceAtLeast(0f)) {
            GlassBarState.translationY = ty.coerceAtLeast(0f)
        }
        if (bar.visibility == View.VISIBLE && bar.alpha != 0f) bar.alpha = 0f
        reassertBottomNormalizations(session)
        reassertNavigationBarTransparent()
        val appliedAlpha = suppressedAlphaByView[bar] ?: 1f
        return bar.visibility == View.VISIBLE && bar.isShown &&
            ty > -bar.height * 0.5f && appliedAlpha > 0.1f
    }

    private fun reassertNavigationBarTransparent() {
        val window = activityRef?.get()?.window ?: return
        runCatching {
            if (window.navigationBarColor != android.graphics.Color.TRANSPARENT) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            if (android.os.Build.VERSION.SDK_INT >= 29 && window.isNavigationBarContrastEnforced) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    /** 玻璃栏抬升（真实导航栏 inset，会话级缓存）：手势导航下该值是常量；切页 insets
     *  重派发期间瞬时读数跳变会让以抬升为源的几何抖动（淘宝 §13.2 同款缓存策略）。 */
    private fun computeNavInsetLift(session: GlassSession, activity: Activity): Int {
        cachedNavInsetLift.takeIf { it >= 0 }?.let { return it }
        val resolved = runCatching {
            session.tabContainer.rootWindowInsets
                ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                ?.bottom?.coerceIn(0, 400)
        }.getOrNull() ?: -1
        if (resolved >= 0) cachedNavInsetLift = resolved
        return resolved
    }

    /**
     * 逐帧底部归一化：①tabcontent 底部储备归零（应用在 attachTab/setTabContentBottomMargin
     *  写回 = tabcontent marginBottom = 54dp+inset，失配即再归零）；②content 链底部 padding
     *  清除（框架 decor-fit 的导航栏 padding，上限 2×抬升+富余防误清业务大 padding）。
     *  仅失配时写入，稳态零写入。
     */
    private fun reassertBottomNormalizations(session: GlassSession) {
        val activity = activityRef?.get() ?: return
        runCatching {
            // ① tabcontent marginBottom 归零（基准首见记录供还原）
            var tabContent = tabContentRef?.get()?.takeIf { it.isAttachedToWindow }
            if (tabContent == null) {
                tabContent = activity.findViewById<View>(android.R.id.tabcontent)
                tabContentRef = WeakReference(tabContent)
            }
            tabContent?.let { content ->
                val lp = content.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    if (tabContentBaseBottomMargin < 0) {
                        tabContentBaseBottomMargin = lp.bottomMargin
                    }
                    if (lp.bottomMargin != 0) {
                        lp.bottomMargin = 0
                        content.layoutParams = lp
                        content.requestLayout()
                    }
                }
            }
            // ② content 链底部 padding 清除
            val decor = activity.window?.decorView
            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            if (decor != null && contentRoot != null) {
                val lift = computeNavInsetLift(session, activity).coerceAtLeast(0)
                val bound = lift * 2 + BOTTOM_RESERVE_SLACK_PX
                var node: ViewParent = contentRoot
                while (node is View) {
                    val v = node
                    if (v.paddingBottom in 1..bound) {
                        synchronized(clearedBottomPaddings) {
                            if (clearedBottomPaddings.none { it.first === v }) {
                                clearedBottomPaddings.add(v to v.paddingBottom)
                            }
                        }
                        if (v.paddingBottom != 0) {
                            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                        }
                    }
                    if (v === decor) break
                    node = v.parent
                }
            }
        }
    }

    /** 还原（restore() 尾部）：tabcontent 底部储备与 content 链 padding 回基准。 */
    private fun restoreBottomNormalizations() {
        tabContentRef?.get()?.let { content ->
            val base = tabContentBaseBottomMargin
            if (base > 0) runCatching {
                val lp = content.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null && lp.bottomMargin != base) {
                    lp.bottomMargin = base
                    content.layoutParams = lp
                }
            }
        }
        tabContentBaseBottomMargin = -1
        synchronized(clearedBottomPaddings) {
            for ((v, value) in clearedBottomPaddings) {
                runCatching {
                    if (v.paddingBottom == 0 && v.paddingBottom != value) {
                        v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, value)
                    }
                }
            }
            clearedBottomPaddings.clear()
        }
    }
}
