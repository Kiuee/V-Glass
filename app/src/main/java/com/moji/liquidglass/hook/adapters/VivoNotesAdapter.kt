package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassInstaller
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.glass.TabExtractor
import com.moji.liquidglass.hook.HookFacade
import androidx.compose.ui.graphics.Color
import java.lang.ref.WeakReference

/**
 * vivo 原子笔记（com.android.notes，vivo 系统应用）适配器。
 *
 * - 主 Tab Activity：com.android.notes.Notes（launcher alias 指向它；四页 = 便签/待办/
 *   文档/我的 Fragment 经 NoteSideView 内容容器切换；编辑页 BaseEditActivity 等为独立
 *   Activity 天然无底栏场景，仅此 Activity 探测）。
 * - 底栏：视觉外壳 nav_container（com.originui.core.blur.VBlurLinearLayout：模糊背景 +
 *   nav_view 0 高 inset 填充视图，Activity 的 insets 监听把 navigationBars 底高写入它）
 *   > navigation_container > CustomNavigationView（LinearLayout，实现 OnClickListener，
 *   自持条目表与选中下标）；每 tab 视图 = NavigationItemView（Lottie 图标 + title
 *   TextView）。4 tab（便签/待办/文档/我的）；出口项目构建（p.isExportProject()）把
 *   文档 tab 置 GONE，中文版恒 4 tab。
 * - 选中态是"自绘标志位"型：内部标志 + 文字色 300ms ArgbEvaluator 动画 + Lottie 选中
 *   动画/静态图切换，从不调 setSelected/setChecked——hook a(Z)/b() 两入口桥接 isSelected
 *   供引擎读取；拖动悬停预览走 itemCheckedApplier（true → b() 直置选中 = 末帧语义）。
 * - 配色：title 是纯色 TextView（无 ColorStateList 选择器），延迟捕获（选中变化 350ms
 *   后读稳态）经 colorOverride 注入。
 * - 内容延伸：待办/文档页 fragment 根是普通包装容器，应用 insets 监听写 paddingBottom
 *   把整页抬起 → 玻璃条带只有背景色（"模糊失效"）——写入点收口 + 防抖清扫归零；
 *   笔记/我的页根是 VCommonLayoutCoordinator（padding 参与列表悬浮合并数学、本来就在
 *   延伸）豁免不动。
 */
object VivoNotesAdapter : AppAdapter {

    override val targetPackage: String = "com.android.notes"
    override val displayName: String = "原子笔记"
    override val tabActivityClassName: String? = "com.android.notes.Notes"
    override val baselineVersions: List<String> = listOf("5.4.5.107")

    private const val BAR_CLASS = "com.android.notes.widget.navigation.CustomNavigationView"
    private const val ITEM_CLASS = "com.android.notes.widget.navigation.NavigationItemView"
    // 视觉外壳（originui 库，非混淆）：模糊背景/inset 填充视图/背景色都在外壳上，
    // suppressNatively 必须作用于外壳——只藏 nav 本体会残留背景条。类名失配回退锚定
    // nav 本体（仅背景条残留，功能不受损）。
    private const val SHELL_CLASS = "com.originui.core.blur.VBlurLinearLayout"
    // 内容延伸锚点（资源表未混淆，getIdentifier 可用）
    private const val CONTENT_CONTAINER_ID_NAME = "container_content"
    private const val NAV_VIEW_ID_NAME = "nav_view"
    // 笔记/我的页根（originui 悬浮合并布局）：根 padding 参与"列表悬浮合并"数学
    // （height + headerBottom − paddingBottom，本来就在延伸）——清扫豁免
    private const val COORDINATOR_SIMPLE_NAME = "VCommonLayoutCoordinator"
    // 选中翻转口（R8 单字母方法名，随版本漂移风险最高，§8.3）：
    // a(Z) = 动画翻转主口（容器点击切换 g(int,int)、程序化 g/h 都经它）
    // b()   = 跳动画直置口（冷启动恢复选中、onConfigurationChanged 重应用选中）
    private const val SELECT_ANIMATED_METHOD = "a"
    private const val SELECT_DIRECT_METHOD = "b"
    // 延迟配色捕获：文字色动画 300ms，350ms 读稳态
    private const val COLOR_CAPTURE_DELAY_MS = 350L
    // NavigationItemView 构造时写入的稳态配色字段（5.4.5.107 实证）：
    // h = 选中色（b() 直设 title）、i = 未选中色（c(false) 动画终点）。装配期直接读，
    // 不依赖 350ms 后的 title 动画落定。
    private const val ITEM_SELECTED_COLOR_FIELD = "h"
    private const val ITEM_UNSELECTED_COLOR_FIELD = "i"
    // 切页触发的页面根重扫防抖
    private const val SWEEP_DEBOUNCE_MS = 300L

    // —— 运行期共享状态（单例适配器；WeakReference 防泄漏，主线程读写） ——
    /** 底栏 nav 本体（CustomNavigationView：条目表读取 + 配色捕获）。 */
    @Volatile private var navRef: WeakReference<View>? = null
    /** 内容容器 container_content（页面根清扫与写入点判定的父锚点）。 */
    @Volatile private var contentContainerRef: WeakReference<ViewGroup>? = null
    @Volatile private var contentIdCache = 0
    @Volatile private var navViewIdCache = 0
    /** 写入点收口已记录的页面根（写回 ≥ 储备归零；弱引用随视图销毁自愈）。 */
    private val pageRoots = java.util.WeakHashMap<View, Boolean>()
    /** 延迟配色捕获结果（0 = 未捕获；经 createExtractor 的 colorOverride 注入引擎）。 */
    @Volatile private var capturedContentColor = 0
    @Volatile private var capturedAccentColor = 0
    @Volatile private var colorCapturePending = false
    @Volatile private var staticColorsLogged = false
    @Volatile private var sweepPending = false
    @Volatile private var sweepMissLogged = false
    @Volatile private var writePointHitLogged = false
    @Volatile private var applierWarned = false

    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：icon = LottieAnimationView（静态 vector + Lottie 填充动画都在它上）、
        // title = 标签 TextView（标签即单 TextView，extractor 原生支持）。symbol 字体变体
        // 布局（layout_navigation_symbol_item）同 id 名，天然兼容。
        elementIds = mapOf(
            "icon" to "icon",
            "labels" to "title",
        ),
        // 拖动悬停预览直控通道：checked=true → 反射 b()（跳动画直置选中 = "悬停=末帧"
        // 规范语义；a(true) 会播放完整 Lottie 填充动画偏离规范，应用自身冷启动恢复选中
        // 即走 b()）；checked=false → 反射 a(false)（静态未选中图 + 文字色动画回退）。
        // a(Z) 纯视觉无切页副作用（切页在容器 onClick 的监听器回调里），无需微博式吞咽
        // hook。itemCheckedSetterMethodName 保留仅为 previewWholeTab 的反射入口守卫
        // （applier 优先，不会被走到）。
        itemCheckedSetterMethodName = SELECT_ANIMATED_METHOD,
        itemCheckedApplier = { view, checked ->
            if (checked) {
                invokeItemMethod(view, SELECT_DIRECT_METHOD, null)
            } else {
                invokeItemMethod(view, SELECT_ANIMATED_METHOD, false)
            }
        },
        // 关闭液泡折射副本的整层强调色染色：条目自带配色（多色 Lottie 动画 + title 纯色），
        // 染色会把折射副本盖成单色剪影。suppressBubbleCoveredSlots 不声明（默认关）：
        // alpha 硬切抑制在真机上随液泡运动引发图标消失/闪烁（§8.7 教训 4 回退记录）
        tintRefractionContent = false,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun createExtractor(): TabExtractor = TabExtractor(descriptor).apply {
        // 配色注入缝：title 是纯色 TextView（无 ColorStateList 选择器），装配期 extract
        // 只能取到首个 tab 的单色。优先读 NavigationItemView 构造期稳态字段 h（选中）/
        // i（未选中）——冷启动首帧即可供给强调色（选中标签立即染色）；原 350ms 延迟捕获
        // （文字色动画 300ms 后读稳态）仅作字段改名/异常兜底，不新增引擎刷新通道。
        colorOverride = {
            val content = capturedContentColor
            val accent = capturedAccentColor
            if (content != 0 && accent != 0 && content != accent) {
                Color(content) to Color(accent)
            } else {
                // 冷启动首帧：title 纯色/动画未落定时捕获可能尚未成功，直接读条目
                // 构造期写入的稳态字段（h 选中 / i 未选中），避免选中标签在装配期
                // 落到内容色（症状：冷启动选中 tab 文字未染色）。
                val static = staticItemColors()
                when {
                    static != null -> Color(static.first) to Color(static.second)
                    content == 0 && accent == 0 -> null
                    else -> (content.takeIf { it != 0 }?.let(::Color)) to
                        (accent.takeIf { it != 0 }?.let(::Color))
                }
            }
        }
    }

    override fun readyToInstall(tabViews: List<View>, loader: ClassLoader): Boolean =
        tabViews.size >= 2 // WHOLE_TAB：无需 Lottie 合成校验，条目就位即可

    override fun findTabBar(content: ViewGroup): View? {
        val nav = findByClass(content, BAR_CLASS) ?: return null
        navRef = WeakReference(nav)
        // tabContainer = 视觉外壳 nav_container：模糊背景（VBlurLinearLayout 自绘）/
        // inset 填充视图/nav_view 高度写入都在外壳上，suppressNatively 隐藏外壳才能一并
        // 隐掉；应用对 nav_container 的 translationY 动画（s0 显隐/侧抽屉）也以它为观测对象
        var node: Any? = nav.parent
        while (node is View) {
            if (node.javaClass.name == SHELL_CLASS) {
                logParentChain(nav, "shell=${node.javaClass.name}")
                return node
            }
            if (node.parent === content) break
            node = node.parent
        }
        // 外壳失配回退锚定 nav 本体（tab 隐藏但模糊背景条残留，功能不受损）
        logParentChain(nav, "shell NOT found (fallback to nav)")
        return nav
    }

    override fun tabViewsOf(bar: View): List<View> {
        // bar 可能是外壳（VBlurLinearLayout）或 nav 本体（外壳失配回退）：向下重新定位 nav
        val nav = if (bar.javaClass.name == BAR_CLASS) {
            bar
        } else {
            findByClass(bar, BAR_CLASS) ?: return emptyList()
        }
        if (nav !is ViewGroup) return emptyList()
        // 直接子项按类名收集 + VISIBLE 过滤：出口项目构建把文档 tab 置 GONE
        // （Notes.N0: findViewById(R.id.tab_word).setVisibility(8)，isExportProject() 是
        // 构建期常量、进程内不翻）——玻璃栏与原生 tab 一一对应；类计数守卫（<2 返回空）
        // 防结构误识别误装
        val items = (0 until nav.childCount)
            .mapNotNull { nav.getChildAt(it) }
            .filter { it.javaClass.name == ITEM_CLASS && it.visibility == View.VISIBLE }
        return if (items.size >= 2) items else emptyList()
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        resolveContentContainer(activity)
        // 导航栏透明呈现（AppAdapter 通用助手，全部适配应用同款）：小白条区域透出应用
        // 全出血内容（应用 SDK 35 边到边，列表自身维护滚动储备）
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // ① 选中态桥接：应用是"自绘标志位"型（内部标志 + 文字色 300ms 动画 + Lottie
        //    选中动画/静态图切换），从不调 setSelected/setChecked——两个翻转入口都 after
        //    桥接 isSelected 供引擎帧循环读取。失效表现 = 玻璃液泡停在装配时 tab 不跟随
        //    切页（WARN 可见）。a(boolean) 是翻转主口（容器点击切换 g(int,int) 与程序化
        //    路径都经它），b() 是跳动画直置口（程序化换页/onConfigurationChanged）。
        val itemClass = runCatching { Class.forName(ITEM_CLASS, true, loader) }.getOrNull()
        if (itemClass == null) {
            facade.log(android.util.Log.WARN, "Notes NavigationItemView not found (app updated?)")
            return
        }
        runCatching {
            val a = itemClass.getDeclaredMethod(SELECT_ANIMATED_METHOD, Boolean::class.javaPrimitiveType)
            facade.around(a, null, { call ->
                (call.thisObj as? View)?.isSelected = call.args.getOrNull(0) as? Boolean ?: false
                scheduleColorCapture()
                scheduleSweep()
                null
            })
            facade.log(android.util.Log.INFO, "hooked Notes NavigationItemView.a(Z) selection bridge")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook Notes NavigationItemView.a failed (app updated?)", it)
        }
        runCatching {
            val b = itemClass.getDeclaredMethod(SELECT_DIRECT_METHOD)
            facade.around(b, null, { call ->
                (call.thisObj as? View)?.isSelected = true
                scheduleColorCapture()
                scheduleSweep()
                null
            })
            facade.log(android.util.Log.INFO, "hooked Notes NavigationItemView.b() selection bridge")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook Notes NavigationItemView.b failed (app updated?)", it)
        }
        // ② 页面根储备写入点收口（平台 View.setPadding after，见 installPageRootSink）
        installPageRootSink(facade)
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 背景采样去重：由 TargetHook 会话创建期统一设置（引擎单管线，默认开）——
        // 笔记/待办重内容页 3× 全树绘制/帧 → 1× RenderNode 录制 + 三节点回放
        // 显隐跟随（微博/小红书同款 Y 门控）：s0(boolean) 对 nav_container 做 translationY
        // 0 ↔ 高度+小白条 动画（多选进出）；侧边抽屉打开的位移含 70dp+小白条 的 Y 分量
        // （Notes$c.a(f) 侧滑进度回调）——两种场景 translationY 都过半，默认 Y 门控全覆盖
        val container = session.tabContainer
        session.mirrorVisibleGate = {
            container.isShown && container.translationY < container.height * 0.5f
        }
        // 玻璃栏小白条抬升的 inset 数据源覆写（TargetHook 的通用三级回退之后调用、此处
        // 覆盖生效）：应用把小白条高度写在 nav_view（0 高 View，Activity insets 监听写入）
        // 而不是容器 padding；真机 rootWindowInsets.navigationBars 实测读 0——插入 nav_view
        // 高度取值级（nav_view 高度与原生栏几何完全一致，原生栏 = 66dp + nav_view）。
        GlassInstaller.navInsetProvider = {
            container.paddingBottom.takeIf { it > 0 }
                ?: navViewHeight(container)
                ?: runCatching {
                    container.rootWindowInsets
                        ?.getInsets(WindowInsets.Type.navigationBars())
                        ?.bottom
                }.getOrNull()?.takeIf { it > 0 }
                ?: -1
        }
        // 内容容器锚点 + 页面根初扫（fragment 视图此时可能未挂载，清扫由选中变化防抖触发兜底）
        resolveContentContainer(activity)
        pageRoots.clear()
        mainHandler.post { runCatching(::sweepPageRoots) }
        // 首帧配色（深链冷启动等装配期取不到强调色时补；无选中变化则静默空转）
        scheduleColorCapture()
    }

    // —— 底栏结构 ——

    private fun findByClass(root: View, className: String): View? {
        if (root.javaClass.name == className) return root
        (root as? ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                findByClass(group.getChildAt(i), className)?.let { return it }
            }
        }
        return null
    }

    private fun logParentChain(nav: View, tail: String) {
        val chain = ArrayList<String>(4)
        var node: Any? = nav
        while (node is View) {
            chain.add(node.javaClass.name)
            node = node.parent
        }
        android.util.Log.println(
            android.util.Log.INFO, "LiquidGlass",
            "notes bar parent chain: ${chain.joinToString(" → ")} ($tail)",
        )
    }

    // —— 拖动悬停预览的直控反射（itemCheckedApplier） ——

    /** 反射调用条目的选中翻转方法：name 带参数版 a(Z)/无参版 b()。失败 WARN 一次
     *  （单字母方法名随版本漂移 → 预览静默失效，不崩溃，§8.3）。 */
    private fun invokeItemMethod(view: View, name: String, arg: Boolean?) {
        runCatching {
            val method = if (arg == null) {
                view.javaClass.getDeclaredMethod(name)
            } else {
                view.javaClass.getDeclaredMethod(name, Boolean::class.javaPrimitiveType)
            }
            method.isAccessible = true
            if (arg == null) method.invoke(view) else method.invoke(view, arg)
        }.onFailure {
            if (!applierWarned) {
                applierWarned = true
                android.util.Log.println(
                    android.util.Log.WARN, "LiquidGlass", "notes itemCheckedApplier failed: $it",
                )
            }
        }
    }

    // —— 延迟配色捕获 ——

    // 选中变化后 350ms（文字色动画 300ms + 余量）读稳态：各条目 title 的 currentTextColor，
    // isSelected=true → 强调色、false → 内容色。每次选中变化重捕获自愈（主题/深色切换）。
    private fun scheduleColorCapture() {
        if (colorCapturePending) return
        colorCapturePending = true
        mainHandler.postDelayed({
            colorCapturePending = false
            runCatching(::captureColors)
        }, COLOR_CAPTURE_DELAY_MS)
    }

    private fun captureColors() {
        val nav = navRef?.get() as? ViewGroup ?: return
        val titleId = resolveId(nav, "title")
        if (titleId == 0) return
        var content = 0
        var accent = 0
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) ?: continue
            if (item.javaClass.name != ITEM_CLASS) continue
            val tv = item.findViewById<TextView>(titleId) ?: continue
            val color = tv.currentTextColor
            if (item.isSelected) {
                if (accent == 0) accent = color
            } else {
                if (content == 0) content = color
            }
        }
        if (content != 0 && accent != 0 && content != accent) {
            capturedContentColor = content
            capturedAccentColor = accent
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "notes colors captured: content=#${Integer.toHexString(content)} " +
                    "accent=#${Integer.toHexString(accent)}",
            )
        }
    }

    /** 条目构造期稳态配色（h=选中 / i=未选中，5.4.5.107 实证）。装配期即可读，
     *  不依赖 title 动画落定；字段改名则返回 null，由原 350ms 捕获兜底。 */
    private fun staticItemColors(): Pair<Int, Int>? {
        val nav = navRef?.get() as? ViewGroup ?: return null
        var content = 0
        var accent = 0
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) ?: continue
            if (item.javaClass.name != ITEM_CLASS) continue
            val selected = intField(item, ITEM_SELECTED_COLOR_FIELD)
            val unselected = intField(item, ITEM_UNSELECTED_COLOR_FIELD)
            if (accent == 0 && selected != 0) accent = selected
            if (content == 0 && unselected != 0) content = unselected
        }
        if (content != 0 && accent != 0 && content != accent) {
            if (!staticColorsLogged) {
                staticColorsLogged = true
                android.util.Log.println(
                    android.util.Log.INFO, "LiquidGlass",
                    "notes static colors: content=#${Integer.toHexString(content)} " +
                        "accent=#${Integer.toHexString(accent)}",
                )
            }
            return content to accent
        }
        return null
    }

    private fun intField(obj: Any?, name: String): Int {
        if (obj == null) return 0
        val field = generateSequence<Class<*>>(obj.javaClass) { it.superclass }
            .firstNotNullOfOrNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
            ?: return 0
        return runCatching { field.apply { isAccessible = true }.getInt(obj) }.getOrDefault(0)
    }

    // —— 内容延伸（待办/文档页根储备归零） ——

    // 页面根储备写入点收口（平台 View.setPadding after）：待办/文档页 fragment 根是普通
    // 包装容器，应用 insets 监听经 View.setPadding 写入小白条高度把整页抬起（玻璃条带
    // 只剩背景色）。300ms 防抖清扫追不上**首次**切页的写入（写入早于清扫，中间露小白条
    // 窗口）——收口下沉到写入点：已记录页面根写回 ≥ 储备归零；未记录视图若 parent 是
    // container_content 且非 coordinator（页面根候选）当场记录+归零，零窗口。
    private fun installPageRootSink(facade: HookFacade) {
        runCatching {
            val setPadding = View::class.java.getDeclaredMethod(
                "setPadding",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            facade.around(setPadding, null, { call ->
                val view = call.thisObj as? View ?: return@around null
                val bottom = (call.args.getOrNull(3) as? Number)?.toInt() ?: 0
                if (bottom <= 0) return@around null
                val known = synchronized(pageRoots) { pageRoots.containsKey(view) }
                if (!known && !isPageRootCandidate(view)) return@around null
                synchronized(pageRoots) { pageRoots[view] = true }
                val reserve = navReservePx(view)
                if (reserve <= 0 || bottom >= reserve) {
                    runCatching {
                        setPadding.invoke(
                            view,
                            (call.args.getOrNull(0) as? Number)?.toInt() ?: 0,
                            (call.args.getOrNull(1) as? Number)?.toInt() ?: 0,
                            (call.args.getOrNull(2) as? Number)?.toInt() ?: 0,
                            0,
                        )
                    }
                    if (!writePointHitLogged) {
                        writePointHitLogged = true
                        android.util.Log.println(
                            android.util.Log.INFO, "LiquidGlass",
                            "notes page-root reserve zeroed (${view.javaClass.name}, reserve=${reserve}px)",
                        )
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setPadding: page-root reserve re-zero (notes)")
        }.onFailure { facade.log(android.util.Log.WARN, "hook View.setPadding page-root sink failed", it) }
    }

    /** 页面根候选：parent 是 container_content 且自身非 coordinator。按 parent.id 比较
     *  判定（写入点 hook 是进程级热路径，不做 findViewById）。 */
    private fun isPageRootCandidate(v: View): Boolean {
        if (v.javaClass.simpleName == COORDINATOR_SIMPLE_NAME) return false
        val parent = v.parent as? View ?: return false
        val containerId = contentIdOf(parent)
        return containerId != 0 && parent.id == containerId
    }

    // 初扫 + 防抖重扫：container_content 直接子项中 paddingBottom>0 且非 coordinator 的
    // 页面根归零（只动 bottom，保留左右 cutout padding）。coordinator（笔记/我的页根）
    // 豁免——其根 padding 参与列表悬浮合并数学、本来就在延伸。清扫保留作初扫与结构取证。
    private fun sweepPageRoots() {
        val container = contentContainerRef?.get() ?: return
        var fixed = 0
        var lastRootClass = ""
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) ?: continue
            if (child.paddingBottom > 0 && child.javaClass.simpleName != COORDINATOR_SIMPLE_NAME) {
                synchronized(pageRoots) { pageRoots[child] = true }
                child.setPadding(child.paddingLeft, child.paddingTop, child.paddingRight, 0)
                fixed++
                lastRootClass = child.javaClass.name
            }
        }
        if (fixed > 0) {
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "notes page-root reserve zeroed ($lastRootClass, sweep fixed=$fixed)",
            )
        } else if (!sweepMissLogged) {
            sweepMissLogged = true
            android.util.Log.println(
                android.util.Log.INFO, "LiquidGlass",
                "notes page-root sweep: no padded non-coordinator root found",
            )
        }
    }

    private fun scheduleSweep() {
        if (sweepPending) return
        sweepPending = true
        mainHandler.postDelayed({
            sweepPending = false
            runCatching(::sweepPageRoots)
        }, SWEEP_DEBOUNCE_MS)
    }

    /** 内容容器 container_content（NoteSideView 内，fragment 根的挂载点）。 */
    private fun resolveContentContainer(activity: Activity) {
        if (contentIdCache == 0) {
            contentIdCache = activity.resources.getIdentifier(
                CONTENT_CONTAINER_ID_NAME, "id", activity.packageName,
            )
        }
        if (contentIdCache != 0) {
            activity.findViewById<ViewGroup>(contentIdCache)?.let { contentContainerRef = WeakReference(it) }
        }
    }

    private fun contentIdOf(v: View): Int {
        if (contentIdCache != 0) return contentIdCache
        contentIdCache = v.resources.getIdentifier(CONTENT_CONTAINER_ID_NAME, "id", v.context.packageName)
        return contentIdCache
    }

    // —— inset 取值（玻璃栏小白条抬升） ——

    /** nav_view（0 高 inset 填充视图）高度：与原生栏几何完全一致。未布局/失配返回 null。 */
    private fun navViewHeight(container: View): Int? {
        if (navViewIdCache == 0) {
            navViewIdCache = container.resources.getIdentifier(
                NAV_VIEW_ID_NAME, "id", container.context.packageName,
            )
        }
        if (navViewIdCache == 0) return null
        return container.findViewById<View>(navViewIdCache)?.height?.takeIf { it > 0 }
    }

    /** 储备阈值 = 小白条高度（nav_view 高度；nav_view 在 nav_container 内，是页面根的
     *  兄弟子树，从 rootView 向下找）。未布局/失配返回 0 = 写入点对页面根的任意正底部
     *  padding 归零（页面根的底部 padding 只会来自 inset 写入）。 */
    private fun navReservePx(v: View): Int {
        if (navViewIdCache == 0) {
            navViewIdCache = v.resources.getIdentifier(
                NAV_VIEW_ID_NAME, "id", v.context.packageName,
            )
        }
        if (navViewIdCache == 0) return 0
        return v.rootView.findViewById<View>(navViewIdCache)?.height ?: 0
    }

    private fun resolveId(v: View, name: String): Int =
        v.resources.getIdentifier(name, "id", v.context.packageName)
}
