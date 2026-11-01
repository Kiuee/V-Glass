package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * vivo 官网（com.vivo.space，应用商店名"vivo 官网"/桌面名"我的vivo"视 ROM 而定）适配器。
 *
 * - 底栏：`com.vivo.space.lib.widget.originui.SpaceVBottomNavigationView`
 *   （extends com.originui.widget.navigation.VBottomNavigationView），挂在主布局
 *   vivospace_main 的 `R.id.v_bottom_view` 上，类名非混淆。
 * - 每 tab 视图：内部 menuLayout（com.originui.widget.navigation.VMenuViewLayout）的
 *   直接子项。子项具体类是混淆类（13.3.0.1 中为 g3.b，FrameLayout 实现
 *   VNavigationBarItemViewInternal），**不能按类名锁定**，按"menuLayout 直接子项 +
 *   含 icon/labels 元素 id"结构识别；元素 id 沿用 originui 库公共命名
 *   （vivo_navigation_bar_item_icon_view / _labels_group），与相册同库同 id。
 * - 选中态：标准 View.isSelected（VMenuViewLayout 点击切换 + setSelected）→
 *   选中同步/点击转发与商店/相册同构。
 * - 图标镜像：WHOLE_TAB 元素级直绘（LottieDrawable 由应用侧 setImage 到图标
 *   ImageView，元素级直绘天然跟随，无需 LOTTIE_RESOLVE）。
 * - 开屏全屏弹窗：用户实测的"开屏广告"真身 = NewProductPopupView（`R.id.popup_container`，
 *   全屏视频弹窗，位于 vivospace_main/appRoot 内部、玻璃 overlay 之下）。进程级监视器
 *   跟踪其 visibility：展示期间玻璃 overlay 置 GONE、关闭后恢复；冷启动会话 overlay 先
 *   隐藏（pending 1.5s），弹窗未出现则视为无弹窗直接显示。此前作用于 logo_adv_layout/
 *   zt.f 的提升/轮询/信号 hook 三层全部作用于错误广告面，已全部删除（§4.7 教训 9）。
 * - 内容延伸（页面底部留白清零）：分发点改投 0 + 收口（VViewUtils/View.setPadding/
 *   View.setLayoutParams，阈值取应用自身基准 qq.d.f）+ 常驻清扫（§4.2）。信息流尾部
 *   footer（168px）是首页触摸/滚动拦截协议的承重墙，**不可归零**（归零导致滚动拦截
 *   死区，已回滚）——信息流最末端玻璃栏周围可见约 168px 页面背景带，为结构性代价。
 */
object VivoSpaceAdapter : AppAdapter {

    override val targetPackage: String = "com.vivo.space"
    override val displayName: String = "vivo 官网"
    override val tabActivityClassName: String? = "com.vivo.space.ui.VivoSpaceTabActivity"
    // 兼容窗口（主册 §0.6.3）= 13.6.0.1：13.3.0.1 的内容延伸锚点（fq.* 包）已随本次
    // 升版删除、未保留候选回退（早于 §0.6.3 立规），不再声明支持。
    override val baselineVersions: List<String> = listOf("13.6.0.1")

    private const val BAR_CLASS = "com.vivo.space.lib.widget.originui.SpaceVBottomNavigationView"
    // 底栏在主布局中的资源 id（类名失配时的结构兜底）
    private const val BAR_ID_NAME = "v_bottom_view"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"
    // 选购页左栏（预留是可用性设计：清零后末项被玻璃栏遮挡、不可滚动到达——用户决策保留）
    private const val CLASSIFY_TAB_STRIP_SIMPLE_NAME = "ClassifyTabLayout"
    // 开屏全屏弹窗（NewProductPopupView 的容器,用户实测的"开屏广告"真身）
    private const val POPUP_ID_NAME = "popup_container"
    // 冷启动 pending：弹窗未出现超过此时长即视为无弹窗,玻璃 overlay 直接显示
    private const val POPUP_PENDING_MS = 1_500L

    // —— 运行期共享状态（单例适配器；WeakReference 防泄漏） ——
    /** 开屏弹窗容器（可见性门控的观测对象）。 */
    @Volatile private var popupRef: WeakReference<View>? = null
    @Volatile private var popupIdCache = 0
    /** 冷启动 pending：会话创建起 overlay 先隐藏,弹窗出现→关闭后或超时未出现才显示。 */
    @Volatile private var splashPending = false
    @Volatile private var splashSeenVisible = false
    @Volatile private var splashPendingStartMs = 0L
    /** 原生底栏容器：预留清零的豁免子树（镜像数据源，自身 inset 维护不可被清）。 */
    @Volatile private var tabBarRef: WeakReference<View>? = null
    /** 应用上下文缓存（qq.d.f 阈值解析用；onTabActivityResumed/onSessionCreated 主线程刷新）。 */
    @Volatile private var appContextRef: WeakReference<Context>? = null
    @Volatile private var classifyFragmentFieldWarned = false
    @Volatile private var classifyVpMissingWarned = false

    // —— 进程级生命周期监视器（独立于模块自身的 Activity.onResume hook 与玻璃会话链路；
    // v1.7 真机取证:宿主冷启动日志风暴期 logd 按 uid 限流,会话级机制的日志被整段丢弃,
    // 进程级 hook 已验证可靠——选购页修复即在此层生效） ——
    @Volatile private var tabActivityRef: WeakReference<Activity>? = null
    @Volatile private var configProvider: (() -> ModuleConfig)? = null
    @Volatile private var monitorActivityRef: WeakReference<Activity>? = null
    @Volatile private var monitorRunning = false
    private var monitorRound = 0
    private val monitorLock = Any()

    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private fun currentApplication(): android.app.Application? =
        runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)
                as? android.app.Application
        }.getOrNull()

    private fun isBottomBar(v: View): Boolean =
        v.javaClass.name == BAR_CLASS || v.javaClass.simpleName == "SpaceVBottomNavigationView"

    private fun idByName(v: View, name: String): Int =
        v.resources.getIdentifier(name, "id", v.context.packageName)

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐商店）
        elementIds = mapOf(
            "icon" to ICON_ID_NAME,
            "labels" to LABELS_ID_NAME,
        ),
        // 条目选中外观翻转方法（拖动悬停预览用）：originui 此版本方法级混淆，g3.b 无
        // setChecked，真实方法为 y(boolean)（原生点击路径 VMenuViewLayout.onClick 循环
        // y(false)/y(true)）。应用更新改名 → 预览静默失效（回退为无预览），见 §4.3。
        itemCheckedSetterMethodName = "y",
        // 关闭液泡折射副本的整层强调色染色：图标素材"白=底色"编码（动画中间帧与刷新/
        // 回顶静态图的间隙都是不透明白），染色会把白间隙一起染成强调色呈实心，且动画
        // 结束切到真透明间隙的静态图时产生"染色实心 → 镂空"闪现。关闭后液泡折射
        // 未染色副本，图标按原样显示（白间隙保持白色），见 §4.7。
        tintRefractionContent = false,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun findTabBar(content: ViewGroup): View? {
        val found = ArrayList<View>(1)
        fun walk(v: View?) {
            if (v == null || found.isNotEmpty()) return
            if (isBottomBar(v)) {
                found.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(content)
        if (found.isNotEmpty()) return found.first()
        // 结构兜底：按资源 id 定位（应用混淆/换 superclass 不影响资源 id）
        val barId = idByName(content, BAR_ID_NAME)
        if (barId != 0) {
            fun walkById(v: View?): View? {
                if (v == null) return null
                if (v.id == barId) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) {
                    walkById(v.getChildAt(i))?.let { return it }
                }
                return null
            }
            return walkById(content)
        }
        return null
    }

    override fun tabViewsOf(bar: View): List<View> {
        // 首选：menuLayout 的直接子项（顺序 = tab 顺序）
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
        // 结构兜底：找同时含 icon+labels 元素的最深视图（混淆类改名后的稳定判据）
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

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        appContextRef = WeakReference(activity)
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }

    // —— 资源 id 解析 ——
    // 该应用资源表（arsc）条目经过混淆改名，getIdentifier 按名字查会返回 0（开屏门控
    // 首版失效的根因）；但代码里的 R.id.xxx 字段引用保留原名与正确值，优先按 R 类字段
    // 反射解析，getIdentifier 仅作兜底。
    private fun resId(loader: ClassLoader?, innerClass: String, name: String, activity: Activity? = null): Int {
        if (loader != null) {
            runCatching {
                return Class.forName("com.vivo.space.$innerClass", true, loader)
                    .getField(name).getInt(null)
            }
        }
        val type = when (innerClass) {
            "R\$id" -> "id"
            "R\$dimen" -> "dimen"
            else -> return 0
        }
        return activity?.resources?.getIdentifier(name, type, activity.packageName) ?: 0
    }

    // —— 开屏全屏弹窗（NewProductPopupView/popup_container）门控 ——
    // 用户实测的"开屏广告"真身是首页进入时的全屏视频弹窗（v1.7 logcat:enterHomePage →
    // ExoPlayer 播放 → dismiss,与首页进入时间线吻合),位于 vivospace_main（appRoot）内部、
    // 玻璃 overlay 之下——此前作用于 logo_adv_layout/zt.f 的提升/轮询/信号 hook 三层全部
    // 作用于错误广告面,已删除（§4.7 教训 9）。弹窗以 setVisibility 切换显隐（dismiss →
    // GONE）,监视器每拍读其 visibility：VISIBLE 期间玻璃 overlay 置 GONE（底栏让位）,
    // 曾 VISIBLE 后转非 VISIBLE（弹窗关闭）恢复 VISIBLE（底栏跟着出现）。
    private fun findPopup(activity: Activity): View? {
        if (popupIdCache == 0) {
            popupIdCache = resId(appClassLoader, "R\$id", POPUP_ID_NAME, activity)
            if (popupIdCache == 0) return null
        }
        return activity.findViewById(popupIdCache)
    }

    private fun syncGlassOverlayWithPopup(activity: Activity) {
        val overlay = findGlassOverlay(activity) ?: return
        val popup = findPopup(activity)
        val popupVisible = popup != null && popup.visibility == View.VISIBLE
        if (popup != null && popupRef?.get() !== popup) popupRef = WeakReference(popup)
        if (popupVisible) splashSeenVisible = true
        if (splashPending) {
            // 冷启动 pending:overlay 保持 GONE,直到弹窗关闭,或 1.5s 未出现（无弹窗/温启动）
            val expired = !splashSeenVisible &&
                android.os.SystemClock.elapsedRealtime() - splashPendingStartMs > POPUP_PENDING_MS
            if ((splashSeenVisible && !popupVisible) || expired) {
                splashPending = false
                if (overlay.visibility != View.VISIBLE) {
                    overlay.visibility = View.VISIBLE
                    android.util.Log.println(
                        android.util.Log.INFO, "LiquidGlass",
                        "gate(pending): released → overlay VISIBLE (popupSeen=$splashSeenVisible)",
                    )
                }
            } else if (overlay.visibility != View.GONE) {
                overlay.visibility = View.GONE
            }
            return
        }
        // 常规同步：弹窗再次展示（如再次进入首页）时继续让位
        if (popupVisible && overlay.visibility != View.GONE) {
            overlay.visibility = View.GONE
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "gate(sync): popup visible → glass overlay GONE",
            )
        } else if (!popupVisible && splashSeenVisible && overlay.visibility == View.GONE) {
            overlay.visibility = View.VISIBLE
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "gate(sync): popup dismissed → glass overlay VISIBLE",
            )
        }
    }

    // —— 进程级生命周期监视器 ——
    // 经 Application.registerActivityLifecycleCallbacks 独立跟踪主 Tab Activity（不依赖
    // 模块自身的 Activity.onResume hook 与玻璃会话链路）。每拍：幂等提升 + 无会话门控
    // （广告 isShown ↔ 玻璃 overlay 显隐）+ 常驻清扫（contentExtend 开启时）；
    // 首次进入后 15s/50s 各做一次取证 dump。冷启动期 500ms×20 密集、之后 2s 常驻。
    // 注意:installAppHooks 在进程加载期运行,此刻 Application 尚未创建
    // （currentApplication()=null）——注册须经 250ms×40 重试直至可用。
    private val lifecycleSpy = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity.javaClass.name != tabActivityClassName) return
            tabActivityRef = WeakReference(activity)
            appContextRef = WeakReference(activity)
            startMonitor(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private fun registerLifecycleSpy(facade: HookFacade) {
        val retry = object : Runnable {
            var remaining = 40
            override fun run() {
                val app = currentApplication()
                if (app == null) {
                    if (remaining > 0) {
                        remaining--
                        mainHandler.postDelayed(this, 250)
                    }
                    return
                }
                runCatching {
                    app.registerActivityLifecycleCallbacks(lifecycleSpy)
                }.onFailure {
                    facade.log(android.util.Log.WARN, "lifecycle spy register failed", it)
                }
            }
        }
        mainHandler.post(retry)
    }

    private fun startMonitor(activity: Activity) {
        synchronized(monitorLock) {
            if (monitorRunning && monitorActivityRef?.get() === activity) return
            monitorActivityRef = WeakReference(activity)
            monitorRunning = true
            monitorRound = 0
        }
        mainHandler.post(monitorTick)
    }

    private val monitorTick = object : Runnable {
        override fun run() {
            val activity = monitorActivityRef?.get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                synchronized(monitorLock) { monitorRunning = false }
                return
            }
            runCatching { monitorOnce(activity) }
                .onFailure {
                    android.util.Log.println(
                        android.util.Log.WARN, "LiquidGlass", "monitor tick failed: $it",
                    )
                }
            monitorRound++
            mainHandler.postDelayed(this, if (monitorRound < 20) 500L else 2_000L)
        }
    }

    /** 监视单拍：①开屏弹窗门控；②常驻清扫（contentExtend 开启时，密集期隔拍、稳定期
     *  每拍）。 */
    private fun monitorOnce(activity: Activity) {
        syncGlassOverlayWithPopup(activity)
        val extendOn = configProvider?.invoke()?.contentExtend != ModuleConfig.CONTENT_EXTEND_OFF
        if (extendOn && (monitorRound >= 20 || monitorRound % 2 == 0)) {
            val fixed = sweepBottomReserve(activity)
            if (fixed > 0) {
                android.util.Log.println(
                    android.util.Log.INFO, "LiquidGlass",
                    "monitor sweep #$monitorRound: fixed=$fixed",
                )
            }
        }
    }

    /** 无会话门控（popup 版）见 [syncGlassOverlayWithPopup]。 */

    private fun findGlassOverlay(activity: Activity): View? {
        val content = activity.findViewById<View>(android.R.id.content) as? ViewGroup ?: return null
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.javaClass.name.contains("ComposeView")) return child
        }
        return null
    }

    // —— 内容延伸（页面底部留白清零） ——
    // 留白公共源 = qq.d（日志名 "BottomNavigationManager"；13.3.0.1 时为 fq.d，整包混淆
    // 迁移 fq→qq，类内结构逐字未变——升版用日志名 grep 定位新包名）：b(Context, c) 变体
    // 转发全量底栏高度（RecommendH5/Forum 系），e() 变体经 qq.e.c(int) 只转发余量
    // （HomePage/Service/Zone）。Classify 页把值写成 bottomMargin，其余走 VViewUtils
    // setPaddingBottom。阈值 = qq.d.f(Context)（应用自身的基准,本设备实测 198px=dp66@3x）。
    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        appClassLoader = loader
        configProvider = config
        // 进程级生命周期监视器:开屏弹窗门控/常驻清扫的可靠执行层,
        // 不受 contentExtend 开关影响（弹窗门控是玻璃栏自身行为,与内容延伸无关）
        registerLifecycleSpy(facade)
        if (config().contentExtend == ModuleConfig.CONTENT_EXTEND_OFF) {
            facade.log(android.util.Log.INFO, "contentExtend=off: page bottom insets untouched")
            return
        }
        // ① 全量分发点 + ② 余量变体：公共依赖 qq.d$c。解析失败只降级 ①② 并 WARN，
        //    **不阻断** ③④⑤⑥（此前 fq.d$c 失配会让全部内容延伸 hook 静默跳过——单点失效）。
        val listenerIface = runCatching { Class.forName("qq.d\$c", true, loader) }.getOrNull()
        if (listenerIface == null) {
            facade.log(android.util.Log.WARN, "qq.d\$c not found (app updated?), dispatch sinks ①② skipped")
        } else {
            val deliverZero: (Any) -> Unit = { listener ->
                listenerIface.getMethod("c", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(listener, 0)
            }
            // ① 全量分发点：立即投递改投 0 并跳过原方法（原方法还会 post 一次布局后重投递，
            //    一并去除）。选购页监听器例外放行（见 ③）。
            runCatching {
                val b = Class.forName("qq.d", true, loader)
                    .getDeclaredMethod("b", android.content.Context::class.java, listenerIface)
                facade.around(b, { call ->
                    val listener = call.args.getOrNull(1)
                    if (listener?.javaClass?.name == "com.vivo.space.shop.classify.i") {
                        return@around null // 放行：一值两写的左栏 margin 是可用性预留，见 ③
                    }
                    listener?.let(deliverZero)
                    false // 非 null 哨兵 → 跳过原方法（void，返回值被忽略）
                }, null)
                facade.log(android.util.Log.INFO, "hooked qq.d.b: bottom inset dispatch zeroed (classify exempt)")
            }.onFailure { facade.log(android.util.Log.WARN, "hook qq.d.b failed (app updated?)", it) }
            // ② 余量变体收口：包装器会把 (高度-dp66) 为负时回退到导航栏高度，改投 0 并跳过
            runCatching {
                val eC = Class.forName("qq.e", true, loader)
                    .getDeclaredMethod("c", Int::class.javaPrimitiveType)
                facade.around(eC, { call ->
                    val inner = call.thisObj?.javaClass?.declaredFields
                        ?.firstOrNull { it.type == listenerIface }
                        ?.apply { isAccessible = true }
                        ?.get(call.thisObj)
                    if (inner != null) deliverZero(inner)
                    false
                }, null)
                facade.log(android.util.Log.INFO, "hooked qq.e.c: extra-height variant zeroed")
            }.onFailure { facade.log(android.util.Log.WARN, "hook qq.e.c failed (app updated?)", it) }
        }
        installClassifyHook(facade, loader)
        // ④ 实时收口（WebView 等容器）：页面状态变化会重新 setPaddingBottom/setMarginBottom
        //    塞入底栏级留白，值 ≥ 阈值 → 归零。阈值惰性解析（首次回调时取上下文反射
        //    qq.d.f，安装发生在 LSPosed 后台线程，§4.7 教训 4b），**无条件安装**，解析
        //    失败放行原值并 WARN 一次。
        runCatching {
            val utils = Class.forName("com.originui.core.utils.VViewUtils", true, loader)
            val setPad = utils.getDeclaredMethod("setPaddingBottom", View::class.java, Int::class.javaPrimitiveType)
            val setMar = utils.getDeclaredMethod("setMarginBottom", View::class.java, Int::class.javaPrimitiveType)
            var vviewPadHit = false
            facade.mapIntArg(setPad, 1) { original ->
                val reserve = resolveReservePx(loader, "VViewUtils sink")
                if (reserve != null && original >= reserve) {
                    if (!vviewPadHit) {
                        vviewPadHit = true
                        facade.log(android.util.Log.INFO, "VViewUtils sink: first reserve padding zeroed ($original)")
                    }
                    0
                } else original
            }
            facade.mapIntArg(setMar, 1) { original ->
                val reserve = resolveReservePx(loader, "VViewUtils sink")
                if (reserve != null && original >= reserve) 0 else original
            }
            facade.log(android.util.Log.INFO, "hooked VViewUtils setPaddingBottom/setMarginBottom (reserve threshold lazy)")
        }.onFailure { facade.log(android.util.Log.WARN, "hook VViewUtils sinks failed", it) }
        // ⑤ 平台级收口：直接 setPadding 的路径（绕过 VViewUtils）——**任意视图**的底栏级
        //    底部 padding（≥ 阈值）实时归零。原 RecyclerView 限定会漏掉 WebView/普通容器
        //    路径；豁免子树见 isReserveExempt。自身调用收敛（归零后 < 阈值不再重入）。
        runCatching {
            val setPadding = View::class.java.getDeclaredMethod(
                "setPadding",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            var padSinkHits = 0
            facade.around(setPadding, null, { call ->
                val view = call.thisObj as? View
                val bottom = (call.args.getOrNull(3) as? Number)?.toInt() ?: 0
                if (view != null && bottom > 0 && !isReserveExempt(view)) {
                    val reserve = resolveReservePx(loader, "View.setPadding sink")
                    if (reserve != null && bottom >= reserve) {
                        padSinkHits++
                        if (padSinkHits == 1) {
                            facade.log(
                                android.util.Log.INFO,
                                "View.setPadding sink: first reserve padding zeroed (${describeView(view)} pb=$bottom)",
                            )
                        }
                        runCatching {
                            setPadding.invoke(
                                view,
                                (call.args.getOrNull(0) as? Number)?.toInt() ?: 0,
                                (call.args.getOrNull(1) as? Number)?.toInt() ?: 0,
                                (call.args.getOrNull(2) as? Number)?.toInt() ?: 0,
                                0,
                            )
                        }
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setPadding: any-view bottom reserve → 0 (threshold lazy)")
        }.onFailure { facade.log(android.util.Log.WARN, "hook View.setPadding sink failed", it) }
        // ⑥ 平台级 margin 收口：直接改写 MarginLayoutParams.bottomMargin（不经 VViewUtils）
        //    的路径——setLayoutParams 落地时 bottomMargin ≥ 阈值 → 归零并回写。ClassifyTabLayout
        //    （选购左栏预留）与广告/底栏/overlay 子树豁免。归零回写触发自身重入，次值
        //    < 阈值即收敛。
        runCatching {
            val setLp = View::class.java.getDeclaredMethod("setLayoutParams", ViewGroup.LayoutParams::class.java)
            var marginSinkHits = 0
            facade.around(setLp, null, { call ->
                val view = call.thisObj as? View
                val lp = call.args.getOrNull(0) as? ViewGroup.MarginLayoutParams
                val bottom = lp?.bottomMargin ?: 0
                if (view != null && lp != null && bottom > 0 && !isReserveExempt(view)) {
                    val reserve = resolveReservePx(loader, "setLayoutParams sink")
                    if (reserve != null && bottom >= reserve) {
                        marginSinkHits++
                        if (marginSinkHits == 1) {
                            facade.log(
                                android.util.Log.INFO,
                                "setLayoutParams sink: first reserve margin zeroed (${describeView(view)} mb=$bottom)",
                            )
                        }
                        lp.bottomMargin = 0
                        runCatching { view.layoutParams = lp }
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setLayoutParams: bottom reserve margin → 0 (threshold lazy)")
        }.onFailure { facade.log(android.util.Log.WARN, "hook View.setLayoutParams sink failed", it) }
        // 信息流尾部 footer（168px,vivospace_list_footer_place_holder 布局占位）是首页
        // 触摸/滚动拦截协议（onInterceptTouchEvent 的 B()/G() 状态机）的承重墙——v2.2 归零
        // 导致触摸拦截死区、内层列表有内容也滚不动,已回滚为原生高度。**禁止再对其归零。**
        // 末端的可见代价：信息流滚到最底时,玻璃栏周围可见约 168px 页面背景带（原生形态由
        // 不透明底栏遮盖,悬浮玻璃盖不住周围）——结构性代价,用户已确认接受。
    }

    // ③ 选购页差异化：classify.i.c(int) 一值两写（左栏 ClassifyTabLayout.bottomMargin =
    //    可用性预留 + 右侧 ViewPager2.bottomMargin = 延伸）。原方法执行后仅把右侧 margin
    //    归零——左栏保留预留（末项可联动滚到栏上方、不被遮挡），右侧延伸全屏。
    //    回调细分明日志（field/view/viewpager 任一环节失配可见），fragment view 未就绪
    //    时 1.5s 后重试一次；常驻清扫（classify_vertical_viewpager 兜底）双保险。
    private fun installClassifyHook(facade: HookFacade, loader: ClassLoader) {
        runCatching {
            val vpId = resId(loader, "R\$id", "classify_vertical_viewpager")
            val classifyC = Class.forName("com.vivo.space.shop.classify.i", true, loader)
                .getDeclaredMethod("c", Int::class.javaPrimitiveType)
            val fragmentClass = Class.forName("com.vivo.space.shop.classify.ClassifyFragment", true, loader)
            var notReadyLogged = false
            facade.around(classifyC, null, { call ->
                val listener = call.thisObj
                if (listener != null) {
                    runCatching {
                        val fragment = listener.javaClass.declaredFields
                            .firstOrNull { it.type == fragmentClass }
                            ?.apply { isAccessible = true }
                            ?.get(listener) ?: run {
                            if (!classifyFragmentFieldWarned) {
                                classifyFragmentFieldWarned = true
                                facade.log(android.util.Log.WARN, "classify.i.c: fragment field not found (锚点失配?)")
                            }
                            return@runCatching
                        }
                        val view = fragment.javaClass.getMethod("getView").invoke(fragment) as? View
                        if (view == null) {
                            // fragment view 未创建（注册早于视图）：延时重试一次
                            if (!notReadyLogged) {
                                notReadyLogged = true
                                facade.log(android.util.Log.INFO, "classify.i.c: fragment view not ready, retry in 1.5s")
                            }
                            mainHandler.postDelayed({
                                runCatching { zeroClassifyRightMargin(fragment, vpId, facade) }
                                    .onFailure { facade.log(android.util.Log.WARN, "classify.i.c retry failed", it) }
                            }, 1_500)
                            return@runCatching
                        }
                        zeroClassifyRightMargin(fragment, vpId, facade)
                    }.onFailure { facade.log(android.util.Log.WARN, "classify.i.c callback failed", it) }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked classify.i.c: viewpager extends, tab strip reserved")
        }.onFailure { facade.log(android.util.Log.WARN, "hook classify.i.c failed (app updated?)", it) }
    }

    /** 归零选购页右侧 ViewPager2（classify_vertical_viewpager）的 bottomMargin；左栏
     *  ClassifyTabLayout 预留不动。返回是否实际归零。 */
    private fun zeroClassifyRightMargin(fragment: Any, vpId: Int, facade: HookFacade): Boolean {
        if (vpId == 0) {
            if (!classifyVpMissingWarned) {
                classifyVpMissingWarned = true
                facade.log(android.util.Log.WARN, "classify.i.c: classify_vertical_viewpager id=0 (R\$id 解析失败，需更新 §4.3 锚点)")
            }
            return false
        }
        val view = fragment.javaClass.getMethod("getView").invoke(fragment) as? View ?: return false
        val vp = view.findViewById<View>(vpId) ?: run {
            if (!classifyVpMissingWarned) {
                classifyVpMissingWarned = true
                facade.log(android.util.Log.WARN, "classify.i.c: viewpager not found in fragment view (锚点失配?)")
            }
            return false
        }
        val lp = vp.layoutParams as? ViewGroup.MarginLayoutParams ?: return false
        if (lp.bottomMargin == 0) return false
        lp.bottomMargin = 0
        vp.layoutParams = lp
        facade.log(android.util.Log.INFO, "classify.i.c: viewpager bottomMargin → 0 (right pane extends)")
        return true
    }

    /** 预留清零的豁免视图：选购左栏 ClassifyTabLayout（预留是可用性设计，用户决策保留）、
     *  开屏全屏弹窗子树（popup 自身布局不动）、原生底栏子树（镜像数据源，自身 inset 维护
     *  不可被清）、模块玻璃 overlay（ComposeView）子树。 */
    private fun isReserveExempt(v: View): Boolean {
        if (v.javaClass.simpleName == CLASSIFY_TAB_STRIP_SIMPLE_NAME) return true
        popupRef?.get()?.let { if (it === v || it.isAncestorOf(v)) return true }
        tabBarRef?.get()?.let { if (it === v || it.isAncestorOf(v)) return true }
        var node: Any? = v
        var depth = 0
        while (node is View && depth < 8) {
            if (node.javaClass.name.contains("ComposeView")) return true
            node = node.parent
            depth++
        }
        return false
    }

    private fun describeView(v: View): String {
        val idName = if (v.id != 0 && v.id != -1) {
            runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
                ?: "0x${Integer.toHexString(v.id)}"
        } else "no-id"
        return "${v.javaClass.name}($idName)"
    }

    /** 底栏预留阈值像素（惰性）：优先反射应用自身的 `qq.d.f(Context)`——SDK/边到边感知
     *  （Android 15+ 全屏设备为 dp50，其余 dp66），硬编码 dp66 会漏掉 dp50 级留白（精选页
     *  回顶态留白复现的根因之一）；失败回退 R$dimen.dp66 字段反射。上下文用缓存的
     *  Activity 或 ActivityThread.currentApplication（任意线程可用，不依赖应用的
     *  Application 访问器——后台线程调用曾失败静默归零，§4.7 教训 4b）。失败返回 null
     *  = 收口放行原值。 */
    private fun resolveReservePx(loader: ClassLoader?, tag: String): Int? {
        reservePxCache.takeIf { it > 0 }?.let { return it }
        val context = appContextRef?.get() ?: currentApplication()
        if (loader != null && context != null) {
            val viaApp = runCatching {
                val f = Class.forName("qq.d", true, loader)
                    .getDeclaredMethod("f", Context::class.java)
                f.isAccessible = true
                (f.invoke(null, context) as? Int) ?: 0
            }.getOrDefault(0)
            if (viaApp > 0) {
                reservePxCache = viaApp
                android.util.Log.println(
                    android.util.Log.INFO, "LiquidGlass",
                    "$tag: reserve base (qq.d.f) = ${viaApp}px",
                )
                return viaApp
            }
        }
        val id = runCatching {
            Class.forName("com.vivo.space.R\$dimen", true, loader).getField("dp66").getInt(null)
        }.getOrElse {
            android.util.Log.println(android.util.Log.WARN, "LiquidGlass", "$tag: R\$dimen.dp66 resolve failed: $it")
            return null
        }
        if (context == null) {
            android.util.Log.println(android.util.Log.WARN, "LiquidGlass", "$tag: no context available (threshold inactive)")
            return null
        }
        val px = runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0)
        if (px <= 0) {
            android.util.Log.println(android.util.Log.WARN, "LiquidGlass", "$tag: dp66 px=0 (threshold inactive)")
            return null
        }
        reservePxCache = px
        android.util.Log.println(android.util.Log.INFO, "LiquidGlass", "$tag: reserve threshold (dp66 fallback) = ${px}px")
        return px
    }

    @Volatile
    private var reservePxCache: Int = 0

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        appContextRef = WeakReference(activity)
        tabBarRef = WeakReference(session.tabContainer)
        // 背景采样去重（v2.2，与小红书/高德/相册对齐，模块配置 lightweightSampling 可
        // 远程关）：三个玻璃节点每帧各自对 appRoot 全树 draw → 1× RenderNode 录制 +
        // 三节点回放（信息流/资讯页重内容滚动卡顿主源）。
        session.dedupeBackdropSampling = config().lightweightSampling
        // 冷启动全屏弹窗（popup_container）pending：overlay 挂载即隐藏,弹窗关闭后出现
        // （判定与同步逻辑见 syncGlassOverlayWithPopup,监视器每拍持续驱动）
        splashPending = true
        splashSeenVisible = false
        splashPendingStartMs = android.os.SystemClock.elapsedRealtime()
        runCatching { syncGlassOverlayWithPopup(activity) }
            .onFailure { facade.log(android.util.Log.WARN, "popup gate sync failed", it) }
        // 图标帧预览（拖动悬停 = 选中动画末帧；被拖离的原 tab = 应用自身反选落定态，
        // 见下方 previewIconUnselected 注入）：条目 ImageView 平时挂
        // 静态图，各 tab 的选中动画 LottieDrawable 存在底栏内部 item 状态表里，须经反射
        // 取出交给引擎临时挂载驱动。混淆锚点（13.3.0.1 与 13.6.0.1 同名）：VBottomNavigationView 字段
        // "B" = HashMap<条目, 状态>（声明在父类，需沿超类链查找）；状态对象 Lottie 字段
        // 按类型扫描（jadx 显示的 f12699c 是改名，真名 "c"，按名字查必失败）。进度方法
        // 运行时真名 "H(float)"（@FloatRange(0..1)；无 setProgress，R8 连 Lottie 库方法
        // 一起混淆；同类 L(float) 是 setSpeed，签名猜测会踩坑）。任一失效 →
        // runCatching 兜底返回 null/空操作 → 预览退化为无图标帧（不崩溃），见 §4.3。
        session.previewIconLottie = { index ->
            runCatching { resolveTabLottie(session.tabContainer, index) }.getOrNull()
        }
        session.previewIconProgress = { lottie, progress ->
            runCatching { driveTabFrame(lottie, progress) }
                .onFailure { facade.log(android.util.Log.WARN, "preview frame drive failed", it) }
        }
        // 反选还原（液泡拖离原选中 tab）：应用自身 w(item) 原生语义换回未选中静态图。
        // 该应用选中动画首帧仍带选中观感（帧驱动"首帧=未选中"假设不成立，实测翻车），
        // 必须走应用反选落定通道；钩子失败由引擎回退首帧（不崩溃）。
        session.previewIconUnselected = { index ->
            runCatching { unselectTabIcon(session.tabContainer, index) }.getOrDefault(false)
        }
        // 常驻清扫已迁入进程级生命周期监视器（registerLifecycleSpy/startMonitor）:
        // v1.7 真机取证发现会话级调度链路的日志会被宿主冷启动日志风暴期的 logd 限流整段
        // 丢弃,进程级 hook 是已验证可靠的执行层。
    }

    /** 清扫 content 树（单轮，返回修复数）：①任意视图的底栏级底部 padding/bottomMargin
     *  （≥ 阈值）归零（豁免 isReserveExempt：ClassifyTabLayout 左栏预留/广告/底栏/overlay
     *  子树）；②选购页右侧 classify_vertical_viewpager 的 bottomMargin 无条件归零（③ 的
     *  兜底，只动 bottom、top 状态栏预留不动）。 */
    private fun sweepBottomReserve(activity: Activity): Int {
        val loader = appClassLoader
        val reserve = resolveReservePx(loader, "sweep") ?: return 0
        val content = activity.findViewById<View>(android.R.id.content) ?: return 0
        val vpId = resId(loader, "R\$id", "classify_vertical_viewpager", activity)
        var fixed = 0
        fun walk(v: View?) {
            if (v == null) return
            if (!isReserveExempt(v)) {
                if (v.paddingBottom >= reserve) {
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                    fixed++
                }
                val lp = v.layoutParams
                if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin >= reserve) {
                    lp.bottomMargin = 0
                    v.layoutParams = lp
                    fixed++
                }
            }
            if (vpId != 0 && v.id == vpId) {
                val lp = v.layoutParams
                if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin != 0) {
                    lp.bottomMargin = 0
                    v.layoutParams = lp
                    fixed++
                }
            }
            (v as? ViewGroup)?.let { group -> for (i in 0 until group.childCount) walk(group.getChildAt(i)) }
        }
        walk(content)
        return fixed
    }

    /** 在底栏视图树中定位 originui menuLayout（tab 条目稳定父容器，见 tabViewsOf）。 */
    private fun findMenuLayout(bar: View): ViewGroup? {
        if (bar is ViewGroup && bar.javaClass.simpleName == MENU_LAYOUT_SIMPLE_NAME) return bar
        (bar as? ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                findMenuLayout(group.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** 反射取指定下标 tab 的选中动画 LottieDrawable（见 onSessionCreated 注释）：字段
     *  "B" 沿超类链查找；Lottie 字段按类型扫描（真名 "c"，jadx 显示名 f12699c 不可用）。 */
    private fun resolveTabLottie(bar: View, index: Int): Any? {
        val item = findMenuLayout(bar)?.getChildAt(index) ?: return null
        val mapField = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls -> runCatching { cls.getDeclaredField("B") }.getOrNull() }
            ?: return null
        mapField.isAccessible = true
        val stateMap = mapField.get(bar) as? Map<*, *> ?: return null
        val state = stateMap[item] ?: return null
        return state.javaClass.declaredFields
            .firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.type.name == "com.airbnb.lottie.LottieDrawable"
            }
            ?.apply { isAccessible = true }
            ?.get(state)
    }

    /** 缓存解析出的进度方法。真名 "H(float)"（@FloatRange(0..1)，内部换算帧并触发重绘；
     *  应用选择跳帧调用 H(1.0f)/H(0.0f)）。**不可用"唯一 void(float) 方法"签名兜底**：
     *  同类还有 L(float)=setSpeed——按签名猜会选中它，设 speed 而非进度（悬停看似跳帧、
     *  还原把速度归零，全部失效），已踩坑（§4.7 教训 2）。 */
    private var tabFrameSetter: java.lang.reflect.Method? = null
    private var appClassLoader: ClassLoader? = null

    /** 拖离原选中 tab 的反选还原 = 调应用自身 `VBottomNavigationView.w(item)`（jadx
     *  13.6.0.1 方法体实证：y(false) + 动画播放中则停 + 状态表未选中静态图 res 非零经
     *  条目 z(res) 换回静态图、为零才回退 lottie 首帧）。单锚点 = 方法真名 "w"
     *  （protected，声明在父类 VBottomNavigationView——查找必须沿超类链，同字段 "B"
     *  的教训），参数类型取条目运行时类（不硬编码混淆短名）。失效返回 false → 引擎
     *  回退动画首帧（不崩溃）。 */
    private fun unselectTabIcon(bar: View, index: Int): Boolean {
        val item = findMenuLayout(bar)?.getChildAt(index) ?: return false
        val w = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                runCatching { cls.getDeclaredMethod("w", item.javaClass) }.getOrNull()
            } ?: return false
        w.isAccessible = true
        w.invoke(bar, item)
        return true
    }

    /** 驱动 tab 选中动画到指定帧（0=首帧未选中，1=末帧选中）。 */
    private fun driveTabFrame(lottie: Any, progress: Float) {
        val setter = tabFrameSetter ?: run {
            runCatching { lottie.javaClass.getMethod("H", Float::class.javaPrimitiveType) }
                .getOrNull()
                ?.apply { isAccessible = true }
                ?.also { tabFrameSetter = it }
        } ?: return
        setter.invoke(lottie, progress)
    }
}
