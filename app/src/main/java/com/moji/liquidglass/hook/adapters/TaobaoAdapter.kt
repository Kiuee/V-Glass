package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.MotionEvent
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
 * 淘宝（com.taobao.taobao，桌面名"淘宝"，版本 10.64.20.608 取证）适配器。
 *
 * - 主宿主链路：launcher = [TAB_ACTIVITY_BTB]（Welcome 快启容器）。主控制器工厂
 *   `tb.yx40.b()` 把 BTBMainController（fragment 模型，setContentView
 *   `tb_activity_main_new` → loadRootFragment TBMainFragment → inflate
 *   `tb_main_fragment`）与 ATBMainController（降级模型，setContentView
 *   `tb_activity_main`）都装进 **Welcome 这一个 Activity**；[TAB_ACTIVITY_ATB]
 *   仅在深链显式启动时作为第二宿主存在，两套布局同构（内容容器
 *   `R.id.tbTabFragment` marginBottom 48dp + [BAR_CLASS] 贴底 56dp），探测两处同收。
 * - 底栏结构：[BAR_CLASS]（非混淆，继承平台 TabHost，XML id = `android:id/tabhost`）。
 *   每 tab 视图 = [TAB_ITEM_CLASS]（非混淆 FrameLayout，编程创建无资源 id：图标
 *   TUrlImageView 52dp 背景挂 StateListDrawable{state_selected} + 标签 TextView 11sp
 *   ColorStateList + 数字角标）。5 个 tab（配置表 `navigation.a.u()`）。
 * - 选中机制：条目自定义 `mSelected` + `setSelect(boolean, boolean)` 纯视觉翻转；
 *   `View.isSelected` 由框架 `TabHost.setCurrentTab → TabWidget.setCurrentTab` 同步
 *   （AOSP 实证）——引擎 isSelected 读数成立，预览翻转走应用轨两参 setSelect。
 * - 点击链路：条目**无 OnClickListener**——触摸经 onTouchEvent 的 GestureDetector →
 *   clickEvent（已选中回顶 / 未选中 setCurrentTab 完整切页）→ [descriptor.tabClickHandler]
 *   合成 DOWN/UP 直入条目 onTouchEvent 复刻原生手势全链路，零混淆锚点。
 * - iconMode = WHOLE_TAB_FULL 整条目 1:1 镜像（子视图无资源 id、角标/标签随条目自含）；
 *   条目自配色 → tintRefractionContent=false。
 * - 底部几何（LiquidTab `setNavigationBarImmersion` 同款配方）：窗口延伸 =
 *   `LAYOUT_STABLE | LAYOUT_HIDE_NAVIGATION` 传统 flags（窗口 frame 延到导航条后、
 *   不退出 decor-fit）+ 透明导航栏（逐帧重申；吞色 hook 已废，见 №12.7 教训 8）；
 *   应用切页会重写 systemUiVisibility 丢掉 flags（其 enableImmersiveStatus 组合不含
 *   LAYOUT_HIDE_NAVIGATION）→ `View.setSystemUiVisibility` 钩子 after **同栈补位**
 *   （先于任何 traversal，窗口永远看不到缺位状态——帧循环补位补不上同帧 traversal，
 *   真机实证"切页闪上去再回位"）。玻璃栏抬升 = 导航栏 inset（会话级缓存，切页 insets
 *   重派发期间读数跳变会造成 bar 抖动）。
 * - 内容延伸（LiquidTab 三件套）：①内容脊无条件延伸（tbTabFragment 父链 ≤8 层
 *   MATCH_PARENT + margin 0，不做页面判定）+ content 链底部 padding 清除（框架
 *   decor-fit 的导航栏 padding）；②购物车 Weex 视口 padding（结算栏固定于视口底、
 *   平移后代无效，`icart_weex_root_view` paddingBottom = 视口底到玻璃栏顶）；③购物车
 *   feed 拉伸回窗口底（Scroll/Recycler 启发式探测）。首页优惠券 tips
 *   （homepage_pop_view）抬升写入 lp.bottomMargin 布局锚定（应用 ~2Hz 重排版不再冲掉）。
 * - 隐藏链路：`tb.nms` INVISIBLE + `tb.ols` translationY 平移动画——mirrorVisibleGate
 *   逐帧跟随（平移只取下滑分量 ty≥0；应用点击后自身 tabhost 上移避让 ~54px 不跟随，
 *   否则叠加我们的抬升 = 双重避让，真机实证"点击后整体抬高"）。
 * - 冷启动闪现：hook [BAR_CLASS] (Context,AttributeSet) 构造器早期压制（平台 id
 *   `android:id/tabhost` 门控 + 8s 兜底 + 会话认领三段式）。
 */
object TaobaoAdapter : AppAdapter {

    override val targetPackage: String = "com.taobao.taobao"
    override val displayName: String = "淘宝"
    override val tabActivityClassName: String? = TAB_ACTIVITY_BTB
    override val baselineVersions: List<String> = listOf("10.64.20.608")

    private const val TAB_ACTIVITY_BTB = "com.taobao.tao.welcome.Welcome"
    private const val TAB_ACTIVITY_ATB = "com.taobao.tao.TBMainActivity"
    private const val BAR_CLASS = "com.taobao.tao.navigation.TBFragmentTabHost"
    private const val TAB_ITEM_CLASS = "com.taobao.tao.navigation.NavigationTabIndicatorView"
    private const val TAB_WIDGET_CLASS = "android.widget.TabWidget"
    private const val CONTENT_ID_NAME = "tbTabFragment"
    private const val POP_TIPS_ID_NAME = "homepage_pop_view"
    private const val CART_WEEX_ROOT_ID_NAME = "icart_weex_root_view"
    private const val CART_FEED_PROBE_INTERVAL_MS = 500L

    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L
    private const val BOTTOM_RESERVE_SLACK_PX = 8
    private const val GLASS_TOP_EXTRA_DP = 8f

    private val REQUIRED_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    // —— 运行期状态（弱引用防泄漏） ——
    @Volatile private var setSelectMethod: java.lang.reflect.Method? = null
    @Volatile private var activityRef: WeakReference<Activity>? = null
    @Volatile private var hostDecorRef: WeakReference<View>? = null
    @Volatile private var contentViewRef: WeakReference<View>? = null
    @Volatile private var contentBaseBottomMargin: Int = -1
    @Volatile private var cachedNavInsetLift: Int = -1
    private val clearedBottomPaddings = ArrayList<Pair<View, Int>>(2)

    @Volatile private var popTipsRef: WeakReference<View>? = null
    @Volatile private var popTipsBaseBottomMargin: Int = Int.MIN_VALUE
    private val clipReleased = ArrayList<Pair<View, Boolean>>(8)

    @Volatile private var cartWeexRootRef: WeakReference<ViewGroup>? = null
    @Volatile private var cartBasePadding: IntArray? = null
    @Volatile private var cartInitialLayoutRequested: View? = null
    @Volatile private var cartFeedRef: WeakReference<View>? = null
    @Volatile private var cartFeedLastProbeMs: Long = 0L
    @Volatile private var cartFeedOriginalHeight: Int = -1

    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = false,
        itemCheckedApplier = { item, checked -> flipSelect(item, checked) },
        tabClickHandler = { slotIndex, tabViews, _ ->
            tabViews.getOrNull(slotIndex)?.let { dispatchNativeTap(it) }
        },
        tintRefractionContent = false,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == TAB_ACTIVITY_BTB || activity.javaClass.name == TAB_ACTIVITY_ATB

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
        fun walkId(v: View?): View? {
            if (v == null) return null
            if (v.id == android.R.id.tabhost) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) {
                walkId(v.getChildAt(i))?.let { return it }
            }
            return null
        }
        return walkId(content)
    }

    override fun tabViewsOf(bar: View): List<View> {
        val widget = findFirstByClass(bar, TAB_WIDGET_CLASS) ?: return emptyList()
        val items = ArrayList<View>(widget.childCount)
        for (i in 0 until widget.childCount) {
            widget.getChildAt(i)?.let { items.add(it) }
        }
        return items.filter { it.javaClass.name == TAB_ITEM_CLASS }
    }

    private fun findFirstByClass(root: View, className: String): ViewGroup? {
        if (root.javaClass.name == className && root is ViewGroup) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirstByClass(root.getChildAt(i), className)?.let { return it }
            }
        }
        return null
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
        cachedNavInsetLift = -1
        // 注意：LAYOUT flags 不在此处写（onResume 时窗口尚未 attach）——pre-attach 携带
        // LAYOUT_HIDE_NAVIGATION 会进入 vivo 系统侧按窗口的边到边判定（№12.7 教训 8）。
        // flags 由 onSessionCreated 断言与 setSystemUiVisibility 补位 hook 在 attach 后写。
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        runCatching {
            // initialize=false：进程加载期宿主 Application 未建，连锁初始化会闪退（微博 §5.7 纪律）
            val barClass = Class.forName(BAR_CLASS, false, loader)
            val ctor = barClass.getDeclaredConstructor(
                android.content.Context::class.java,
                android.util.AttributeSet::class.java,
            )
            facade.around(ctor, null, { call ->
                (call.thisObj as? View)?.let { suppressEarly(it, facade) }
                null
            })
            facade.log(
                android.util.Log.INFO,
                "hooked TBFragmentTabHost.<init>(Context,AttributeSet): early suppress",
            )
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook TBFragmentTabHost ctor failed (app updated?)", it)
        }
        // 注意：不吞 PhoneWindow 设色三件套（商店 §2.2 旧配方已废）——吞参跳过方法体 =
        // 跳过 vivo NavigationBarWindow 经 setNavigationBarColor 路径的策略记账，策略随后
        // 按"未适配"重强制不透明色（№12.7 教训 8）；放行让策略全程知情，透明保持由逐帧
        // 重申 + post-attach 断言承担。
        // 窗口延伸 flags 同栈补位：应用切页在输入事件里重写 systemUiVisibility（其组合
        // 不含 LAYOUT_HIDE_NAVIGATION）→ 同帧 traversal 用缺位值把窗口重排（bottom-gravity
        // 覆盖层随 content 闪动一帧）。after 同栈补位先于任何 traversal，窗口永远看不到
        // 缺位状态（帧循环时机晚于 traversal，真机实证补不上）。
        runCatching {
            val setSui = View::class.java.getDeclaredMethod(
                "setSystemUiVisibility", Int::class.javaPrimitiveType,
            )
            facade.around(setSui, null, { call ->
                val view = call.thisObj as? View ?: return@around null
                runCatching {
                    if (!view.isAttachedToWindow) {
                        // attach 前（含应用自身写入）剥掉 LAYOUT_HIDE_NAVIGATION——pre-attach
                        // 携带该位会被 vivo 系统侧按窗口标记"想要边到边"→ 强制下巴条
                        // （№12.7 教训 8）；attach 后由本 hook 补位回写。
                        val v = view.systemUiVisibility
                        val cleaned = v and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION.inv()
                        if (cleaned != v) {
                            view.systemUiVisibility = cleaned
                        }
                    } else if (view.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                        view.systemUiVisibility = view.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setSystemUiVisibility: layout bits re-ORed in-stack")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook setSystemUiVisibility failed", it)
        }
        installNavbarTransparentGateHooks(facade, loader)
    }

    /**
     * vivo 导航条「下巴条」链路的进程内压制（№12.7 教训 8）。
     *
     * 完整机制（dexsql 反汇编 + SF/dumpsys/策略装载探针取证，详见 №12.6/№12.7）：
     * vivo 按窗口 attach 时 `NavigationBarWindow.initNavColorPolicy` 从系统拉取**按
     * Activity 组件**的策略（res 配置串）；res 含 `IMMERSE_V2,style:4`（v2 白名单组件如
     * TBMainActivity）→ policy=0 → updateShowChinBar 外层门不开 → 无下巴条；res 为
     * `/IMMERSE//`（Welcome，图标快启宿主）→ policy=4 → 门开 → `isAdapted()` 判定
     * （fit 含导航条等条件）失败 → `LayoutParams.showChinBar=true` → 三层表现：
     * ① system_server 创建 ChinBar SF 子图层（"…Welcome - ChinBar#…"）+ vivo DecorView
     *    `shouldClearNavRect()==true` 时 draw/onPostDraw 以 `getLastChinbarColor()`
     *    （不透明白）`drawRect` 盖条带（onPostDraw 画在子视图之上 = 三重像素取证"单色"来源）；
     * ② 框架导航 ColorView（`android.R.id.navigationBarBackground`）被置可见；
     * ③ 淘宝 `NavigationBarUtils.isNavigationBarShown` 读 ColorView visibility →
     *    `initContentHeight`（-1 哨兵缓存）以「屏高 − 54」定信息流视口 → 条带无内容可画。
     *
     * 压制 = `installChinPolicyNeutralizer` 单层根治（mRes 重写 → 门永不开）。曾叠加的
     * 兜底层（shouldClearNavRect hook / isNavigationBarShown hook / post-attach 断言 /
     * fit 排除 / 500ms 自愈 / ColorView 压制）经有效性实证在中和器生效时全部为空转
     * （最小变体四轮全路径 SF=0），已按无效代码清除。
     */
    private fun installNavbarTransparentGateHooks(facade: HookFacade, loader: ClassLoader) {
        installChinPolicyNeutralizer(facade, loader)
    }

    /** 下巴条程序中和（№12.7 教训 8）：vivo 按窗口 attach 时经
     *  `NavigationBarWindow.initNavColorPolicy` 从系统拉取**按 Activity 组件**的导航策略
     *  （getNavBarParameter bundle：res 配置串）。真机取证：淘宝主 UI 宿主为
     *  `TBMainActivity` 时系统下发 `IMMERSE_V2,style:4/IMMERSE//` → mNavColorPolicy=0/
     *  style=-1 → updateShowChinBar 的外层门（isInImmersiveV2StyleV2 = policy==4 &&
     *  style==0）不开 → 永不画下巴条；主 UI 宿主为 `Welcome`（图标快启流程）时系统只下发
     *  `/IMMERSE//` → policy=4/style=0 → 门开 → isAdapted()（fit 含导航条等）判负 →
     *  强制下巴条盖住条带 = "时好时坏"终极根因（两启动路径主 UI 宿主不同）。
     *  中和 = 把 mRes 重写为 TBMainActivity 同款（补 `IMMERSE_V2,style:4` 前缀）：
     *  dispatchApplyWindowInsets 每次 insets 派发都会用 mRes 重算 policy，只改一次性
     *  policy 字段会被重算冲掉，改源头串让全部重算自然落到 policy=0/style=-1；
     *  init 后的首次字段同步仅覆盖首帧窗口。非 `/IMMERSE//` 形态（其他策略）不动。 */
    private fun installChinPolicyNeutralizer(facade: HookFacade, loader: ClassLoader) {
        runCatching {
            val cls = Class.forName("com.android.internal.policy.NavigationBarWindow", false, loader)
            val m = cls.getDeclaredMethod("initNavColorPolicy", android.view.WindowManager.LayoutParams::class.java)
            val policyF = cls.getDeclaredField("mNavColorPolicy").apply { isAccessible = true }
            val styleF = cls.getDeclaredField("mNavColorStyle").apply { isAccessible = true }
            val resF = cls.getDeclaredField("mRes").apply { isAccessible = true }
            facade.around(m, null, { call ->
                runCatching {
                    val self = call.thisObj
                    val res = resF.get(self) as String
                    if (res == "/IMMERSE//") {
                        resF.set(self, "IMMERSE_V2,style:4$res")
                        if (policyF.getInt(self) == 4 && styleF.getInt(self) == 0) {
                            policyF.setInt(self, 0)
                            styleF.setInt(self, -1)
                        }
                        val title = runCatching {
                            val attrs = call.args.firstOrNull()
                            attrs?.javaClass?.getMethod("getTitle")?.invoke(attrs)?.toString()
                        }.getOrDefault("?")
                        facade.log(
                            android.util.Log.INFO,
                            "chin program neutralized (res → IMMERSE_V2,style:4) for $title",
                        )
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked NavigationBarWindow.initNavColorPolicy: chin neutralizer")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook chin neutralizer failed (not vivo framework?)", it)
        }
    }

    private fun suppressEarly(view: View, facade: HookFacade) {
        if (view.id != android.R.id.tabhost) return
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        facade.log(android.util.Log.INFO, "taobao early-suppress tabhost (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "taobao early-suppress fallback restore (session never claimed)",
                )
            }
        }, EARLY_SUPPRESS_FALLBACK_MS)
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        activityRef = WeakReference(activity)
        hostDecorRef = WeakReference(activity.window?.decorView ?: return)
        earlySuppressed.remove(session.tabContainer)
        if (session.originalContainerVisibility != View.VISIBLE) {
            session.tabContainer.visibility = session.originalContainerVisibility
        }
        GlassBarState.translationY = 0f
        GlassInstaller.navInsetProvider = { computeNavInsetLift(session, activity) }
        session.onAuxGlassRestore = {
            restoreBottomNormalizations()
            resetCartWeexViewport()
        }
        session.mirrorVisibleGate = { mirrorGate(session) }
    }

    private fun mirrorGate(session: GlassSession): Boolean {
        val bar = session.tabContainer
        // 平移跟随只取下滑分量（ty ≥ 0，商店二楼同通道语义）；应用点击后自身 tabhost
        // 上移避让（translationY ≈ −54）不跟随——否则叠加我们的抬升 = 双重避让
        val ty = bar.translationY.coerceAtLeast(0f)
        if (GlassBarState.translationY != ty) GlassBarState.translationY = ty
        if (bar.visibility == View.VISIBLE && bar.alpha != 0f) bar.alpha = 0f
        reassertBottomNormalizations(session)
        reassertNavigationBarTransparent()
        return bar.visibility == View.VISIBLE && bar.isShown
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
            val decor = window.decorView
            if (decor.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                decor.systemUiVisibility = decor.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
            }
        }
    }

    /** 玻璃栏抬升（真实导航栏 inset，会话级缓存）：手势导航下该值是常量（本机 54px），
     *  切页时应用重设 systemUiVisibility → insets 重派发期间瞬时读数跳变（54→0→54）
     *  会让一切以抬升为源的几何抖动——仅装配期与 onResume 刷新缓存，帧循环读缓存。 */
    private fun computeNavInsetLift(session: GlassSession, activity: Activity): Int {
        cachedNavInsetLift.takeIf { it >= 0 }?.let { return it }
        val resolved = runCatching {
            session.tabContainer.rootWindowInsets
                ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                ?.bottom?.coerceIn(0, 400)
        }.getOrNull() ?: runCatching {
            val wm = activity.getSystemService(android.view.WindowManager::class.java) ?: return@runCatching null
            (wm.maximumWindowMetrics.bounds.bottom - wm.currentWindowMetrics.bounds.bottom)
                .coerceIn(0, 400)
        }.getOrNull() ?: -1
        if (resolved >= 0) cachedNavInsetLift = resolved
        return resolved
    }

    /** 逐帧底部归一化：①content 链底部预留清除（框架 decor-fit 的导航栏 padding，
     *  上限 2×抬升+富余防误清业务大 padding）；②内容脊无条件延伸；③购物车 Weex 视口
     *  padding + feed 拉伸；④首页优惠券 tips 抬升。仅失配时写入，稳态零写入。 */
    private fun reassertBottomNormalizations(session: GlassSession) {
        val activity = activityRef?.get() ?: return
        runCatching {
            val decor = activity.window?.decorView
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            if (decor != null && content != null) {
                val lift = computeNavInsetLift(session, activity).coerceAtLeast(0)
                val bound = lift * 2 + BOTTOM_RESERVE_SLACK_PX
                var node: ViewParent = content
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
            extendContentSpine(activity)
            adjustCartWeexViewport(session, activity)
            liftHomepagePopTips(session, activity)
        }
    }

    /** 内容脊延伸（LiquidTab `extendContentThroughNativeInset` 同款）：从内容容器沿父链
     *  向上 ≤8 层逐层 height=MATCH_PARENT + bottomMargin=0（基准 margin 首见记录供还原），
     *  直到父层覆盖 ≥95% 屏高。无条件执行、不做页面判定——购物车结算栏预留由 Weex
     *  视口 padding 承担（[adjustCartWeexViewport]），不在内容脊上做豁免。 */
    private fun extendContentSpine(activity: Activity) {
        runCatching {
            val decor = activity.window?.decorView ?: return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            var current: View = contentViewRef?.get()?.takeIf { it.isAttachedToWindow }
                ?: activity.findViewById<View>(contentViewId(activity))
                ?: return
            contentViewRef = WeakReference(current)
            repeat(8) {
                if (contentBaseBottomMargin < 0) {
                    contentBaseBottomMargin =
                        (current.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                }
                val parent = current.parent as? ViewGroup ?: return
                val params = current.layoutParams
                var changed = false
                if (params.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    changed = true
                }
                if (params is ViewGroup.MarginLayoutParams && params.bottomMargin != 0) {
                    params.bottomMargin = 0
                    changed = true
                }
                if (changed) {
                    current.layoutParams = params
                    current.requestLayout()
                    parent.requestLayout()
                }
                if (parent.height >= decor.height * 0.95f || parent === content) return
                current = parent
            }
        }
    }

    private fun contentViewId(activity: Activity): Int =
        activity.resources.getIdentifier(CONTENT_ID_NAME, "id", activity.packageName)

    /** 购物车 Weex 视口适配（LiquidTab `adjustCart` 同款）：结算栏固定于 Weex 视口底
     *  （平移 Android 后代无效、"Never compete with that layout"）——视口
     *  paddingBottom = 视口底到玻璃栏顶距离（clamp [基准, 45% 视口高]），Weex 引擎自行
     *  把结算栏铺到玻璃栏上方；引导期守卫（0×0 → post 一次 requestLayout 即返回）。 */
    private fun adjustCartWeexViewport(session: GlassSession, activity: Activity) {
        runCatching {
            val decor = hostDecorRef?.get() ?: return
            val wm = activity.getSystemService(android.view.WindowManager::class.java)
            val screenBottom = wm?.maximumWindowMetrics?.bounds?.bottom ?: return
            var root = cartWeexRootRef?.get()?.takeIf { it.isAttachedToWindow && it.isShown }
            if (root == null) {
                val id = activity.resources.getIdentifier(
                    CART_WEEX_ROOT_ID_NAME, "id", activity.packageName,
                )
                root = (if (id != 0) decor.findViewById<ViewGroup>(id) else null)
                    ?.takeIf { it.isAttachedToWindow && it.isShown }
                    ?.also { cartWeexRootRef = WeakReference(it) }
                if (root == null) return
            }
            val weexRoot = root
            if (weexRoot.width <= 0 || weexRoot.height <= 0) {
                if (cartInitialLayoutRequested !== weexRoot) {
                    cartInitialLayoutRequested = weexRoot
                    weexRoot.post {
                        if (weexRoot.isAttachedToWindow) {
                            weexRoot.requestLayout()
                            (weexRoot.parent as? View)?.requestLayout()
                        }
                    }
                }
                return
            }
            var contentReady = false
            for (i in 0 until weexRoot.childCount) {
                val c = weexRoot.getChildAt(i)
                if (c != null && c.visibility == View.VISIBLE && c.width > 0 && c.height > 0) {
                    contentReady = true
                    break
                }
            }
            if (!contentReady) return
            val base = cartBasePadding ?: intArrayOf(
                weexRoot.paddingLeft,
                weexRoot.paddingTop,
                weexRoot.paddingRight,
                weexRoot.paddingBottom,
            ).also { cartBasePadding = it }
            val barTop = screenBottom - glassTopReservePx(session, activity)
            val loc = IntArray(2).also(weexRoot::getLocationOnScreen)
            val weexBottom = loc[1] + weexRoot.height
            val required = (weexBottom - barTop)
                .coerceAtLeast(base[3])
                .coerceAtMost((weexRoot.height * 0.45f).toInt())
            if (weexRoot.paddingLeft != base[0] || weexRoot.paddingTop != base[1] ||
                weexRoot.paddingRight != base[2] || weexRoot.paddingBottom != required
            ) {
                weexRoot.setPadding(base[0], base[1], base[2], required)
                weexRoot.requestLayout()
                weexRoot.invalidate()
            }
            weexRoot.clipChildren = false
            weexRoot.clipToPadding = false
            extendCartFeedUnderGlass(weexRoot)
        }
    }

    /** 购物车 feed 拉伸（LiquidTab `extendTaobaoCartFeedUnderGlass` 同款）：把页面滚动
     *  视图（类名含 Scroll/Recycler、宽 ≥85% 视口、最大者且 ≥30% 视口高，500ms 节流
     *  探测）的 native 高度拉回真实窗口底，feed 滚动进玻璃条带后方；祖先 ≤8 层释放裁切。 */
    private fun extendCartFeedUnderGlass(weexRoot: ViewGroup) {
        val rootHeight = weexRoot.height
        if (rootHeight <= 0) return
        val now = android.os.SystemClock.uptimeMillis()
        var feed = cartFeedRef?.get()?.takeIf { it.isAttachedToWindow && it.isShown }
        if (feed == null && now - cartFeedLastProbeMs >= CART_FEED_PROBE_INTERVAL_MS) {
            cartFeedLastProbeMs = now
            var best: View? = null
            var bestHeight = 0
            val stack = ArrayDeque<Pair<View, Int>>()
            stack += weexRoot to 0
            while (stack.isNotEmpty()) {
                val (v, depth) = stack.removeLast()
                if (v === weexRoot || v.visibility != View.VISIBLE) continue
                val name = v.javaClass.name
                val scrollable = name.contains("Scroll", true) || name.contains("Recycler", true)
                if (scrollable && v.width >= weexRoot.width * 0.85f && v.height > bestHeight) {
                    best = v
                    bestHeight = v.height
                }
                if (v is ViewGroup && depth < 14) {
                    for (i in 0 until v.childCount) {
                        v.getChildAt(i)?.let { stack += it to (depth + 1) }
                    }
                }
            }
            if (bestHeight < rootHeight * 0.3f) return
            feed = best
            cartFeedRef = java.lang.ref.WeakReference(best)
        }
        val resolved = feed ?: return
        if (cartFeedOriginalHeight < 0) {
            cartFeedOriginalHeight = resolved.layoutParams.height
        }
        val rootLoc = IntArray(2).also(weexRoot::getLocationOnScreen)
        val feedLoc = IntArray(2).also(resolved::getLocationOnScreen)
        val desiredHeight = (rootLoc[1] + rootHeight) - feedLoc[1]
        if (desiredHeight <= resolved.height) return
        val params = resolved.layoutParams
        params.height = desiredHeight
        resolved.layoutParams = params
        var ancestor = resolved.parent as? ViewGroup
        repeat(8) {
            ancestor?.let {
                it.clipChildren = false
                it.clipToPadding = false
            }
            ancestor = ancestor?.parent as? ViewGroup
        }
    }

    private fun resetCartWeexViewport() {
        val root = cartWeexRootRef?.get()
        val base = cartBasePadding
        if (root != null && base != null) {
            runCatching {
                if (root.paddingLeft != base[0] || root.paddingTop != base[1] ||
                    root.paddingRight != base[2] || root.paddingBottom != base[3]
                ) {
                    root.setPadding(base[0], base[1], base[2], base[3])
                    root.requestLayout()
                }
            }
        }
        cartBasePadding = null
        cartInitialLayoutRequested = null
        val feed = cartFeedRef?.get()
        if (feed != null && cartFeedOriginalHeight >= 0) {
            runCatching {
                val params = feed.layoutParams
                if (params.height != cartFeedOriginalHeight) {
                    params.height = cartFeedOriginalHeight
                    feed.layoutParams = params
                }
            }
        }
        cartFeedOriginalHeight = -1
        cartFeedRef = null
    }

    /** 玻璃栏顶预留（px，购物车 Weex 视口与悬浮 tips 共用）：净抬升 + 底部间隙 + 栏高
     *  + 富余（玻璃辉光/液泡放大溢出胶囊边界，贴顶会被辉光盖住一点）。抬升取不到
     *  （-1）时回退原生基准 margin。 */
    private fun glassTopReservePx(session: GlassSession, activity: Activity): Int {
        val lift = computeNavInsetLift(session, activity)
        if (lift < 0) return contentBaseBottomMargin.coerceAtLeast(0)
        val density = activity.resources.displayMetrics.density
        val style = GlassInstaller.glassStyle
        return (lift + (style.bottomGapDp + style.barHeightDp + GLASS_TOP_EXTRA_DP) * density)
            .toInt().coerceAtLeast(0)
    }

    /** 首页优惠券 tips 抬升（R.id.homepage_pop_view，BasePopView 自设 id）：抬升量写入
     *  lp.bottomMargin（布局锚定——应用 ~2Hz 重排版不再冲掉 offsetTopAndBottom 的位移
     *  造成闪动），offset 只补 margin 生效前的本帧残差。 */
    private fun liftHomepagePopTips(session: GlassSession, activity: Activity) {
        runCatching {
            var target = popTipsRef?.get()?.takeIf { it.isAttachedToWindow }
            if (target == null) {
                val id = activity.resources.getIdentifier(POP_TIPS_ID_NAME, "id", activity.packageName)
                if (id == 0) return
                target = activity.findViewById<View>(id)?.also { popTipsRef = WeakReference(it) }
            }
            val view = target ?: return
            if (view.visibility != View.VISIBLE || !view.isShown || view.height <= 0) return
            val reserve = glassTopReservePx(session, activity)
            if (reserve <= 0) return
            val wm = activity.getSystemService(android.view.WindowManager::class.java)
            val screenBottom = wm?.maximumWindowMetrics?.bounds?.bottom ?: return
            val barTop = screenBottom - reserve
            releaseClipChildren(view)
            val lp = view.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                if (popTipsBaseBottomMargin == Int.MIN_VALUE) {
                    popTipsBaseBottomMargin = lp.bottomMargin
                }
                val loc = IntArray(2).also(view::getLocationOnScreen)
                val overlap = (loc[1] + view.height) - barTop
                if (overlap > 0) {
                    if (lp.bottomMargin == popTipsBaseBottomMargin) {
                        lp.bottomMargin = popTipsBaseBottomMargin + overlap
                        view.layoutParams = lp
                    } else {
                        view.offsetTopAndBottom(-overlap)
                    }
                }
            } else {
                val loc = IntArray(2).also(view::getLocationOnScreen)
                val overlap = (loc[1] + view.height) - barTop
                if (overlap > 0) view.offsetTopAndBottom(-overlap)
            }
        }
    }

    private fun releaseClipChildren(view: View) {
        var p = view.parent
        var depth = 0
        while (p is ViewGroup && depth < 8) {
            val v = p
            synchronized(clipReleased) {
                if (clipReleased.none { it.first === v }) {
                    clipReleased.add(v to v.clipChildren)
                }
            }
            if (v.clipChildren) v.clipChildren = false
            p = v.parent
            depth++
        }
    }

    private fun restoreBottomNormalizations() {
        val base = contentBaseBottomMargin
        contentViewRef?.get()?.let { view ->
            if (base >= 0) runCatching {
                val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null && lp.bottomMargin != base) {
                    lp.bottomMargin = base
                    view.layoutParams = lp
                }
            }
        }
        if (popTipsBaseBottomMargin != Int.MIN_VALUE) {
            popTipsRef?.get()?.let { view ->
                runCatching {
                    val lp = view.layoutParams
                    if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin != popTipsBaseBottomMargin) {
                        lp.bottomMargin = popTipsBaseBottomMargin
                        view.layoutParams = lp
                    }
                }
            }
            popTipsBaseBottomMargin = Int.MIN_VALUE
        }
        synchronized(clipReleased) {
            for ((v, value) in clipReleased) {
                runCatching { (v as? ViewGroup)?.clipChildren = value }
            }
            clipReleased.clear()
        }
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

    /** 预览选中翻转（§0.5 B.1 纯视觉通道）：反射条目 `setSelect(boolean, boolean)`
     *  （public 非混淆），z2 传 false——仅 mSelected 变化时翻转（瞬时切图无动画，
     *  StateListDrawable/ColorStateList 按视图状态切换 = 落定外观）。 */
    private fun flipSelect(item: View, checked: Boolean) {
        val method = setSelectMethod ?: runCatching {
            item.javaClass.getMethod(
                "setSelect",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull()?.also { setSelectMethod = it } ?: return
        runCatching { method.invoke(item, checked, false) }
    }

    /** 点击派发（tabClickHandler）：合成 DOWN/UP 直入条目 onTouchEvent——原生点击链路
     *  就是该 GestureDetector（条目无 OnClickListener，performClick 无效）；复刻原生
     *  手势全链路（已选中回顶 / 未选中完整切页，含登录门控/UT 埋点）。触点取条目中心。 */
    private fun dispatchNativeTap(item: View) {
        runCatching {
            val now = android.os.SystemClock.uptimeMillis()
            val x = item.width.coerceAtLeast(1) / 2f
            val y = item.height.coerceAtLeast(1) / 2f
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, x, y, 0)
            try {
                item.onTouchEvent(down)
                item.onTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }
    }
}
