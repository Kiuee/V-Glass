package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassInstaller
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * 芒果TV（com.hunantv.imgo.activity，版本 9.4.5 取证）适配器。
 *
 * - 主宿主链路：launcher = [TAB_ACTIVITY]（manifest launcher-activity，多个
 *   activity-alias 均指向它）。主界面 = `com.mgtv.ui.main.MainFragment`
 *   （onInitializeData 里 `MgFragmentTabHost.setUp` + `addTabContent` + `setUpTab`）。
 *   常规模式 4~5 个 tab（精选 index/大芒 damang/VIP/可选电商直播/我的 me，配置在
 *   MainFragment.De 分支决定）、长辈模式 3 个、浏览器模式 1 个（<2 tab 不装配，
 *   引擎 readyToInstall 天然拒绝）。
 * - 底栏结构（activity_main_mgtv 布局，资源名未混淆）：[TAB_HOST_CLASS]
 *   `MgFragmentTabHost`（非混淆，SkinnableRelativeLayout 子类）内含三个直接子项——
 *   `tab_host_continar`（Fragment 内容容器，**全高**，内容原生即延伸到 bar 后方，
 *   无需内容延伸 hook）、[BG_ID_NAME] `bottom_tab_bg`（51dp 底栏背景 View，
 *   MainFragment.I，与条目容器同路径显隐）、[BAR_ID_NAME] `tab_host_index`
 *   （80dp 横向 LinearLayout = 底栏容器，条目宿主）。条目高 80dp、内容贴底
 *   （49dp 点击热区 + 图标 23dp + 标签），玻璃槽位 ~56dp 居中镜像会裁掉底部标签
 *   → descriptor `wholeTabAlignBottom`（引擎新增声明式通道，默认 false 其余应用
 *   逐位不变）改底对齐镜像。
 * - 每 tab 视图 = [TAB_ITEM_CLASS] `MainTabView`（非混淆 SkinnableFrameLayout，
 *   inflate `layout_main_tab_item`：`ivDynTabIcon` 23dp 图标 + `tvTabName` 标签 +
 *   `main_tab_notify` 角标 TextView + `img_click` 49dp 点击热区）。图标四形态
 *   （字段 R）：0=selector（selector_main_tab_*，StateListDrawable XML）、
 *   1=Lottie（服务端 `MobileConfig.LOTTIE_SWITCH` 开关，`V` StateListDrawable
 *   {state_selected/pressed: LottieDrawable U, default: menu_*_normal}，落定配方
 *   `b4()` = `U.x1(1.0f)`（setProgress 终帧）+ setImage(V)）、2=皮肤、3=头像
 *   （Glide 加载，仅未选中时加载）。WHOLE_TAB_FULL 整条目镜像（图标/标签/角标
 *   自含随条目）；条目自配色 → `tintRefractionContent=false`。
 * - 选中机制（自研轨，非平台 TabWidget）：`setCurrentTabByIndex` 尾部对全部条目
 *   `setSelected(Q == i)`；`MainTabView.setSelected` 覆写 = K3 标签渐变着色 +
 *   R==1 时 `U.N0()`（playAnimation 重播）/ R==0 时缩放弹跳动画 / 非 me tab 选中
 *   清角标（L3）。引擎 isSelected 读数成立；预览翻转走 [descriptor.itemCheckedApplier]
 *   = 标准 setSelected + **动画落定 + 副作用还原**（见 flipChecked）。
 * - 点击链路：条目**无 OnClickListener**——触摸监听 `com.mgtv.ui.main.tab.f`
 *   （OnTouchListener）挂在 `img_click` 子视图上（`setClickViewId(R.id.img_click)`）：
 *   DOWN 时未选中立即 `d()` → setCurrentTabByIndex 切页；已选中 200ms 计数单/双击
 *   → onTabClick/onTabDoubleClick（回顶刷新/回首页动画，teenager 模式拦截在
 *   isInterceptClick）。恒 return false → performClick 转发无效 →
 *   [descriptor.tabClickHandler] 合成 DOWN/UP 经 `dispatchTouchEvent` 直入
 *   img_click（OnTouchListener 只在 dispatchTouchEvent 路径触发，直调
 *   onTouchEvent 够不到——区别于淘宝的 onTouchEvent GestureDetector 条目）。
 * - 导航栏与窗口延伸：应用换肤/深色路径 `SkinnableActivity.refreshNavigationBar`
 *   全部收口到 `window.setNavigationBarColor`——吞色 hook（商店/淘宝/支付宝同款）+
 *   onResume 直设透明。应用窗口默认不延伸（主题无 translucent nav），内容画不进
 *   导航条区（真机"信息流不进导航条区"）——窗口延伸 LiquidTab 配方（淘宝 §13.2
 *   同款）：`LAYOUT_STABLE|LAYOUT_HIDE_NAVIGATION` 传统 flags（onResume 设置 +
 *   `View.setSystemUiVisibility` 钩子 after 同栈补位——应用切页/换肤重写 flags 时
 *   先于 traversal 补位）+ 玻璃栏抬升 = 导航栏 inset（`GlassInstaller.navInsetProvider`，
 *   会话级缓存，支付宝 §14.2 同款）。
 * - 原栏 alpha 写入拦截：对平台 `View.setAlpha` 按 identity 吞受压制视图（早期压制
 *   集合 + 活跃会话条目容器 + bottom_tab_bg）的非零写入（写 0 放行）——帧循环重申
 *   与写入隔一帧渲染，应用侧周期性 alpha 写回（H5 桥/页面动画）即一帧闪现 = 无限
 *   闪屏（§14.7 写入点拦截原则）；帧循环重申保留为 hook 安装失败时的兜底。
 * - 显隐：`unVisibleTab/visibleTab` → `MgFragmentTabHost.i1()/j1()`（tab_host_index
 *   GONE/VISIBLE）+ bottom_tab_bg 同步——mirrorVisibleGate 按 visibility/isShown
 *   跟随；应用无平移写入，无平移跟随。
 * - 内容延伸（真机"信息流不全屏"修复）：内容容器 tab_host_continar 虽全高，但各
 *   tab 页自身为原生底栏留 **51dp 底部储备**（dp_51 = 底栏背景高度，应用统一设计
 *   语言）——三形态：①fragment_selected/fragment_youliao/fragment_vip 的 vpPager
 *   （ViewPager 子类）marginBottom=51dp；②fragment_main_me_v4 的 MeRefreshLayout
 *   （me_refresh）paddingBottom=51dp；③ec_lib_fragment_channel_home 底部匿名
 *   51dp spacer View。静态 XML 无运行时写回，但 tab 首切 fragment 视图懒创建——
 *   帧循环 500ms 节流全树探测 + 切 tab 即时探测（置零节流）+ 已归一化锚点每帧
 *   失配重申（稳态零写入）+ restore 回基准。
 * - 采样冻结（真机"会员页/周边页无限闪屏"修复，主题 §10.7 同款通道）：会员页
 *   （VipFragment 子频道 = VipChannelWebViewFragment / X5 ChannelXWebViewFragment，
 *   XWebManager 路由二选一）与周边页（EcChannelWebViewFragment）是 WebView 页
 *   ——引擎逐帧外部 view.draw 打乱 Chromium/X5 自身合成、间歇输出空白帧 = 整页
 *   无限闪屏。`backdropFreezeGate` 闩式冻结：本 tab 探到 WebView 即冻结到切 tab
 *   为止（防 WebView 重挂载/isShown 抖动使 freeze↔live 反复切换、反复重录制条带
 *   本身成为闪源；切 tab 解闩立即重探）。双内核家族检测：标准 android.webkit.WebView
 *   + 腾讯 X5 com.tencent.smtt.sdk.WebView 超类链，300ms 节流探测 + 弱引用缓存；
 *   非 WebView 页 gate=false 保持引擎直绘路径不变。
 * - 冷启动闪现：条目容器在 inflate 后经 setUpTab 才有内容，且 MgFragmentTabHost
 *   是内容容器宿主（对其整体压 alpha 会连内容一起压黑）——早期压制锚定
 *   [TAB_HOST_CLASS] `setUpTab` after（此时 setUp 已建好 tab_host_index、条目已
 *   addView，早于首帧）：对条目容器 + bottom_tab_bg 压 alpha=0（三段式：8s 兜底 +
 *   会话认领）。
 */
object MangoTVAdapter : AppAdapter {

    override val targetPackage: String = "com.hunantv.imgo.activity"
    override val displayName: String = "芒果TV"
    override val tabActivityClassName: String? = TAB_ACTIVITY
    override val baselineVersions: List<String> = listOf("9.4.5")

    private const val TAB_ACTIVITY = "com.hunantv.imgo.activity.MainActivity"
    private const val TAB_HOST_CLASS = "com.mgtv.widget.MgFragmentTabHost"
    private const val TAB_ITEM_CLASS = "com.mgtv.ui.main.tab.MainTabView"
    private const val BAR_ID_NAME = "tab_host_index"
    private const val BG_ID_NAME = "bottom_tab_bg"
    private const val CLICK_ID_NAME = "img_click"
    private const val ICON_ID_NAME = "ivDynTabIcon"
    private const val BADGE_ID_NAME = "main_tab_notify"
    private const val LOTTIE_FIELD_NAME = "U"
    private const val LOTTIE_CANCEL_METHOD = "J"
    private const val LOTTIE_PROGRESS_METHOD = "x1"
    private const val CONTENT_ID_NAME = "tab_host_continar"

    // —— 内容延伸（51dp 底部储备归一化，真机"信息流不全屏"修复） ——
    // 三形态全部是应用为原生底栏留的储备：①fragment_selected/fragment_youliao/
    // fragment_vip 的 vpPager（ViewPager 子类）marginBottom=51dp；②fragment_main_me_v4
    // 的 MeRefreshLayout paddingBottom=51dp（有 id）；③ec_lib_fragment_channel_home 底部
    // 匿名 51dp spacer View（竖直 LinearLayout 末子项）。静态 XML、无运行时写回——但
    // tab 首切时 fragment 视图懒创建，需周期探测（500ms，淘宝 feed 探测同节奏）+
    // 用户切 tab 即时探测（tabClickHandler）。
    private const val RESERVE_DIMEN_NAME = "dp_51"
    private const val RESERVE_FALLBACK_DP = 51
    private const val VIEWPAGER_CLASS_NAME = "androidx.viewpager.widget.ViewPager"
    private const val CONTENT_EXT_PROBE_INTERVAL_MS = 500L

    // —— 采样冻结（WebView 页闪屏修复，主题 §10.7 同款通道） ——
    // 会员页（VipFragment 子频道 = VipChannelWebViewFragment / X5 ChannelXWebViewFragment）
    // 与周边页（EcChannelWebViewFragment）全是 WebView 页：引擎逐帧外部 view.draw 打乱
    // Chromium/X5 自身合成 → 间歇空白帧 = 整页无限闪屏。双家族检测（标准
    // android.webkit.WebView + 腾讯 X5 com.tencent.smtt.sdk.WebView，芒果TV 两套 H5
    // 内核并存），探测 300ms 节流 + 弱引用缓存。
    private const val X5_WEBVIEW_CLASS_NAME = "com.tencent.smtt.sdk.WebView"
    private const val WEBVIEW_PROBE_INTERVAL_MS = 300L

    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L

    // —— 窗口延伸（LiquidTab 配方，真机"信息流不进导航条区"修复，淘宝 §13.2 同款） ——
    // 应用窗口默认不延伸（主题无 translucent nav），窗口 frame 止于导航条顶——内容
    // 永远画不进小白条区域。LAYOUT_STABLE|LAYOUT_HIDE_NAVIGATION 传统 flags 让窗口
    // frame 延到导航条后且不退出 decor-fit（LiquidTab 明禁 setDecorFitsSystemWindows(false)）；
    // 应用切页/换肤在输入事件里重写 systemUiVisibility → setSystemUiVisibility 钩子
    // after 同栈补位（先于任何 traversal，帧循环补位补不上同帧 traversal，淘宝真机实证）。
    // 玻璃栏抬升 = 导航栏 inset（会话级缓存，支付宝 §14.2 同款）。
    private val REQUIRED_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    // —— 运行期状态（弱引用防泄漏） ——
    @Volatile private var activityRef: WeakReference<Activity>? = null
    @Volatile private var activeSessionRef: WeakReference<GlassSession>? = null
    @Volatile private var suppressedBgRef: WeakReference<View>? = null

    /** L3 副作用镜像：预览翻转 true 前 MainTabView 可见的角标（非 me tab 选中会被清），
     *  翻回 false 时还原——角标状态是应用数据（Mqtt 更新驱动），不能丢。 */
    private val badgeVisibilityRestore =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Int>())

    @Volatile private var lottieCancelMethod: java.lang.reflect.Method? = null
    @Volatile private var lottieProgressMethod: java.lang.reflect.Method? = null

    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    // —— 内容延伸状态：已归一化视图的基准记录（restore 还原用），遍历发现的锚点
    //    记录后每帧失配重申（稳态零写入）。弱键防泄漏。 ——
    private val zeroedBottomMargins =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Int>())
    private val zeroedBottomPaddings =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Int>())
    private val zeroedHeights =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Int>())
    @Volatile private var lastContentProbeMs = 0L

    // —— 采样冻结状态（闩式：本 tab 一旦见过 WebView 即冻结到切 tab 为止——
    //    防 WebView 重挂载/pager 邻页 isShown 抖动导致 freeze↔live 反复切换、
    //    反复重录制条带本身成为闪源；切 tab 立即解闩并清节流重探） ——
    @Volatile private var webViewRef: WeakReference<View>? = null
    @Volatile private var lastWebViewProbeMs = 0L
    @Volatile private var lastSeenSelectedTab = -1
    @Volatile private var latchedWebViewTab = -1
    @Volatile private var facadeForLog: HookFacade? = null
    @Volatile private var cachedNavInsetLift: Int = -1
    @Volatile private var lastGateVisible = true

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = null,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = false,
        // 条目选中翻转 = 标准 setSelected（MainTabView 覆写含动画副作用），同态去重 +
        // 图标动画落定 + 角标还原，见 flipChecked
        itemCheckedApplier = { item, checked -> flipChecked(item, checked) },
        // 条目无 OnClickListener，触摸监听在 img_click 子视图的 OnTouchListener 上——
        // 合成 DOWN/UP 经 dispatchTouchEvent 直入（OnTouchListener 只在 dispatch 路径触发）
        tabClickHandler = { slotIndex, tabViews, _ ->
            tabViews.getOrNull(slotIndex)?.let { dispatchNativeTap(it) }
            // 即时内容延伸探测：tab 首切时 fragment 视图懒创建（commitNow 同步建树），
            // 置零探测节流让下一帧全树发现新页的 51dp 储备，不等 500ms 周期
            activeSessionRef?.get()?.let { session ->
                lastContentProbeMs = 0L
                session.armActivityWindow()
            }
        },
        // 条目自配色（selector 图标 + 标签渐变着色自绘），整层染色会盖掉原生配色
        tintRefractionContent = false,
        // 原生条目 80dp、内容贴底（标签在条目底部），居中镜像会把标签裁出槽位——
        // 底对齐镜像（引擎声明式通道，默认 false 其余应用逐位不变）
        wholeTabAlignBottom = true,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == TAB_ACTIVITY

    override fun findTabBar(content: ViewGroup): View? {
        // 首选：tab_host_index 资源 id（arsc 资源名未混淆，getIdentifier 可查）
        val barId = content.resources.getIdentifier(BAR_ID_NAME, "id", content.context.packageName)
        if (barId != 0) {
            content.findViewById<View>(barId)?.let { return it }
        }
        // 兜底：MgFragmentTabHost（setUp 契约：L = findViewById(tab_host_index)）的
        // 第一个 LinearLayout 直接子项
        return findFirstByClass(content, TAB_HOST_CLASS)
            ?.takeIf<ViewGroup> { it.childCount > 0 }
            ?.let { host ->
                (0 until host.childCount)
                    .mapNotNull { host.getChildAt(it) }
                    .firstOrNull { it is android.widget.LinearLayout }
            }
    }

    override fun tabViewsOf(bar: View): List<View> {
        if (bar !is ViewGroup) return emptyList()
        val items = ArrayList<View>(bar.childCount)
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i) ?: continue
            // 防御性过滤：条目类名非混淆。过滤失效（类名漂移）时退回全部子项——
            // tab_host_index 子项本就只有条目（setUpTab 逐个 addView）
            if (child.javaClass.name == TAB_ITEM_CLASS || items.isEmpty()) {
                items.add(child)
            }
        }
        return items
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
        // 窗口延伸（淘宝 §13.2 配方）：每次 onResume 重设（应用可能在 onCreate 后改回）
        runCatching {
            val decor = activity.window?.decorView ?: return
            if (decor.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                decor.systemUiVisibility = decor.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
                decor.requestApplyInsets()
            }
        }
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // 早期压制（三段式）：条目容器在 inflate 后经 setUpTab 才有内容（setUp 先建
        // tab_host_index、循环内逐个 addView 条目，均早于首帧），MgFragmentTabHost 本体
        // 是内容容器宿主不能整体压 alpha——setUpTab after 对条目容器 + bottom_tab_bg 压
        // alpha=0，压掉 setUpTab 到装配之间的原栏闪现窗口。8s 兜底还原 + 会话认领。
        // initialize=false：进程加载期宿主 Application 未建，连锁初始化会闪退（微博 §5.7 纪律）。
        runCatching {
            val hostClass = Class.forName(TAB_HOST_CLASS, false, loader)
            val listenerClass = Class.forName("$TAB_HOST_CLASS\$e", false, loader)
            val setUpTab = hostClass.getDeclaredMethod("setUpTab", listenerClass)
            facade.around(setUpTab, null, { call ->
                val host = call.thisObj as? ViewGroup ?: return@around null
                val res = host.resources
                val pkg = host.context.packageName
                res.getIdentifier(BAR_ID_NAME, "id", pkg)
                    .takeIf { it != 0 }
                    ?.let { host.findViewById<View>(it) }
                    ?.let { suppressEarly(it, facade) }
                res.getIdentifier(BG_ID_NAME, "id", pkg)
                    .takeIf { it != 0 }
                    ?.let { host.findViewById<View>(it) }
                    ?.let { suppressEarly(it, facade) }
                null
            })
            facade.log(
                android.util.Log.INFO,
                "hooked MgFragmentTabHost.setUpTab: early suppress bar + bg",
            )
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook MgFragmentTabHost.setUpTab failed (app updated?)", it)
        }
        // 导航栏透明（商店 §2.2 / 淘宝 §13.2 / 支付宝 §14.2 同款）：应用换肤/深色设色
        // 路径（SkinnableActivity.refreshNavigationBar）全部收口到框架 PhoneWindow，
        // 吞非透明色与对比度蒙层 true；透明色与蒙层关闭另由 onResume 直设兜底。
        // 停用全部放行（重启还原）。
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
        // 原栏 alpha 写入拦截（会员页闪屏修复，支付宝 §14.7 同款写入点拦截原则）：
        // 压制类需求必须收口在写入点——帧循环重申与写入隔一帧渲染，应用侧任何周期性
        // 的底栏 alpha 写回（VIP/H5 页动画、桥指令等）都会造成一帧原栏闪现 = 无限闪屏。
        // 对平台 View.setAlpha 全局 hook，回调按实例 identity 判定：仅受压制视图
        // （早期压制集合 + 活跃会话条目容器 + bottom_tab_bg）的非零写入被吞，写 0
        // （压制自身）放行；restore 通道经 onAuxGlassRestore 清活跃引用后引擎的
        // alpha=1 还原天然放行。hook 安装失败降级为仅帧循环压制（闪现残留）。
        runCatching {
            val setAlpha = View::class.java.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType)
            facade.around(setAlpha, { call ->
                val view = call.thisObj as? View ?: return@around null
                val alpha = call.args.firstOrNull() as? Float ?: return@around null
                if (!isSuppressedContainer(view)) return@around null
                if (alpha != 0f) false else null
            }, null)
            facade.log(android.util.Log.INFO, "hooked View.setAlpha: suppressed-container non-zero writes swallowed")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook View.setAlpha failed (flash-back may remain)", it)
        }
        // 窗口延伸 flags 同栈补位（淘宝 §13.2 同款）：应用切页/换肤在输入事件里重写
        // systemUiVisibility → 同帧 traversal 用缺位值重排 = 底栏区内容闪动。after
        // 同栈补位先于任何 traversal，窗口永远看不到缺位状态。应用自身的
        // updateNavigationBarFlag（亮暗导航图标位切换）是读改写、保留其他位，与此
        // 补位兼容；重入时 flags 已在位、不再写入，天然收敛。
        runCatching {
            val setSui = View::class.java.getDeclaredMethod(
                "setSystemUiVisibility", Int::class.javaPrimitiveType,
            )
            facade.around(setSui, null, { call ->
                val view = call.thisObj as? View ?: return@around null
                runCatching {
                    if (view.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                        view.systemUiVisibility = view.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setSystemUiVisibility: layout bits re-ORed in-stack")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook setSystemUiVisibility failed", it)
        }
    }

    /** setAlpha 拦截判定：受压制视图 = 早期压制集合成员或活跃会话的条目容器/
     *  bottom_tab_bg（identity）。restore 通道：onAuxGlassRestore 先清活跃引用，
     *  引擎随后的 alpha=1 还原写入自然放行。 */
    private fun isSuppressedContainer(view: View): Boolean {
        if (synchronized(earlySuppressed) { earlySuppressed.contains(view) }) return true
        if (activeSessionRef?.get()?.tabContainer === view) return true
        return suppressedBgRef?.get() === view
    }

    private fun suppressEarly(view: View, facade: HookFacade) {
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        facade.log(android.util.Log.INFO, "mangotv early-suppress tab bar view (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "mangotv early-suppress fallback restore (session never claimed)",
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
        earlySuppressed.remove(session.tabContainer)
        activeSessionRef = WeakReference(session)
        if (session.originalContainerVisibility != View.VISIBLE) {
            session.tabContainer.visibility = session.originalContainerVisibility
        }
        // bottom_tab_bg 的会话认领 + restore 通道：装配期引擎压的是条目容器，背景
        // View 由适配器压制（setUpTab 已压 / 此处兜底认领），restore 时还原 alpha。
        // id 解析失败 = setUpTab 早期压制同样未生效，此处不再单独兜底。
        val bg = session.tabContainer.rootView.let { rootView ->
            val res = session.tabContainer.resources
            val bgId = res.getIdentifier(BG_ID_NAME, "id", session.tabContainer.context.packageName)
            if (bgId != 0) rootView.findViewById<View>(bgId) else null
        }
        if (bg != null && bg !== session.tabContainer) {
            earlySuppressed.remove(bg)
            if (bg.alpha != 0f) bg.alpha = 0f
            suppressedBgRef = WeakReference(bg)
        }
        session.onAuxGlassRestore = {
            activeSessionRef = null
            suppressedBgRef?.get()?.let { bg -> if (bg.alpha != 1f) bg.alpha = 1f }
            suppressedBgRef = null
            badgeVisibilityRestore.clear()
            restoreContentExtension()
        }
        session.mirrorVisibleGate = { mirrorGate(session) }
        facadeForLog = facade
        // 采样冻结（主题 §10.7 同款通道 + 闩式防抖）：WebView 页可见时冻结采样——进入时
        // 一次性录制条带、冻结期间纯回放（零外部 WebView draw），离开恢复实时采样。
        // 会员/周边页无限闪屏修复；非冻结页保持引擎直绘路径不受影响。
        session.backdropFreezeGate = { freezeGate(session) }
        // 玻璃栏抬升（真实导航栏 inset，会话级缓存）：窗口延伸后玻璃贴窗口底会压在
        // 小白条上，按 inset 抬到导航条顶（淘宝/支付宝同款）。
        GlassInstaller.navInsetProvider = { computeNavInsetLift(session, activity) }
    }

    /** 逐帧门控：应用显隐 = tab_host_index + bottom_tab_bg 同步 visibility 切换
     *  （i1/j1），无 alpha/平移写入——按可见 + isShown 跟随即可；稳态对两个受压制
     *  视图的 alpha 做失配重申（应用虽无 alpha 写入路径，兜底防新增）+ 51dp 底部
     *  储备归一化重申。 */
    private fun mirrorGate(session: GlassSession): Boolean {
        val bar = session.tabContainer
        if (bar.visibility == View.VISIBLE && bar.alpha != 0f) bar.alpha = 0f
        suppressedBgRef?.get()?.let { bg ->
            if (bg.isAttachedToWindow && bg.visibility == View.VISIBLE && bg.alpha != 0f) bg.alpha = 0f
        }
        reassertContentExtension(session)
        val visible = bar.visibility == View.VISIBLE && bar.isShown
        // 显隐状态变化取证（仅变化时输出）：若应用在某页高频翻转底栏 visibility，
        // 玻璃跟随闪现，此处日志直接暴露切换频率
        if (visible != lastGateVisible) {
            lastGateVisible = visible
            facadeForLog?.log(
                android.util.Log.INFO,
                "mirror gate -> $visible (bar visibility=${bar.visibility})",
            )
        }
        return visible
    }

    /** 玻璃栏抬升（真实导航栏 inset，会话级缓存，支付宝 §14.2 同款）：手势导航下该值
     *  是常量；切页 insets 重派发期间瞬时读数跳变会让以抬升为源的几何抖动。 */
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

    // —— 内容延伸（51dp 底部储备归一化） ——

    /** 每帧调用：500ms 节流全树探测（tab 首切时 fragment 视图懒创建，新储备在此发现）
     *  + 已归一化锚点的失配重申（稳态零写入）。 */
    private fun reassertContentExtension(session: GlassSession) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastContentProbeMs >= CONTENT_EXT_PROBE_INTERVAL_MS) {
            lastContentProbeMs = now
            discoverContentReserves(session)
        }
        synchronized(zeroedBottomMargins) {
            for ((v, _) in zeroedBottomMargins) {
                val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                if (lp.bottomMargin != 0) {
                    lp.bottomMargin = 0
                    v.layoutParams = lp
                }
            }
        }
        synchronized(zeroedBottomPaddings) {
            for ((v, _) in zeroedBottomPaddings) {
                if (v.paddingBottom != 0) v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            }
        }
        synchronized(zeroedHeights) {
            for ((v, _) in zeroedHeights) {
                val lp = v.layoutParams ?: continue
                if (lp.height != 0) {
                    lp.height = 0
                    v.layoutParams = lp
                }
            }
        }
    }

    /** 全树探测 51dp 储备三形态并归一化（记录基准供 restore）：
     *  ①ViewPager 子类的 bottomMargin == 储备值（精选/畅聊/VIP 页 vpPager）；
     *  ②有 id 视图的 paddingBottom == 储备值（我的页 MeRefreshLayout）；
     *  ③竖直 LinearLayout 末位匿名 match_parent 子项 height == 储备值（ec 页 spacer）。 */
    private fun discoverContentReserves(session: GlassSession) {
        val root = contentRootOrNull(session) ?: return
        val reserve = reservePx(root)
        pruneDeadReserves()
        walkContent(root) { v ->
            val lp = v.layoutParams
            if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin == reserve &&
                !recorded(zeroedBottomMargins, v) && isViewPagerSubclass(v)
            ) {
                zeroedBottomMargins[v] = lp.bottomMargin
                lp.bottomMargin = 0
                v.layoutParams = lp
                return@walkContent
            }
            if (v.paddingBottom == reserve && v.id != View.NO_ID && !recorded(zeroedBottomPaddings, v)) {
                zeroedBottomPaddings[v] = v.paddingBottom
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                return@walkContent
            }
            if (lp != null && lp.height == reserve && lp.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                v.id == View.NO_ID && isLastSpacerOfVerticalLinear(v) && !recorded(zeroedHeights, v)
            ) {
                zeroedHeights[v] = lp.height
                lp.height = 0
                v.layoutParams = lp
            }
        }
    }

    private fun recorded(map: MutableMap<View, Int>, v: View): Boolean = synchronized(map) { map.containsKey(v) }

    private fun contentRootOrNull(session: GlassSession): View? {
        val bar = session.tabContainer
        val res = bar.resources
        val contentId = res.getIdentifier(CONTENT_ID_NAME, "id", bar.context.packageName)
        if (contentId != 0) {
            (bar.parent as? View)?.findViewById<View>(contentId)?.let { return it }
            session.appRoot.findViewById<View>(contentId)?.let { return it }
        }
        // id 漂移兜底：走 appRoot 全树（三条规则自带类名/id/结构门控，误伤面已收窄）
        return session.appRoot
    }

    private fun reservePx(root: View): Int {
        val res = root.resources
        val id = res.getIdentifier(RESERVE_DIMEN_NAME, "dimen", root.context.packageName)
        if (id != 0) return res.getDimensionPixelSize(id)
        return (RESERVE_FALLBACK_DP * res.displayMetrics.density).toInt()
    }

    private fun pruneDeadReserves() {
        synchronized(zeroedBottomMargins) { zeroedBottomMargins.keys.removeAll { it.parent == null && !it.isAttachedToWindow } }
        synchronized(zeroedBottomPaddings) { zeroedBottomPaddings.keys.removeAll { it.parent == null && !it.isAttachedToWindow } }
        synchronized(zeroedHeights) { zeroedHeights.keys.removeAll { it.parent == null && !it.isAttachedToWindow } }
    }

    private fun isViewPagerSubclass(v: View): Boolean {
        var c: Class<*>? = v.javaClass
        while (c != null) {
            if (c.name == VIEWPAGER_CLASS_NAME) return true
            c = c.superclass
        }
        return false
    }

    private fun isLastSpacerOfVerticalLinear(v: View): Boolean {
        val parent = v.parent as? android.widget.LinearLayout ?: return false
        if (parent.orientation != android.widget.LinearLayout.VERTICAL) return false
        return parent.indexOfChild(v) == parent.childCount - 1
    }

    private fun walkContent(root: View, visit: (View) -> Unit) {
        visit(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.let { walkContent(it, visit) }
            }
        }
    }

    /** restore（会话还原）：51dp 储备全部回基准。 */
    private fun restoreContentExtension() {
        synchronized(zeroedBottomMargins) {
            for ((v, original) in zeroedBottomMargins) {
                runCatching {
                    val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return@runCatching
                    if (lp.bottomMargin == 0 && original != 0) {
                        lp.bottomMargin = original
                        v.layoutParams = lp
                    }
                }
            }
            zeroedBottomMargins.clear()
        }
        synchronized(zeroedBottomPaddings) {
            for ((v, original) in zeroedBottomPaddings) {
                runCatching {
                    if (v.paddingBottom == 0 && original != 0) {
                        v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, original)
                    }
                }
            }
            zeroedBottomPaddings.clear()
        }
        synchronized(zeroedHeights) {
            for ((v, original) in zeroedHeights) {
                runCatching {
                    val lp = v.layoutParams ?: return@runCatching
                    if (lp.height == 0 && original != 0) {
                        lp.height = original
                        v.layoutParams = lp
                    }
                }
            }
            zeroedHeights.clear()
        }
    }

    // —— 采样冻结（WebView 页检测，双内核家族 + 闩式防抖） ——

    /** 冻结门控：选中 tab 变化即解闩（清缓存 + 清节流立即重探）；本 tab 一旦探到
     *  WebView 即闩住返回 true，直到切 tab——防 WebView 重挂载/isShown 抖动导致
     *  freeze↔live 反复切换、反复重录制条带本身成为闪源。 */
    private fun freezeGate(session: GlassSession): Boolean {
        val selected = session.tabViews.indexOfFirst { it.isSelected }
        if (selected != lastSeenSelectedTab) {
            lastSeenSelectedTab = selected
            if (latchedWebViewTab != -1) {
                latchedWebViewTab = -1
                webViewRef = null
                lastWebViewProbeMs = 0L
                facadeForLog?.log(
                    android.util.Log.INFO,
                    "backdrop freeze unlatch (tab changed -> $selected)",
                )
            }
        }
        if (selected != -1 && latchedWebViewTab == selected) return true
        val webview = visibleWebViewOrNull(session)
        if (webview != null && selected != -1 && latchedWebViewTab == -1) {
            latchedWebViewTab = selected
            facadeForLog?.log(
                android.util.Log.INFO,
                "backdrop freeze latch on tab $selected (webview visible)",
            )
        }
        return webview != null
    }

    /** WebView 页可见判定：缓存有效（attached + shown）直接返回；否则 300ms 节流全树
     *  探测。命中标准 WebView 或腾讯 X5 WebView（芒果TV 两套 H5 内核并存）。 */
    private fun visibleWebViewOrNull(session: GlassSession): View? {
        webViewRef?.get()?.let { wv ->
            if (wv.isAttachedToWindow && wv.isShown) return wv
        }
        webViewRef = null
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWebViewProbeMs < WEBVIEW_PROBE_INTERVAL_MS) return null
        lastWebViewProbeMs = now
        var found: View? = null
        walkContent(session.appRoot) { v ->
            if (found == null && isWebViewView(v)) found = v
        }
        found?.let { webViewRef = WeakReference(it) }
        return found
    }

    private fun isWebViewView(v: View): Boolean {
        if (v is android.webkit.WebView) return true
        var c: Class<*>? = v.javaClass
        while (c != null) {
            if (c.name == X5_WEBVIEW_CLASS_NAME) return true
            c = c.superclass
        }
        return false
    }

    /**
     * 预览选中翻转（§0.5 B.1/B.2/B.5）：`MainTabView.setSelected` 覆写是唯一选中
     * 写入口，含动画/数据副作用——
     * - 同态去重：selected 已是目标态直接返回（覆写对重复 true 会重播 Lottie/弹跳动画）；
     * - R==0 弹跳：flip(true) 后 `iconView.clearAnimation()` 落定（公共 API）；
     * - R==1 Lottie：覆写内 `U.N0()`（playAnimation 全程重播）——flip 后按应用落定
     *   配方 b4() 的语义停动画 + 进度直驱终帧（`U.J()` cancel + `U.x1(1.0f)`
     *   setProgress，lottie 混淆真名取证，失效静默降级为短暂重播）；
     * - L3 角标清除（非 me tab 选中）：flip(true) 前记录可见角标、flip(false) 还原。
     */
    private fun flipChecked(item: View, checked: Boolean) {
        if (item.isSelected == checked) return
        val badge = badgeView(item)
        if (checked) {
            if (badge != null && badge.visibility == View.VISIBLE) {
                badgeVisibilityRestore[item] = View.VISIBLE
            }
            item.isSelected = true
            iconView(item)?.clearAnimation()
            settleLottie(item)
        } else {
            item.isSelected = false
            badge?.let { b ->
                badgeVisibilityRestore.remove(item)?.let { b.visibility = it }
            }
        }
    }

    /** R==1（Lottie 图标形态）落定：MainTabView 字段 U（LottieDrawable，非混淆类
     *  字段真名）→ `J()`（cancelAnimation）+ `x1(1.0f)`（setProgress 终帧）。
     *  锚点失效（升版混淆漂移）静默降级：预览期图标短暂重播动画，无崩溃路径。 */
    private fun settleLottie(item: View) {
        runCatching {
            val drawable = item.javaClass.getDeclaredField(LOTTIE_FIELD_NAME).let { field ->
                field.isAccessible = true
                field.get(item)
            } ?: return
            val cancel = lottieCancelMethod ?: drawable.javaClass
                .getDeclaredMethod(LOTTIE_CANCEL_METHOD)
                .also { lottieCancelMethod = it }
            val progress = lottieProgressMethod ?: drawable.javaClass
                .getDeclaredMethod(LOTTIE_PROGRESS_METHOD, Float::class.javaPrimitiveType)
                .also { lottieProgressMethod = it }
            cancel.invoke(drawable)
            progress.invoke(drawable, 1.0f)
        }
    }

    private fun iconView(item: View): View? {
        val id = item.resources.getIdentifier(ICON_ID_NAME, "id", item.context.packageName)
        return if (id == 0) null else item.findViewById(id)
    }

    private fun badgeView(item: View): View? {
        val id = item.resources.getIdentifier(BADGE_ID_NAME, "id", item.context.packageName)
        return if (id == 0) null else item.findViewById(id)
    }

    /** 合成 DOWN/UP 经 dispatchTouchEvent 直入 img_click（触点 = 热区中心）：
     *  复刻原生手势全链路（f.onTouch DOWN 分支：未选中立即切页 / 已选中 200ms 单击
     *  计数 → onTabClick 回顶刷新；isInterceptClick teenager 门控照常生效）。
     *  UP 经 view.post 延后派发，事件回收。 */
    private fun dispatchNativeTap(item: View) {
        runCatching {
            val res = item.resources
            val clickId = res.getIdentifier(CLICK_ID_NAME, "id", item.context.packageName)
            val target = (if (clickId != 0) item.findViewById<View>(clickId) else null) ?: item
            val now = android.os.SystemClock.uptimeMillis()
            val x = target.width.coerceAtLeast(1) / 2f
            val y = target.height.coerceAtLeast(1) / 2f
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, x, y, 0)
            target.dispatchTouchEvent(down)
            target.post {
                runCatching { target.dispatchTouchEvent(up) }
                down.recycle()
                up.recycle()
            }
        }
    }
}
