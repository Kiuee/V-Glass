package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
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
 * vivo 官网（com.vivo.space，应用商店名"vivo 官网"/桌面名"我的vivo"视 ROM 而定）适配器。
 *
 * - 底栏：`...originui.nav.SpaceVBottomNavigationView`（13.7.0.7）/ 旧
 *   `...originui.SpaceVBottomNavigationView`（13.6.0.1），extends
 *   com.originui.widget.navigation.VBottomNavigationView，挂在主布局 vivospace_main 的
 *   `R.id.v_bottom_view` 上；13.7.0.7 外层新增 `SpaceVBottomBarContainer`（id
 *   `v_bottom_view_container`，AIOS 导航条 padding 维护者）。
 * - 每 tab 视图：内部 menuLayout（com.originui.widget.navigation.VMenuViewLayout）的
 *   直接子项。子项具体类是混淆类（13.7.0.7=`s3.b`，13.6.0.1=`j3.b`，FrameLayout 实现
 *   VNavigationBarItemViewInternal），**不能按类名锁定**，按"menuLayout 直接子项 +
 *   含 icon/labels 元素 id"结构识别；元素 id 沿用 originui 库公共命名
 *   （vivo_navigation_bar_item_icon_view / _labels_group），与相册同库同 id。
 * - 选中态：标准 View.isSelected（VMenuViewLayout 点击切换 + setSelected）→
 *   选中同步/点击转发与商店/相册同构。翻转方法候选 O(boolean)（13.7.0.7）/ y(boolean)
 *   （13.6.0.1），经 itemCheckedApplier 运行时解析（旧版 O(I) 是别义方法）。
 * - 图标镜像：WHOLE_TAB 元素级直绘（LottieDrawable 由应用侧 setImage 到图标
 *   ImageView，元素级直绘天然跟随，无需 LOTTIE_RESOLVE）。
 * - 开屏全屏弹窗：用户实测的"开屏广告"真身 = NewProductPopupView（`R.id.popup_container`，
 *   全屏视频弹窗，位于 vivospace_main/appRoot 内部、玻璃 overlay 之下）。进程级监视器
 *   跟踪其 visibility：展示期间玻璃 overlay 置 GONE、关闭后恢复；冷启动会话 overlay 先
 *   隐藏（pending 1.5s），弹窗未出现则视为无弹窗直接显示。
 * - 内容延伸（页面底部留白清零）：13.7.0.7 页面经 `nr.b.c` 注册高度监听，回调把
 *   `max(b0 - raise, 0)` 写进 VViewUtils/`ViewPager2.bottomMargin`；阈值取
 *   `bar.height + container.C() - raise`。13.6.0.1 保留 qq.d 分发 + qq.d.f 阈值。
 *   收口 = VViewUtils/View.setPadding/View.setLayoutParams（豁免 ClassifyTabLayout/
 *   ClassifyTabContainer、开屏弹窗、底栏与外层容器、overlay）+ 常驻清扫。信息流尾部
 *   footer（168px 占位）仅 13.6.0.1 存在，钳制为非零 token（4px）以保留触摸/滚动拦截
 *   协议；13.7.0.7 已移除该占位与 r(View) 落地点。
 */
object VivoSpaceAdapter : AppAdapter {

    override val targetPackage: String = "com.vivo.space"
    override val displayName: String = "vivo 官网"
    override val tabActivityClassName: String? = "com.vivo.space.ui.VivoSpaceTabActivity"
    // 兼容窗口（主册 §0.6.3）= 13.6.0.1、13.7.0.7：13.7.0.7 底栏包路径/条目短名/选中
    // 翻转/反选/帧驱动/内容留白机制均漂移（候选链兼容两版）；13.3.0.1 已超窗淘汰。
    override val baselineVersions: List<String> = listOf("13.6.0.1", "13.7.0.7")

    // 底栏类：13.7.0.7 迁移到 ...originui.nav，13.6.0.1 为旧路径；simpleName 相同可兜底。
    private val BAR_CLASS_CANDIDATES = listOf(
        "com.vivo.space.lib.widget.originui.nav.SpaceVBottomNavigationView",
        "com.vivo.space.lib.widget.originui.SpaceVBottomNavigationView",
    )
    private const val BAR_SIMPLE_NAME = "SpaceVBottomNavigationView"
    // 13.7.0.7 新增底栏容器（AIOS 导航条 padding 维护者；旧版无此层）。
    private const val BAR_CONTAINER_CLASS =
        "com.vivo.space.lib.widget.originui.nav.SpaceVBottomBarContainer"
    private const val BAR_CONTAINER_SIMPLE_NAME = "SpaceVBottomBarContainer"
    private const val BAR_CONTAINER_ID_NAME = "v_bottom_view_container"
    // 底栏在主布局中的资源 id（类名失配时的结构兜底）
    private const val BAR_ID_NAME = "v_bottom_view"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"
    // 选购页左栏（预留是可用性设计：清零后末项被玻璃栏遮挡、不可滚动到达——用户决策保留）。
    // 13.7.0.7 把预留改写在 ClassifyTabContainer 上，豁免需同时覆盖容器及其祖先。
    private const val CLASSIFY_TAB_STRIP_SIMPLE_NAME = "ClassifyTabLayout"
    private const val CLASSIFY_TAB_CONTAINER_SIMPLE_NAME = "ClassifyTabContainer"
    // 开屏全屏弹窗（NewProductPopupView 的容器,用户实测的"开屏广告"真身）
    private const val POPUP_ID_NAME = "popup_container"
    // 冷启动 pending：弹窗未出现超过此时长即视为无弹窗,玻璃 overlay 直接显示
    private const val POPUP_PENDING_MS = 1_500L
    // 精选页信息流尾部 footer 占位（vivospace_list_footer_place_holder,高恰为 dp56）钳制
    // 目标 token 高度（px）。**非零**是刻意的：footer 是父列表滚动区间与触摸/滚动拦截
    // 协议（NestedParentRecyclerView.B()/G() 状态机）的承重墙,v2.2 归零致死区已回滚
    // （教训 7）；协议依赖 canScrollVertically 的翻转而非 168px 本身,4px 保留语义、
    // 消除视觉带。**13.7.0.7 已移除该占位布局与 r(View) 落地点**，本机制只对
    // 13.6.0.1 安装（见 installAppHooks 的旧版分支）。
    private const val FEED_FOOTER_TOKEN_PX = 4

    // —— 混淆锚点候选链（新名优先、旧名回退，逐个精确签名匹配；见主册 §0.6.2 SOP-D） ——
    /** 条目选中外观翻转：13.7.0.7=`O(Z)`，13.6.0.1=`y(Z)`；旧版 `O(I)` 是别义方法，
     *  必须按 boolean 精确签名过滤，不能按名裸取。 */
    private val CHECKED_SETTER_CANDIDATES = listOf("O", "y")
    /** 反选落定：13.7.0.7=`VBottomNavigationView.E0(item)`，13.6.0.1=`w(item)`。 */
    private val UNSELECT_METHOD_CANDIDATES = listOf("E0", "w")
    /** Lottie 进度直驱：13.7.0.7=`J(F)`，13.6.0.1=`H(F)`；新版 `N(F)` 是 setSpeed。 */
    private val FRAME_METHOD_CANDIDATES = listOf("J", "H")
    /** 条目状态表：13.7.0.7=`k0:HashMap`，13.6.0.1=`B:HashMap`；旧版 `k0` 是 int。 */
    private val STATE_MAP_FIELD_CANDIDATES = listOf("k0", "B")

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
    /** 13.7.0.7 底栏外层容器（AIOS 导航条 padding 维护者；旧版为 null）。 */
    @Volatile private var barContainerRef: WeakReference<View>? = null
    /** 13.7.0.7 新底栏布局（高度提供器 + 高度监听内容留白）；旧版=false。 */
    @Volatile private var newBottomBarLayout = false
    /** 应用上下文缓存（内容留白阈值解析用；onTabActivityResumed/onSessionCreated 主线程刷新）。 */
    @Volatile private var appContextRef: WeakReference<Context>? = null
    @Volatile private var classifyFragmentFieldWarned = false
    @Volatile private var classifyVpMissingWarned = false
    /** 精选页信息流尾部 footer 占位（r(View) 落地时记录；清扫逐拍重钳制回顶态重设）。 */
    @Volatile private var feedFooterRef: WeakReference<View>? = null
    @Volatile private var sweepDiagLogged = false
    /** 选中翻转方法缓存（候选 O/y；条目类在单进程内唯一）。 */
    @Volatile private var checkedSetterClass: Class<*>? = null
    @Volatile private var checkedSetterMethod: java.lang.reflect.Method? = null
    @Volatile private var stateMapFieldLogged = false
    @Volatile private var unselectLogged = false
    @Volatile private var selectedIconLogged = false
    /** 会员页 WebView（q3 记录；清扫逐拍强制底部 padding 0，防应用重设）。 */
    @Volatile private var memberWebViewRef: WeakReference<View>? = null

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
        v.javaClass.name in BAR_CLASS_CANDIDATES || v.javaClass.simpleName == BAR_SIMPLE_NAME

    private fun idByName(v: View, name: String): Int =
        v.resources.getIdentifier(name, "id", v.context.packageName)

    /** 记录底栏外层容器（13.7.0.7 新增；旧版无。用于阈值 bar.height+C() 与豁免）。 */
    private fun rememberBarContainer(bar: View) {
        val parent = bar.parent as? View ?: return
        if (parent.javaClass.simpleName == BAR_CONTAINER_SIMPLE_NAME ||
            parent.id == idByName(bar, BAR_CONTAINER_ID_NAME)
        ) {
            barContainerRef = WeakReference(parent)
        }
    }

    /** 从 Activity 按资源 id 记录底栏外层容器（会话装配前的早期兜底）。 */
    private fun rememberBarContainer(activity: Activity) {
        val id = resId(appClassLoader, "R\$id", BAR_CONTAINER_ID_NAME, activity)
        if (id != 0) {
            activity.findViewById<View>(id)?.let { barContainerRef = WeakReference(it) }
        }
    }

    /**
     * 条目选中外观翻转方法：候选 `O`（13.7.0.7）→ `y`（13.6.0.1），按 boolean 精确签名
     * 匹配（旧版 `O(I)` 是别义方法）。命中后按条目类缓存；全部失败返回 null = 预览静默
     * 降级（applier 内已做同态去重，不反复重启动画）。
     */
    private fun resolveCheckedSetter(cls: Class<*>): java.lang.reflect.Method? {
        if (checkedSetterClass === cls) return checkedSetterMethod
        val method = CHECKED_SETTER_CANDIDATES.firstNotNullOfOrNull { name ->
            runCatching { cls.getMethod(name, Boolean::class.javaPrimitiveType) }.getOrNull()
        }
        checkedSetterClass = cls
        checkedSetterMethod = method
        if (method != null) {
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "vivo space: checked setter = ${method.name} (bool)",
            )
        }
        return method
    }

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS_CANDIDATES.first(),
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐商店）
        elementIds = mapOf(
            "icon" to ICON_ID_NAME,
            "labels" to LABELS_ID_NAME,
        ),
        // 条目选中外观翻转：13.7.0.7=`O(Z)`、13.6.0.1=`y(Z)`，单名 descriptor 无法跨版本，
        // 改走 applier 运行时候选链；applier 内复刻引擎同态去重（isSelected 已等于目标态
        // 则跳过），避免深色悬停反复重启动画（主册 §0.5 B.5 / 2.9.47 闪烁修复）。
        itemCheckedApplier = { view, checked ->
            if (view.isSelected != checked) {
                runCatching { resolveCheckedSetter(view.javaClass)?.invoke(view, checked) }
            }
        },
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
        if (found.isNotEmpty()) return found.first().also { rememberBarContainer(it) }
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
            return walkById(content)?.also { rememberBarContainer(it) }
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
        rememberBarContainer(activity)
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
            rememberBarContainer(activity)
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
                    } else {
                        // 真机 2026-11-02：静默放弃曾让监视器整体哑火且无迹可查（会话在跑、
                        // gate/sweep 全无日志）——放弃必须留痕；会话级保底见 onSessionCreated
                        android.util.Log.println(
                            android.util.Log.WARN, "LiquidGlass",
                            "lifecycle spy: no application after retries, spy dead",
                        )
                    }
                    return
                }
                runCatching {
                    app.registerActivityLifecycleCallbacks(lifecycleSpy)
                    android.util.Log.println(
                        android.util.Log.INFO, "LiquidGlass",
                        "lifecycle spy registered",
                    )
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
        // 冷启动早扫：会话建立时底栏高度/新内容留白可能尚未就绪，阈值暂时不可用；
        // 在 500ms 常驻节拍前补几次短延迟清扫，把选购页等首帧留白压到 ~1s 内（用户实测
        // 2s 节拍有可感延迟）。
        scheduleColdStartSweeps(activity)
    }

    private fun scheduleColdStartSweeps(activity: Activity) {
        if (configProvider?.invoke()?.contentExtend == ModuleConfig.CONTENT_EXTEND_OFF) return
        for (delay in longArrayOf(120L, 320L, 720L, 1_200L)) {
            mainHandler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    runCatching { sweepBottomReserve(activity) }
                }
            }, delay)
        }
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
        if (extendOn) {
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
    // 13.7.0.7 机制替换：页面经 nr.b.c(rootView, listener) 注册，底栏高度由
    // VivoSpaceTabActivity.b0()（= bar.height + container.C()）提供，监听回调把
    // max(height - raise, 0) 写进 VViewUtils.setPaddingBottom / ViewPager2.bottomMargin；
    // raise = io.reactivex.internal.util.b.f(Context,false)（导航条抬升高度）。
    // 阈值 = bar.height + container.C() - raise。
    // 旧版 13.6.0.1 仍走 qq.d 分发（b 全量 / e 余量）+ qq.d.f 阈值；两套候选链并存，
    // 以底栏外层容器类是否存在判定（不是 versionName 字符串）。
    // 选购页左栏预留 13.7.0.7 改写在 ClassifyTabContainer 上，豁免需覆盖容器及祖先
    // （见 isReserveExempt）；精选页 footer 占位 13.7.0.7 已移除，只对旧版安装。
    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        appClassLoader = loader
        configProvider = config
        newBottomBarLayout = runCatching { Class.forName(BAR_CONTAINER_CLASS, false, loader) }.isSuccess
        // 进程级生命周期监视器:开屏弹窗门控/常驻清扫的可靠执行层,
        // 不受 contentExtend 开关影响（弹窗门控是玻璃栏自身行为,与内容延伸无关）
        registerLifecycleSpy(facade)
        if (config().contentExtend == ModuleConfig.CONTENT_EXTEND_OFF) {
            facade.log(android.util.Log.INFO, "contentExtend=off: page bottom insets untouched")
            return
        }
        if (newBottomBarLayout) {
            facade.log(
                android.util.Log.INFO,
                "new bottom bar layout (height-provider): legacy dispatch/classify/footer hooks skipped",
            )
        } else {
            installLegacyDispatchHooks(facade, loader)
        }
        // ④ 实时收口（WebView 等容器）：页面状态变化会重新 setPaddingBottom/setMarginBottom
        //    塞入底栏级留白，值 ≥ 阈值 → 归零。阈值惰性解析（安装发生在 LSPosed 后台
        //    线程，§4.7 教训 4b），**无条件安装**，解析失败放行原值并 WARN 一次。
        //    **必须带 isReserveExempt**：13.7.0.7 的选购左栏预留改由 VViewUtils 写在
        //    ClassifyTabContainer 上，旧实现会把它一并清零（左栏末项被遮挡）。
        runCatching {
            val utils = Class.forName("com.originui.core.utils.VViewUtils", true, loader)
            val setPad = utils.getDeclaredMethod("setPaddingBottom", View::class.java, Int::class.javaPrimitiveType)
            val setMar = utils.getDeclaredMethod("setMarginBottom", View::class.java, Int::class.javaPrimitiveType)
            var vviewHit = false
            facade.around(setPad, null, { call ->
                val view = call.args.getOrNull(0) as? View
                val original = (call.args.getOrNull(1) as? Number)?.toInt() ?: 0
                if (view != null && original > 0 && !isReserveExempt(view)) {
                    val reserve = resolveReservePx(loader, "VViewUtils setPaddingBottom")
                    if (reserve != null && original >= reserve) {
                        if (!vviewHit) {
                            vviewHit = true
                            facade.log(
                                android.util.Log.INFO,
                                "VViewUtils setPaddingBottom: first reserve padding zeroed (${describeView(view)} pb=$original)",
                            )
                        }
                        runCatching { setPad.invoke(null, view, 0) }
                    }
                }
                null
            })
            facade.around(setMar, null, { call ->
                val view = call.args.getOrNull(0) as? View
                val original = (call.args.getOrNull(1) as? Number)?.toInt() ?: 0
                if (view != null && original > 0 && !isReserveExempt(view)) {
                    val reserve = resolveReservePx(loader, "VViewUtils setMarginBottom")
                    if (reserve != null && original >= reserve) {
                        runCatching { setMar.invoke(null, view, 0) }
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked VViewUtils setPaddingBottom/setMarginBottom (reserve threshold lazy, exempt)")
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
        // ⑦ 精选页信息流尾部 footer 占位钳制（仅 13.6.0.1）。13.7.0.7 已移除
        //    `PrimaryRecyclerView.r(View)`（现 `r()` 是 position getter）与
        //    `vivospace_list_footer_place_holder` 占位布局，故跳过；不做结构猜测。
        //    旧版细节：箭头（回顶态）出现时机 = NestedParentRecyclerView.B()
        //    （isScrollEnd）首次翻转 → activity C1() 换 tab 0 图标为 tab_back_top，
        //    168px 占位即留白源；v2.2 归零破坏触摸/滚动拦截协议已回滚（教训 7），
        //    本方案钳为非零 token（FEED_FOOTER_TOKEN_PX），清扫逐拍压制回顶态重设。
        if (!newBottomBarLayout) {
            runCatching {
                val rView = Class.forName(
                    "com.vivo.space.widget.recyclerview.PrimaryRecyclerView", true, loader,
                ).getDeclaredMethod("r", View::class.java)
                var footerHitLogged = false
                facade.around(rView, null, { call ->
                    val list = call.thisObj as? View
                    val footer = call.args.getOrNull(0) as? View
                    if (list != null && footer != null && isFeedFooterPlaceholder(list, footer)) {
                        clampFeedFooter(footer)
                        if (!footerHitLogged) {
                            footerHitLogged = true
                            facade.log(
                                android.util.Log.INFO,
                                "footer clamp: feed placeholder (${footer.layoutParams?.height}px) → token",
                            )
                        }
                    }
                    null
                })
                facade.log(android.util.Log.INFO, "hooked PrimaryRecyclerView.r: feed footer placeholder clamp")
            }.onFailure { facade.log(android.util.Log.WARN, "hook PrimaryRecyclerView.r failed (app updated?)", it) }
        } else {
            facade.log(android.util.Log.INFO, "footer clamp skipped (new layout: legacy placeholder removed)")
        }
        if (newBottomBarLayout) installMemberWebViewHook(facade, loader)
    }

    /**
     * 会员页 `MemberFragment.q3()` 会把 `WebFragment.v`（HtmlWebView）的 paddingBottom
     * 写成 `raise`（导航条高度），与全量留白阈值擦肩而过；threshold 收口在真实机型上仍可能
     * 漏掉（q3 的调用时机/上下文差异）。这里直接 after 兜底把该 WebView 底部 padding 清零，
     * 保证会员页内容画进小白条区域；q3 不存在（升版改名）则 WARN 留痕，不崩溃。
     */
    private fun installMemberWebViewHook(facade: HookFacade, loader: ClassLoader) {
        runCatching {
            val member = Class.forName("com.vivo.space.ui.member.MemberFragment", true, loader)
            val q3 = member.getDeclaredMethod("q3").apply { isAccessible = true }
            var hit = false
            facade.around(q3, null, { call ->
                val fragment = call.thisObj
                val field = generateSequence(fragment?.javaClass) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .firstOrNull { it.type.name == "com.vivo.space.web.widget.HtmlWebView" }
                val web = field?.apply { isAccessible = true }?.get(fragment) as? View
                if (web != null) {
                    memberWebViewRef = WeakReference(web)
                    val fixed = zeroMemberBottomInsets(web)
                    if (fixed > 0 && !hit) {
                        hit = true
                        facade.log(
                            android.util.Log.INFO,
                            "member webview: bottom insets → 0 (${describeView(web)} fixed=$fixed)",
                        )
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked MemberFragment.q3: webview nav-bar padding → 0")
        }.onFailure { facade.log(android.util.Log.WARN, "hook MemberFragment.q3 failed (app updated?)", it) }
    }

    /** 会员页 WebView 及其祖先（WebViewContainer/根布局）的底部 padding/margin 清零；
     *  只对会员页 WebView 链路生效（q3 记录），不影响其它页面。返回修复数。 */
    private fun zeroMemberBottomInsets(web: View): Int {
        var fixed = 0
        var node: View? = web
        var depth = 0
        while (node != null && depth < 8) {
            if (node.paddingBottom != 0) {
                node.setPadding(node.paddingLeft, node.paddingTop, node.paddingRight, 0)
                fixed++
            }
            val lp = node.layoutParams
            if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                node.layoutParams = lp
                fixed++
            }
            node = node.parent as? View
            depth++
        }
        return fixed
    }

    /** footer 占位判据：高度恰为 dp56 的空 LinearLayout/ViewGroup + 调用列表在
     *  common_root 子树内（精选页信息流根视图,HomePageFragment.P）。任一不满足 =
     *  其它页面的真实 footer,放行原生高度。dp56/common_root 解析失败 = 放行（fail-open）。 */
    private fun isFeedFooterPlaceholder(list: View, footer: View): Boolean {
        if (footer !is ViewGroup || footer.childCount != 0) return false
        val lp = footer.layoutParams ?: return false
        val dp56 = dimenPx("dp56")
        if (dp56 <= 0 || lp.height != dp56) return false
        val rootId = resId(appClassLoader, "R\$id", "common_root")
        if (rootId == 0) return false
        var node: Any? = list
        var depth = 0
        while (node is View && depth < 12) {
            if (node.id == rootId) return true
            node = node.parent
            depth++
        }
        return false
    }

    /** 钳制 footer 占位高度并记录引用（清扫重钳制用）；已 ≤ token 时幂等。 */
    private fun clampFeedFooter(footer: View) {
        val lp = footer.layoutParams ?: return
        if (lp.height <= FEED_FOOTER_TOKEN_PX) return
        lp.height = FEED_FOOTER_TOKEN_PX
        footer.layoutParams = lp
        feedFooterRef = WeakReference(footer)
    }

    /** R$dimen 取像素（多源：主 R + lib R 字段反射 + getIdentifier 兜底，正结果缓存）。
     *  **多源是永久必要而非补丁**：R8 resource shrinker 会剥离代码未引用的 R 字段——
     *  真机 13.6.0.1 实证 `com.vivo.space.R$dimen.dp56` 被剥离（NoSuchFieldException）、
     *  `com.vivo.space.lib.R$dimen` 存活；清扫逐拍调用若不缓存则每拍两次反射异常。
     *  全部失败打一次性 WARN（防"静默归零禁用判据"复发无迹可查，教训 12）。失败返回
     *  0 = 调用方放行。 */
    private fun dimenPx(name: String, activity: Activity? = null): Int {
        dimenCache[name]?.let { return it }
        val context = appContextRef?.get() ?: currentApplication() ?: activity ?: return 0
        val loader = appClassLoader
        if (loader != null) {
            for (cls in listOf("com.vivo.space.R\$dimen", "com.vivo.space.lib.R\$dimen")) {
                val id = runCatching { Class.forName(cls, true, loader).getField(name).getInt(null) }
                    .getOrDefault(0)
                if (id != 0) {
                    val px = runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0)
                    if (px > 0) {
                        dimenCache[name] = px
                        return px
                    }
                }
            }
        }
        // getIdentifier 兜底（该应用 arsc 混淆改名,大概率返回 0,仅作最后尝试）
        val a = activity ?: (context as? Activity) ?: return 0
        val id2 = a.resources.getIdentifier(name, "dimen", a.packageName)
        if (id2 != 0) {
            val px = a.resources.getDimensionPixelSize(id2)
            if (px > 0) {
                dimenCache[name] = px
                return px
            }
        }
        if (!dimenWarnLogged) {
            dimenWarnLogged = true
            android.util.Log.println(
                android.util.Log.WARN, "LiquidGlass",
                "dimenPx: $name unresolved (all sources), dependent checks disabled",
            )
        }
        return 0
    }

    @Volatile private var dimenWarnLogged = false
    private val dimenCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /** 13.6.0.1 专用：旧 `qq.d` 全量/余量分发 + `classify.i.c` 选购 hook。
     *  13.7.0.7 已移除这些锚点，只安装 ④⑤⑥ 收口与清扫（见 installAppHooks 分支）。 */
    private fun installLegacyDispatchHooks(facade: HookFacade, loader: ClassLoader) {
        // ① 全量分发点 + ② 余量变体：公共依赖 qq.d$c。解析失败只降级 ①② 并 WARN，
        //    **不阻断** ④⑤⑥（此前 fq.d$c 失配会让全部内容延伸 hook 静默跳过——单点失效）。
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
            //    一并去除）。选购页监听器例外放行（见 installClassifyHook）。
            runCatching {
                val b = Class.forName("qq.d", true, loader)
                    .getDeclaredMethod("b", android.content.Context::class.java, listenerIface)
                facade.around(b, { call ->
                    val listener = call.args.getOrNull(1)
                    if (listener?.javaClass?.name == "com.vivo.space.shop.classify.i") {
                        return@around null // 放行：一值两写的左栏 margin 是可用性预留
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
    }

    /** 13.6.0.1 选购页差异化：`classify.i.c(int)` 一值两写（左栏预留 + 右栏 margin），
     *  after 仅归零右侧 `classify_vertical_viewpager` 的 bottomMargin，左栏 ClassifyTabLayout
     *  预留保留（末项可联动滚到栏上方、不被遮挡）；回调细分明日志，fragment view 未就绪
     *  1.5s 重试一次；清扫按 id 兜底。13.7.0.7 无此方法，仅旧版安装。 */
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

    /** 预留清零的豁免视图：选购左栏 ClassifyTabLayout/ClassifyTabContainer（预留是可用性
     *  设计，用户决策保留）、开屏弹窗子树、原生底栏与 13.7.0.7 外层容器子树（自身 inset
     *  维护不可被清）、模块玻璃 overlay（ComposeView）子树。**祖先遍历统一覆盖**：
     *  13.7.0.7 的左栏预留写在 ClassifyTabContainer 上，只查视图自身会误清。 */
    private fun isReserveExempt(v: View): Boolean {
        var node: View? = v
        var depth = 0
        while (node != null && depth < 12) {
            val simpleName = node.javaClass.simpleName
            if (simpleName == CLASSIFY_TAB_STRIP_SIMPLE_NAME ||
                simpleName == CLASSIFY_TAB_CONTAINER_SIMPLE_NAME ||
                simpleName == BAR_CONTAINER_SIMPLE_NAME
            ) return true
            if (node.javaClass.name.contains("ComposeView")) return true
            if (popupRef?.get() === node) return true
            if (tabBarRef?.get() === node) return true
            if (barContainerRef?.get() === node) return true
            node = node.parent as? View
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

    /** 底栏内容留白阈值（惰性，失败返回 null = 收口放行原值）。
     *  - 13.7.0.7：`bar.height + container.C() - raise`（与 `VivoSpaceTabActivity.b0()`
     *    的公式一致；raise = 导航条抬升高度，见 [resolveRaisePx]）。缓存键 = (total, raise)，
     *    AIOS/任务栏高度变化时监视器重算。
     *  - 13.6.0.1：`qq.d.f(Context)`（应用自身基准），失败回退 R$dimen dp66/dp50。
     *  **绝不以 0 作为阈值**（会清掉全部正 padding）。 */
    private fun resolveReservePx(loader: ClassLoader?, tag: String): Int? {
        // 旧版阈值固定，可永久缓存；新版 bar+C/raise 会随 AIOS/任务栏变化，必须每拍按
        // (total, raise) 键重算（见下方 reservePxKey）。
        if (!newBottomBarLayout) {
            reservePxCache.takeIf { it > 0 }?.let { return it }
        }
        val context = appContextRef?.get() ?: currentApplication()
        if (context != null) {
            if (newBottomBarLayout) {
                val total = currentBarTotalHeight()
                if (total != null && total > 0) {
                    val raise = resolveRaisePx(context, loader)
                    if (raise != null) {
                        val reserve = (total - raise).coerceAtLeast(0)
                        if (reserve > 0) {
                            val key = (total.toLong() shl 32) or (raise.toLong() and 0xffffffffL)
                            if (reservePxCache <= 0 || reservePxKey != key) {
                                reservePxCache = reserve
                                reservePxKey = key
                                android.util.Log.println(
                                    android.util.Log.INFO, "LiquidGlass",
                                    "$tag: reserve base (bar+C-raise) = ${reserve}px (bar+C=$total raise=$raise)",
                                )
                            }
                            return reserve
                        }
                        return null
                    }
                }
            } else if (loader != null) {
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
        }
        // dimen 兜底（不缓存：新布局 bar+C 就绪后必须能切换到精确值）
        for (name in listOf("dp66", "dp50")) {
            val px = dimenPx(name)
            if (px > 0) {
                if (!reserveFallbackLogged) {
                    reserveFallbackLogged = true
                    android.util.Log.println(
                        android.util.Log.INFO, "LiquidGlass",
                        "$tag: reserve threshold (dimen $name fallback) = ${px}px",
                    )
                }
                return px
            }
        }
        if (!reserveFallbackLogged) {
            reserveFallbackLogged = true
            android.util.Log.println(
                android.util.Log.WARN, "LiquidGlass",
                "$tag: content reserve unresolved (bar/qq.d/dimen all failed), dependent checks disabled",
            )
        }
        return null
    }

    /** 13.7.0.7 底栏总高 = bar.height + 外层容器 C()（与 VivoSpaceTabActivity.b0 同式）。
     *  任一环节未就绪返回 null（调用方放行，监视器后续重算）。 */
    private fun currentBarTotalHeight(): Int? {
        val bar = tabBarRef?.get() ?: return null
        val h = bar.height
        if (h <= 0) return null
        val container = (bar.parent as? View)
            ?.takeIf { it.javaClass.simpleName == BAR_CONTAINER_SIMPLE_NAME }
            ?: barContainerRef?.get()
        val c = container?.let { parent ->
            runCatching { parent.javaClass.getMethod("C").invoke(parent) as? Int }
                .getOrNull()
        } ?: 0
        return h + c.coerceAtLeast(0)
    }

    /** 13.7.0.7 导航条抬升高度：优先应用自身 `io.reactivex.internal.util.b.f(Context,false)`
     *  （页面监听同款），回退 `VNavigationBarUtils.getNavigationBarHeightV2`，再回退
     *  R$dimen.os_nav_height。返回 0 是合法值（多窗口/无抬升），仅全部解析失败才 null。 */
    private fun resolveRaisePx(context: Context, loader: ClassLoader?): Int? {
        if (loader != null) {
            val viaApp = runCatching {
                Class.forName("io.reactivex.internal.util.b", true, loader)
                    .getMethod("f", Context::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(null, context, false) as? Int
            }.getOrNull()
            if (viaApp != null) return viaApp.coerceAtLeast(0)
            val viaOrigin = runCatching {
                Class.forName("com.originui.core.utils.VNavigationBarUtils", true, loader)
                    .getMethod("getNavigationBarHeightV2", Context::class.java)
                    .invoke(null, context) as? Int
            }.getOrNull()
            if (viaOrigin != null && viaOrigin > 0) return viaOrigin
        }
        return dimenPx("os_nav_height").takeIf { it > 0 }
    }

    @Volatile
    private var reservePxCache: Int = 0
    @Volatile
    private var reservePxKey: Long = 0
    @Volatile
    private var reserveFallbackLogged = false

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        appContextRef = WeakReference(activity)
        tabBarRef = WeakReference(session.tabContainer)
        rememberBarContainer(session.tabContainer)
        // 会话级保底驱动监视器（幂等，startMonitor 同 activity 去重）：lifecycleSpy 依赖
        // Application 注册链路，真机 2026-11-02 曾整体哑火（渲染会话在跑而 gate/sweep
        // 全无日志）——onSessionCreated 本身在主线程且 activity 就绪，直接启动监视器，
        // 开屏弹窗门控/常驻清扫/footer 钳制不再单依赖 spy 的注册成败。
        startMonitor(activity)
        // 背景采样去重：由 TargetHook 会话创建期统一设置（引擎单管线，默认开；
        // lightweightSampling 远程可关）——1× RenderNode 录制 + 三节点回放
        // （信息流/资讯页重内容滚动卡顿主源，原 3× 全树 draw/帧）。
        // 冷启动全屏弹窗（popup_container）pending：overlay 挂载即隐藏,弹窗关闭后出现
        // （判定与同步逻辑见 syncGlassOverlayWithPopup,监视器每拍持续驱动）
        splashPending = true
        splashSeenVisible = false
        splashPendingStartMs = android.os.SystemClock.elapsedRealtime()
        runCatching { syncGlassOverlayWithPopup(activity) }
            .onFailure { facade.log(android.util.Log.WARN, "popup gate sync failed", it) }
        // 图标帧预览（拖动悬停 = 选中落定外观；被拖离的原 tab = 应用自身反选落定态，
        // 见下方 previewIconUnselected 注入）：条目 ImageView 平时挂
        // 静态图，选中动画/静态图存在底栏内部 item 状态表里，须经反射取出交给引擎临时
        // 挂载。13.7.0.7 的选中外观由应用 `k1(item)` 换图（状态 e drawable / b res / c
        // Lottie），单靠 `O(true)` 只改布局/选中 flag、不换图标；因此新版优先返回**选中
        // 静态图**（e/b），取不到才回退 Lottie。状态表字段候选 k0（13.7.0.7）/ B
        // （13.6.0.1），必须按 Map 类型过滤（旧版 k0 是 int）；进度方法候选 J(float)
        // （13.7.0.7）/ H(float)（13.6.0.1）——新版 N(float) 是 setSpeed，禁止签名猜。
        // 任一失效 → runCatching 兜底返回 null/空操作 → 预览退化为无图标帧（不崩溃）。
        session.previewIconLottie = { index ->
            val item = session.tabViews.getOrNull(index)
            if (item == null) null
            else runCatching { resolvePreviewIconDrawable(session.tabContainer, item) }.getOrNull()
        }
        session.previewIconProgress = { lottie, progress ->
            runCatching { driveTabFrame(lottie, progress) }
                .onFailure { facade.log(android.util.Log.WARN, "preview frame drive failed", it) }
        }
        // 反选还原（液泡拖离原选中 tab）：应用自身 E0(item)（13.7.0.7）/ w(item)
        // （13.6.0.1）原生语义换回未选中静态图。该应用选中动画首帧仍带选中观感
        // （帧驱动"首帧=未选中"假设不成立，实测翻车），必须走应用反选落定通道；
        // 钩子失败由引擎回退首帧（不崩溃）。
        session.previewIconUnselected = { index ->
            val item = session.tabViews.getOrNull(index)
            if (item == null) false
            else runCatching { unselectTabIcon(session.tabContainer, item) }.getOrDefault(false)
        }
        // 常驻清扫已迁入进程级生命周期监视器（registerLifecycleSpy/startMonitor）:
        // v1.7 真机取证发现会话级调度链路的日志会被宿主冷启动日志风暴期的 logd 限流整段
        // 丢弃,进程级 hook 是已验证可靠的执行层。
    }

    /** 清扫 content 树（单轮，返回修复数）：①任意视图的底栏级底部 padding/bottomMargin
     *  （≥ 阈值）归零（豁免 isReserveExempt：ClassifyTabLayout/ClassifyTabContainer 左栏
     *  预留、广告、底栏与 13.7.0.7 外层容器、overlay 子树）；②选购页右侧
     *  classify_vertical_viewpager 的 bottomMargin 无条件归零（新布局右栏延伸兜底，
     *  只动 bottom、top 状态栏预留不动）；③精选页 footer 占位重钳制（仅旧版有记录）。 */
    private fun sweepBottomReserve(activity: Activity): Int {
        val loader = appClassLoader
        // 全量阈值未就绪（bar 未测量）时不提前返回：viewpager 无条件归零与会员页 WebView
        // 兜底仍可执行，否则冷启动首帧会一直等到 bar 测完（用户实测选购页有可感延迟）。
        val regularReserve = resolveReservePx(loader, "sweep")
        val reserve = regularReserve ?: Int.MAX_VALUE
        val content = activity.findViewById<View>(android.R.id.content) ?: return 0
        val vpId = resId(loader, "R\$id", "classify_vertical_viewpager", activity)
        var fixed = 0
        // 会员页 WebView：q3 之后应用/WebView 内部可能重设底部 padding/margin，
        // 清扫逐拍对 WebView 链路强制 0。
        memberWebViewRef?.get()?.let { web -> fixed += zeroMemberBottomInsets(web) }
        // ③ footer 占位重钳制：回顶态（滚到末尾）应用会把占位重设回原生 168px
        feedFooterRef?.get()?.let { footer ->
            val dp56 = dimenPx("dp56", activity)
            if (dp56 > 0 && footer.layoutParams?.height ?: 0 >= dp56) {
                clampFeedFooter(footer)
                fixed++
            }
        }
        if (!sweepDiagLogged) {
            // 一次性输出判据解析值（防"解析归零静默禁用判据"复发无迹可查,教训 12）
            sweepDiagLogged = true
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "sweep first: dp56=${dimenPx("dp56", activity)} reserve=${regularReserve ?: -1}",
            )
        }
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

    /** 反射取条目状态对象（状态表字段候选 k0/B，沿超类链且必须为 Map 类型）。 */
    private fun tabStateOf(bar: View, item: View): Any? {
        val mapField = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                STATE_MAP_FIELD_CANDIDATES.firstNotNullOfOrNull { name ->
                    runCatching { cls.getDeclaredField(name) }.getOrNull()
                        ?.takeIf { Map::class.java.isAssignableFrom(it.type) }
                }
            } ?: return null
        mapField.isAccessible = true
        if (!stateMapFieldLogged) {
            stateMapFieldLogged = true
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "vivo space: state map field = ${mapField.name} (Map)",
            )
        }
        val stateMap = mapField.get(bar) as? Map<*, *> ?: return null
        return stateMap[item]
    }

    /** 反射取条目的选中动画 LottieDrawable（见 onSessionCreated 注释）：
     *  状态对象 Lottie 字段按类型扫描（不按名）。 */
    private fun resolveTabLottie(bar: View, item: View): Any? {
        val state = tabStateOf(bar, item) ?: return null
        return state.javaClass.declaredFields
            .firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.type.name == "com.airbnb.lottie.LottieDrawable"
            }
            ?.apply { isAccessible = true }
            ?.get(state)
    }

    /**
     * 悬停目标图标：13.7.0.7 优先返回应用的**选中静态图**（状态 `e` drawable / `b` res），
     * 再回退选中 Lottie。新版条目选中外观由应用自身 `k1(item)` 换图（`e`/`b`/`c`），
     * 单靠 `O(true)` 只改布局/选中 flag，不会换图标；返回 Drawable 让引擎按元素直绘直接
     * 上屏（`previewIconProgress` 对它无 `J(float)` 即无操作），引擎挂载前会保存原未选中
     * drawable、离开时恢复。旧版保持原 Lottie 路径不变。
     */
    /**
     * 选中静态图按条目自身的 `G` tint list 复刻应用 `R(Drawable)` 的换图着色（应用选中
     * 图标常为“白色底图 + 选中色 tint”，直接挂原图会丢强调色）；同时预置 selected
     * drawable state 让状态色列表解析到选中分支。返回副本，避免污染应用共享 drawable。
     */
    private fun tintedPreviewDrawable(item: View, source: Drawable): Drawable {
        val copy = runCatching {
            (source.constantState?.newDrawable() ?: source).mutate()
        }.getOrDefault(source)
        runCatching { copy.setState(intArrayOf(android.R.attr.state_selected)) }
        val tint = runCatching {
            item.javaClass.getDeclaredField("G").apply { isAccessible = true }.get(item) as? ColorStateList
        }.getOrNull()
        if (tint != null) runCatching { copy.setTintList(tint) }
        return copy
    }

    private fun resolvePreviewIconDrawable(bar: View, item: View): Any? {
        if (newBottomBarLayout) {
            val state = tabStateOf(bar, item)
            if (state != null) {
                val selectedDrawable = runCatching {
                    state.javaClass.getDeclaredField("e").apply { isAccessible = true }.get(state)
                }.getOrNull() as? Drawable
                if (selectedDrawable != null) {
                    if (!selectedIconLogged) {
                        selectedIconLogged = true
                        android.util.Log.println(
                            android.util.Log.INFO, "LiquidGlass",
                            "vivo space: preview selected icon = state.e drawable",
                        )
                    }
                    return tintedPreviewDrawable(item, selectedDrawable)
                }
                val selectedRes = runCatching {
                    state.javaClass.getDeclaredField("b").apply { isAccessible = true }.getInt(state)
                }.getOrDefault(0)
                if (selectedRes != 0) {
                    val drawable = runCatching { item.context.getDrawable(selectedRes) }.getOrNull()
                    if (drawable != null) {
                        if (!selectedIconLogged) {
                            selectedIconLogged = true
                            android.util.Log.println(
                                android.util.Log.INFO, "LiquidGlass",
                                "vivo space: preview selected icon = state.b res $selectedRes",
                            )
                        }
                        return tintedPreviewDrawable(item, drawable)
                    }
                }
            }
        }
        return resolveTabLottie(bar, item)
    }

    /** 缓存解析出的进度方法：候选 J(float)（13.7.0.7）/ H(float)（13.6.0.1），内部换算帧
     *  并触发重绘。**不可用"唯一 void(float) 方法"签名兜底**：新版同类 N(float)/旧版
     *  L(float) 是 setSpeed——按签名猜会设 speed 而非进度（悬停看似跳帧、还原把速度归零，
     *  全部失效），已踩坑（§4.7 教训 2）。 */
    private var tabFrameSetter: java.lang.reflect.Method? = null
    private var appClassLoader: ClassLoader? = null

    /** 拖离原选中 tab 的反选还原 = 调应用自身 `VBottomNavigationView.E0(item)`
     *  （13.7.0.7）/ `w(item)`（13.6.0.1）。方法体语义：O/y(false) + 动画播放中则停 +
     *  状态表未选中静态图换回、为零才回退 lottie 首帧。方法 protected、声明在父类
     *  VBottomNavigationView——查找必须沿超类链；参数类型取条目运行时类（不硬编码
     *  混淆短名）。失效返回 false → 引擎回退动画首帧（不崩溃）。 */
    private fun unselectTabIcon(bar: View, item: View): Boolean {
        val w = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                UNSELECT_METHOD_CANDIDATES.firstNotNullOfOrNull { name ->
                    runCatching { cls.getDeclaredMethod(name, item.javaClass) }.getOrNull()
                }
            } ?: return false
        w.isAccessible = true
        w.invoke(bar, item)
        if (!unselectLogged) {
            unselectLogged = true
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "vivo space: unselect method = ${w.name}",
            )
        }
        return true
    }

    /** 驱动 tab 选中动画到指定帧（0=首帧未选中，1=末帧选中）。 */
    private fun driveTabFrame(lottie: Any, progress: Float) {
        val setter = tabFrameSetter ?: run {
            FRAME_METHOD_CANDIDATES.firstNotNullOfOrNull { name ->
                runCatching { lottie.javaClass.getMethod(name, Float::class.javaPrimitiveType) }
                    .getOrNull()
            }?.apply { isAccessible = true }
                ?.also {
                    tabFrameSetter = it
                    android.util.Log.println(
                        android.util.Log.INFO, "LiquidGlass",
                        "vivo space: frame method = ${it.name}(float)",
                    )
                }
        } ?: return
        // 预览图标可能是“选中静态图”（非 Lottie）：缓存的方法声明类不匹配时跳过，
        // 避免每帧 IllegalArgumentException（静态图保持选中态即可）。
        if (!setter.declaringClass.isInstance(lottie)) return
        setter.invoke(lottie, progress)
    }
}
