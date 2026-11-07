package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * vivo 文件管理（com.android.filemanager，桌面名"文件管理"）适配器。
 *
 * - 主页链路：主 Tab Activity = [TAB_ACTIVITY]（launcher 途经 FileManagerRouterActivity
 *   落到它），主页是 PhoneHomeFragment（布局 activity_main_category），底栏挂在其中
 *   的 `main_navigation`（LinearLayout）内：`main_navigation_divider`（VDivider 分割线）
 *   + `main_navigation_view`（[BAR_CLASS]，非混淆 originui 库类）。
 * - 每 tab 视图：内部 menuLayout（com.originui.widget.navigation.VMenuViewLayout）的
 *   直接子项 = `com.originui.widget.navigation.navigation.VNavMenuItem`（与相册同库同
 *   结构；本应用 originui **非混淆**，setChecked 完整存在：末尾 setSelected(z10)，选中
 *   读数 = 标准 isSelected）。tab 数动态：2 或 3（跨设备 tab 受设备能力开关）。
 * - 图标：`l(String, Drawable)`/`m(...)` 挂静态 symbol drawable（m() 双 Lottie 变体也
 *   以 setIcon 落到图标 ImageView），元素级直绘天然跟随；无 setChecked 切图逻辑。
 * - 可见性门控（确证的真实路径，非假想）：主页进入搜索态时应用把 main_navigation
 *   置 GONE（PhoneHomeFragmentViewHelper 搜索动画回调）、退出恢复；平板/折叠态（Z2()）
 *   也直接 GONE。原生条目在 GONE 的祖先下不绘制，玻璃 overlay 在 content 层不受影响，
 *   会成为"残留玻璃栏"——门控读条目 `isShown`（逐帧求值，覆盖任意祖先 GONE）驱动
 *   引擎 barWanted 隐藏/恢复整条玻璃栏。
 * - 内容延伸：主页内容 FrameLayout 本就全屏延伸到底（RelativeLayout 同层叠加），
 *   未确认列表留白问题，Noop（真机反馈确认后再按 §0.4 补专属策略）。
 */
object FileManagerAdapter : AppAdapter {

    override val targetPackage: String = "com.android.filemanager"
    override val displayName: String = "vivo 文件管理"
    override val tabActivityClassName: String? = "com.android.filemanager.FileManagerActivity"
    override val baselineVersions: List<String> = listOf("10.0.16.0")

    private const val TAB_ACTIVITY = "com.android.filemanager.FileManagerActivity"
    // 底栏类（非混淆 originui 库类）与其在主页布局中的资源 id（类名失配兜底）
    private const val BAR_CLASS = "com.originui.widget.navigation.VBottomNavigationView"
    private const val BAR_ID_NAME = "main_navigation_view"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/vivo 官网同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"
    // 底栏容器上方的分割线（VDivider，与底栏同在 main_navigation 内）：
    // 玻璃栏只压制底栏本体，它残留为玻璃上缘边界线（真机实证 0x7f0a03a5），装配期 GONE
    private const val DIVIDER_ID_NAME = "main_navigation_divider"
    // 文件页存储状态条（SpaceSituationLinearLayout，fragment_file_category）：辅助玻璃视图
    // —— 原生纯色矩形背景改为与玻璃底栏同款胶囊玻璃底（压制 + 镜像 + 引擎渲染，§9.2）
    private const val SPACE_SITUATION_ID_NAME = "space_situation"

    // —— 辅助玻璃运行期状态（弱引用防泄漏；原状态供会话还原） ——
    @Volatile private var hostActivityRef: WeakReference<Activity>? = null
    @Volatile private var barRef: WeakReference<View>? = null
    @Volatile private var dividerIdCache = 0
    /** 外部分割线的同类型替身（微博先例 §5.2）：吸收应用对 main_navigation_divider 的
     *  裸引用（如设背景色），0×0 + GONE 永不可见。 */
    @Volatile private var standInRef: WeakReference<View>? = null
    /** 已移除/中性化的底栏装饰视图（外部 VDivider + 底栏内部顶边分割线等非 menuLayout
     *  子视图）。装饰经四期定案**物理移除**出视图树（微博方案）：应用滚动路径存在逐帧
     *  翻装饰可见性的机制族（lc.a/OS5VirtualSharedHeaderView 实证），三期的"GONE +
     *  中性化 + 逐帧重申"仍输给 traversal 阶段的写入（新加/恢复渲染能力的装饰当帧
     *  渲染，滑动期间每帧画线）——移除后应用的可见性/背景写入落在游离视图上（内部
     *  字段方法全判空），帧循环扫描对**新加**装饰检出即移除（一次性取证日志）。原状态
     *  供会话还原（重新挂载）。 */
    private val hiddenDecorations = ArrayList<DecorationState>(2)

    private class DecorationState(
        val view: View,
        val parent: ViewGroup?,
        /** 移除时的挂载位置；-1 = 未移出树（仅中性化，如新加装饰先中性化的降级路径）。 */
        val index: Int,
        val visibility: Int,
        val height: Int,
        val background: Drawable?,
        val minHeight: Int,
    )
    @Volatile private var spaceViewRef: WeakReference<View>? = null
    /** 已压制实例（防重复记录原状态；视图重建后对新实例重新压制）。 */
    @Volatile private var spaceSuppressedFor: View? = null
    private var spaceOriginalAlpha = 1f
    private var spaceOriginalBackground: Drawable? = null
    private var spaceOriginalWidth = ViewGroup.LayoutParams.MATCH_PARENT
    private var spaceOriginalHeight = ViewGroup.LayoutParams.MATCH_PARENT
    private var spaceOriginalMinHeight = 0
    private var spaceOriginalGravity = -1
    private var spaceOriginalPaddingL = 0
    private var spaceOriginalPaddingR = 0
    private var spaceOriginalPaddingT = 0
    private var spaceOriginalPaddingB = 0
    private var spaceOriginalMarginL = 0
    private var spaceOriginalMarginR = 0

    /** 玻璃底左右外边距（每侧 dp）：存储条内容（进度条等全宽元素）固有宽度接近满屏，
     *  wrap_content 收不回来、调 padding 无效——宽度保持原生 match_parent，用 margin
     *  限制胶囊宽度，胶囊不再顶屏幕边缘（用户观感调整项，2.9.19；与玻璃底栏最小安全
     *  边距 minHorizontalMarginDp 默认 20dp 同源，观感和谐）。内部进度条随之略窄，
     *  图标/文字大小不变。 */
    private const val AUX_SIDE_MARGIN_DP = 20f
    /** 玻璃底纵向收拢后每侧保留的垂直内边距（dp）：不清零——内容距胶囊弧形端部保留
     *  呼吸感（接近底栏"栏高 60dp / 内容行 52dp"的比例观感）。收拢目的 = 胶囊高度进入
     *  玻璃 overlay 顶部余量（GlassRoot 内层盒 48dp）之内，越界被 overlay 上缘裁切
     *  （"上半部分显示不全"）的整类问题消失；内容大小完全不动。 */
    private const val AUX_KEEP_VERTICAL_DP = 6f

    @Volatile private var spaceSituationId = 0

    private fun isBottomBar(v: View): Boolean =
        v.javaClass.name == BAR_CLASS || v.javaClass.simpleName == "VBottomNavigationView"

    private fun idByName(v: View, name: String): Int =
        v.resources.getIdentifier(name, "id", v.context.packageName)

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐相册）
        elementIds = mapOf(
            "icon" to ICON_ID_NAME,
            "labels" to LABELS_ID_NAME,
        ),
        // 选中外观翻转方法 = 默认 setChecked（本应用 originui 非混淆，方法完整存在：
        // 末尾 setSelected(z10)，无切页副作用），无需声明 itemCheckedSetterMethodName
        // 玻璃栏标签精简：第三 tab 原生文案"跨设备访问"过长（超出玻璃槽位），显示为
        // "跨设备"（EN 环境同步精简）；仅作用于玻璃栏自绘标签
        labelOverrides = mapOf(
            "跨设备访问" to "跨设备",
            "Cross-device access" to "Cross-device",
        ),
        // 标签悬停强调色回退：应用标签色 filemanager_home_tab_color 为纯色（非选择器，
        // 原生选中态靠标签缩放表达）→ 取色 强调色==内容色/缺失 → 悬停切换在视觉上不
        // 存在。回退到液泡折射染色的强调色（引擎默认蓝，与气泡观感一致），符合 §1.2
        // 悬停语义（悬停 tab = 强调色、原选中 = 内容色）
        labelAccentFallbackToBubble = true,
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun findTabBar(content: ViewGroup): View? {
        val found = ArrayList<View>(2)
        fun walk(v: View?) {
            if (v == null || found.size >= 2) return
            if (isBottomBar(v)) {
                found.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(content)
        if (found.isEmpty()) return null
        // 多实例时优先主页底栏（id main_navigation_view）；单实例直接返回
        if (found.size > 1) {
            val barId = idByName(content, BAR_ID_NAME)
            if (barId != 0) found.firstOrNull { it.id == barId }?.let { return it }
        }
        return found.first()
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

    /** 取证日志用的视图描述（类名 + id 名）。 */
    private fun describeView(v: View): String {
        val idName = if (v.id != 0 && v.id != -1) {
            runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
                ?: "0x${Integer.toHexString(v.id)}"
        } else "no-id"
        return "${v.javaClass.name}($idName)"
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }

    // —— discolor 双 drawable（预览换图数据源） ——
    /** 按 tab 注册顺序记录的 (选中 drawable, 非选中 drawable)。应用 initView 的 J7 按
     *  tab 0/1/2 顺序调用底栏 m()/l()（非混淆公开方法，进程加载期 hook 先于注册），
     *  记录序 = tab 序。选中态 = 品牌色 drawable（filemanager_home_tab_color）、非选中
     *  态 = 灰色 drawable（main_bottom_icon_back）；l() 单 drawable 路径两者相同。 */
    private val tabIconPairs = ArrayList<Pair<Drawable?, Drawable?>>(4)
    /** 符号系统重着色入口（混淆类 `pj.a` 单例，随版本漂移风险）：换图后必须调用，符号
     *  系统按当前主题/checked 态给 drawable 重新着色——裸 setIcon 会留下构造时的浅色
     *  资源色（深色背景下呈黑色）。原生配对：选中路径 `H()` = `r(drawable, 1.0f)`
     *  （animProgress 直驱满进度）；回退路径 `r()` = `U(drawable)`（start() 状态动画）。
     *  懒反射解析，失败 = null（降级为裸 setIcon）。 */
    private class SymbolDriver(
        val setProgress: (Drawable, Float) -> Unit,
        val start: (Drawable) -> Unit,
    )

    @Volatile private var symbolDriver: SymbolDriver? = null
    @Volatile private var symbolDriverResolved = false
    @Volatile private var appClassLoader: ClassLoader? = null

    /** 懒解析符号系统 `pj.a.e()` 单例的 `r(Object, float)` 与 `U(Object)`。 */
    private fun resolveSymbolDriver(loader: ClassLoader?): SymbolDriver? {
        symbolDriver?.let { return it }
        if (symbolDriverResolved) return symbolDriver
        symbolDriverResolved = true
        if (loader == null) return null
        val resolved = runCatching {
            val cls = Class.forName("pj.a", true, loader)
            val manager = cls.getMethod("e").apply { isAccessible = true }.invoke(null)
            val r = cls.getMethod("r", Any::class.java, Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
            val u = cls.getMethod("U", Any::class.java).apply { isAccessible = true }
            SymbolDriver(
                setProgress = { d, f -> r.invoke(manager, d, f) },
                start = { d -> u.invoke(manager, d) },
            )
        }.getOrNull()
        symbolDriver = resolved
        return resolved
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        appClassLoader = loader
        runCatching {
            val barClass = Class.forName(BAR_CLASS, true, loader)
            val addAnimated = barClass.getDeclaredMethod(
                "m",
                String::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Drawable::class.java,
                Drawable::class.java,
            )
            val addPlain = barClass.getDeclaredMethod("l", String::class.java, Drawable::class.java)
            facade.around(addAnimated, null, { call ->
                synchronized(tabIconPairs) {
                    tabIconPairs.add(
                        (call.args.getOrNull(4) as? Drawable) to (call.args.getOrNull(5) as? Drawable),
                    )
                }
                null
            })
            facade.around(addPlain, null, { call ->
                val drawable = call.args.getOrNull(1) as? Drawable
                synchronized(tabIconPairs) { tabIconPairs.add(drawable to drawable) }
                null
            })
            facade.log(android.util.Log.INFO, "hooked VBottomNavigationView m/l: discolor icon pairs recorder")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook VBottomNavigationView m/l failed (app updated?)", it)
        }
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        hostActivityRef = WeakReference(activity)
        suppressBarDecorations(session)
        // 辅助玻璃：文件页存储状态条与玻璃底栏同款胶囊玻璃底（引擎逐帧镜像 + 玻璃绘制，
        // 压制与还原由本适配器负责）
        session.auxGlassViews = { provideAuxGlassViews() }
        session.onAuxGlassRestore = { restoreAuxGlass() }
        // 预览换图（discolor 双 drawable）：引擎 setTabChecked 翻转后调用——悬停 tab 换
        // 选中态（品牌色）drawable、被拖离的原选中换非选中态（灰色），与商店"气泡内
        // 图标 = 换图后选中态资源的染色副本"机制对齐（setChecked 不换图，换图走应用
        // 自身 H()/r() 路径，预览期间由本闭包代驱动）
        session.previewIconSwapper = { index, checked ->
            swapPreviewIcon(session, index, checked)
        }
        // 可见性门控顺带逐帧重申装饰隐藏（防应用/theme 路径翻回可见性；两次可见性
        // 比较的开销可忽略）
        session.mirrorVisibleGate = {
            reassertBarDecorations()
            session.tabViews.firstOrNull()?.isShown == true
        }
    }

    /** 隐藏底栏上缘的边界线装饰（真机实证两处）：
     *  ①外部 VDivider：main_navigation 内、底栏上方的 `main_navigation_divider`
     *    （0x7f0a03a5，一期 GONE 已生效）；
     *  ②底栏内部顶边分割线：VBottomNavigationView 构造器在 index 0 挂载的**无 id**
     *    View（divider line，应用 setDividerLineAlpha(0) 理论隐形，真机仍见线——布局
     *    读取工具显示其为 main_navigation_view 的无 id 子视图，存在绕过 view alpha 的
     *    渲染路径，vivo VBlur 接管背景绘制嫌疑最大）。斩断方式：调用底栏自身公开 API
     *    `setDividerLineVisibility(false)`（→ setVisibility(GONE)）+ 结构兜底 GONE 底栏
     *    全部非 menuLayout 直接子视图（覆盖 API 改名/未来新增装饰）。GONE 移出遍历，
     *    同时斩断常规绘制与模糊层取源；menuLayout（tab 条目容器）不可动。 */
    /** 移除底栏上缘的边界线装饰（真机实证两处，四期定案 = 微博方案 §5.2）：
     *  ①外部 VDivider：main_navigation 内、底栏上方的 `main_navigation_divider`
     *    （0x7f0a03a5）——移除真身 + **同类型替身**（同 id、0×0、GONE）吸收应用裸引用
     *    （V0.setBackgroundColor，View 类型引用）；
     *  ②底栏内部顶边分割线：VBottomNavigationView 构造器在 index 0 挂载的**无 id**
     *    View（divider line）——直接移除。该应用对底栏装饰存在逐帧翻可见性的滚动机制
     *  族（lc.a → tf.c0 "OS5VirtualSharedHeaderView" 的 m(1,0/8) 实证同类模式），GONE/
     *  中性化都输给 traversal 阶段写入；移除后相关写入落在游离视图上（底栏内部字段
     *  方法 o0.s1/v1/p0 全判空），帧循环扫描对运行期新加的装饰检出即移除（见
     *  [reassertBarDecorations]）。menuLayout（tab 条目容器）不可动。 */
    private fun suppressBarDecorations(session: GlassSession) {
        val bar = session.tabContainer
        barRef = WeakReference(bar)
        // ① 外部 VDivider：移除真身 + 同类型替身
        val parent = bar.parent as? ViewGroup
        if (dividerIdCache == 0) {
            dividerIdCache = idByName(bar, DIVIDER_ID_NAME)
        }
        if (parent != null && dividerIdCache != 0) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child.id == dividerIdCache && child !== standInRef?.get()) {
                    installStandIn(parent, i, child, bar)
                    break
                }
            }
        }
        // ② 底栏内部装饰：移除（倒序防移除后索引漂移）
        (bar as? ViewGroup)?.let { group ->
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                if (child.javaClass.simpleName != MENU_LAYOUT_SIMPLE_NAME) {
                    removeDecoration(group, i, child)
                }
            }
        }
    }

    /** 移除一条装饰并记录原状态（幂等）。 */
    private fun removeDecoration(parent: ViewGroup, index: Int, view: View) {
        if (hiddenDecorations.any { it.view === view }) return
        android.util.Log.println(
            android.util.Log.INFO, "LiquidGlass",
            "fm decoration removed: ${describeView(view)} index=$index",
        )
        hiddenDecorations.add(
            DecorationState(
                view = view,
                parent = parent,
                index = index,
                visibility = view.visibility,
                height = view.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                background = view.background,
                minHeight = view.minimumHeight,
            )
        )
        parent.removeView(view)
    }

    /** 外部分割线的同类型替身（微博先例 §5.2）：同类型（吸收 setBackground 等 View 级
     *  调用）、同 id（吸收按 id 的 findViewById 重查）、0×0 + GONE（不参与布局/绘制）。
     *  构造失败回退普通 View（V0 为 View 类型引用，够用）。 */
    private fun installStandIn(parent: ViewGroup, index: Int, real: View, bar: View) {
        removeDecoration(parent, index, real)
        val standIn = runCatching {
            real.javaClass.getConstructor(android.content.Context::class.java)
                .newInstance(bar.context) as View
        }.getOrElse { View(bar.context) }
        standIn.id = real.id
        standIn.visibility = View.GONE
        runCatching {
            parent.addView(standIn, index, android.widget.LinearLayout.LayoutParams(0, 0))
        }.onFailure {
            parent.addView(standIn, index)
        }
        standInRef = WeakReference(standIn)
    }

    /** 逐帧结构化扫描（mirrorVisibleGate 顺带执行）：①底栏全部非 menuLayout 子视图、
     *  ②main_navigation 内 dividerId 视图（替身除外）——检出即**移除**（微博方案不变量：
     *  底栏子树只剩 menuLayout、main_navigation 只剩 [替身, 底栏]），覆盖运行期新加的
     *  装饰；已移除的不会出现在树里，稳态零写入。 */
    private fun reassertBarDecorations() {
        val bar = barRef?.get() ?: return
        (bar as? ViewGroup)?.let { group ->
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                if (child.javaClass.simpleName != MENU_LAYOUT_SIMPLE_NAME) {
                    removeDecoration(group, i, child)
                }
            }
        }
        if (dividerIdCache == 0) return
        val parent = bar.parent as? ViewGroup ?: return
        val standIn = standInRef?.get()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.id == dividerIdCache && child !== standIn) {
                removeDecoration(parent, i, child)
            }
        }
    }

    /** 辅助玻璃视图提供者（引擎帧循环每 tick 调用）：返回文件页存储状态条
     *  space_situation（弱引用缓存，失联/重建后按 id 重查）。文件 tab 之外该视图
     *  不存在或不可见，返回空列表 = 胶囊不渲染。 */
    private fun provideAuxGlassViews(): List<View> {
        val cached = spaceViewRef?.get()
        val view = if (cached != null && cached.isAttachedToWindow) {
            cached
        } else {
            val activity = hostActivityRef?.get() ?: return emptyList()
            if (spaceSituationId == 0) {
                spaceSituationId = activity.resources.getIdentifier(
                    SPACE_SITUATION_ID_NAME, "id", activity.packageName,
                )
            }
            if (spaceSituationId == 0) return emptyList()
            // fragment_file_category 内的视图，挂载后经 activity 树可达
            val found = activity.findViewById<View>(spaceSituationId) ?: return emptyList()
            spaceViewRef = WeakReference(found)
            found
        }
        suppressSpaceView(view)
        return listOf(view)
    }

    /** 压制存储状态条原生外观（幂等，仅首见实例记录原状态）：
     *  - 背景置空：引擎镜像走 `View.draw()`（含 drawBackground），不置空会把纯色矩形
     *    画进玻璃胶囊；
     *  - alpha=0：原生内容隐藏但布局占位与点击保留（alpha 不影响输入，"清理"入口照常；
     *    `View.draw()` 手动调用不施加 alpha，引擎镜像仍取到完整内容，无鬼影）；
     *  - 左右收拢：宽度保持原生 match_parent，加对称左右 margin（[AUX_SIDE_MARGIN_DP]，
     *    原值记录供还原）——存储条内容固有宽度接近满屏，wrap_content 收不回来、调
     *    padding 无效，用 margin 限宽胶囊才不顶屏幕边缘（内容居中不变，进度条略窄）；
     *  - 纵向收拢：上下 padding 收拢到 [AUX_KEEP_VERTICAL_DP]（不清零、不放大原值）+
     *    固定高度 → wrap_content + minimumHeight 置 0 + 垂直居中（原值全部记录供还原）
     *    ——玻璃胶囊 1:1 镜像视图边界，收拢后胶囊高度进入玻璃 overlay 顶部余量（48dp）
     *    之内，越界被 overlay 上缘裁切（"上半部分显示不全"）的整类问题消失；内容
     *    （图标/文字）大小完全不动。 */
    private fun suppressSpaceView(view: View) {
        if (spaceSuppressedFor === view) return
        spaceSuppressedFor = view
        spaceOriginalAlpha = view.alpha
        spaceOriginalBackground = view.background
        spaceOriginalWidth = view.layoutParams?.width ?: ViewGroup.LayoutParams.MATCH_PARENT
        spaceOriginalHeight = view.layoutParams?.height ?: ViewGroup.LayoutParams.MATCH_PARENT
        spaceOriginalMinHeight = view.minimumHeight
        spaceOriginalGravity = (view.layoutParams as? LinearLayout.LayoutParams)?.gravity ?: -1
        spaceOriginalPaddingL = view.paddingLeft
        spaceOriginalPaddingR = view.paddingRight
        spaceOriginalPaddingT = view.paddingTop
        spaceOriginalPaddingB = view.paddingBottom
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            spaceOriginalMarginL = it.leftMargin
            spaceOriginalMarginR = it.rightMargin
        }
        view.background = null
        view.alpha = 0f
        view.layoutParams?.let { lp ->
            if (lp.height > 0) lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            (lp as? ViewGroup.MarginLayoutParams)?.let {
                val sidePx = (AUX_SIDE_MARGIN_DP * view.resources.displayMetrics.density).toInt()
                it.leftMargin = sidePx
                it.rightMargin = sidePx
            }
            (lp as? LinearLayout.LayoutParams)?.let {
                it.gravity = (spaceOriginalGravity.takeIf { g -> g >= 0 }
                    ?: Gravity.CENTER_HORIZONTAL) or Gravity.CENTER_VERTICAL
            }
            view.layoutParams = lp
        }
        view.minimumHeight = 0
        val keepVPx = (AUX_KEEP_VERTICAL_DP * view.resources.displayMetrics.density).toInt()
        view.setPadding(
            spaceOriginalPaddingL,
            minOf(spaceOriginalPaddingT, keepVPx),
            spaceOriginalPaddingR,
            minOf(spaceOriginalPaddingB, keepVPx),
        )
    }

    /** 预览换图：按注册序取该 tab 的双 drawable，checked → 选中态（品牌色）、否则非
     *  选中态（灰色）setIcon 到条目，并按方向复刻应用自身的符号系统配对调用——选中
     *  路径 `H()` = `r(drawable, 1.0f)`（animProgress 直驱满进度）、回退路径 `r()` =
     *  `U(drawable)`（start() 状态动画）。缺符号系统调用时 drawable 留构造时的浅色
     *  资源色，深色背景呈黑色（无法按主题重着色）。双 drawable 相同（l() 单 drawable
     *  路径）或未记录时跳过。 */
    private fun swapPreviewIcon(session: GlassSession, index: Int, checked: Boolean) {
        val pair = synchronized(tabIconPairs) { tabIconPairs.getOrNull(index) } ?: return
        val (selected, unselected) = pair
        if (selected == null || unselected == null || selected === unselected) return
        val item = session.tabViews.getOrNull(index) ?: return
        val drawable = if (checked) selected else unselected
        runCatching {
            item.javaClass
                .getMethod("setIcon", Drawable::class.java)
                .apply { isAccessible = true }
                .invoke(item, drawable)
        }
        val driver = resolveSymbolDriver(appClassLoader) ?: return
        runCatching {
            if (checked) driver.setProgress(drawable, 1.0f) else driver.start(drawable)
        }
    }

    /** 会话还原（模块关闭/视图树重建）：移除替身、重新挂载被移除的装饰（倒序恢复原
     *  挂载位置）并恢复原状态（可见性/高度/背景/minimumHeight），以及存储状态条原
     *  alpha/背景/宽度。 */
    private fun restoreAuxGlass() {
        standInRef?.get()?.let { standIn ->
            (standIn.parent as? ViewGroup)?.removeView(standIn)
        }
        standInRef = null
        for (d in hiddenDecorations.reversed()) {
            val stillDetached = d.view.parent == null
            if (d.index >= 0 && stillDetached && d.parent != null) {
                runCatching {
                    d.parent.addView(
                        d.view,
                        d.index.coerceAtMost(d.parent.childCount),
                    )
                }
            }
            d.view.visibility = d.visibility
            d.view.minimumHeight = d.minHeight
            d.background?.let { d.view.background = it }
            d.view.layoutParams?.let { lp ->
                if (lp.height != d.height) {
                    lp.height = d.height
                    d.view.layoutParams = lp
                }
            }
        }
        hiddenDecorations.clear()
        spaceSuppressedFor?.let { v ->
            v.alpha = spaceOriginalAlpha
            spaceOriginalBackground?.let { v.background = it }
            v.layoutParams?.let { lp ->
                lp.width = spaceOriginalWidth
                lp.height = spaceOriginalHeight
                (lp as? ViewGroup.MarginLayoutParams)?.let {
                    it.leftMargin = spaceOriginalMarginL
                    it.rightMargin = spaceOriginalMarginR
                }
                if (spaceOriginalGravity >= 0) {
                    (lp as? LinearLayout.LayoutParams)?.gravity = spaceOriginalGravity
                }
                v.layoutParams = lp
            }
            v.minimumHeight = spaceOriginalMinHeight
            v.setPadding(
                spaceOriginalPaddingL,
                spaceOriginalPaddingT,
                spaceOriginalPaddingR,
                spaceOriginalPaddingB,
            )
        }
        spaceSuppressedFor = null
        spaceViewRef = null
    }
}
