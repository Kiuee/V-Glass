package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * vivo 闹钟时钟（com.android.BBKClock，vivo 系统应用，版本 8.0.3.7 取证）适配器。
 *
 * - 主 Tab Activity：com.android.BBKClock.Timer（launcher alias com.android.deskclock.DeskClock
 *   指向它；四页 = 闹钟/世界时钟/秒表/计时器 Fragment 经 f1431l 切换，布局 activity_timer：
 *   ConstraintLayout main_content_layout > fl_fragment_container（0dp 上下贴满，内容天然
 *   全出血延伸到底，无内容延伸诉求）+ [BAR_CLASS]（id [BAR_ID_NAME]，constraint 底部
 *   overlay）+ vs_markup_view 悬浮工具条（页面级浮层，本适配不动）+ NavigationBarFakeView
 *   （setShow(false) 恒隐藏）。仅此 Activity 探测。
 * - 底栏：originui 库 [BAR_CLASS]（非混淆，6.1.0.8）**直接使用**（应用无自有子类，健康
 *   №11 是应用自有子类 MainBottomNavigationView——本应用是首个直接用库类的适配）。
 *   构造器 index 0 挂载无 id 顶边分割线 View（库内字段，高度按 ROM 版本 dimen，应用仅在
 *   全局主题模式 setDividerLineVisibility(false)，普通模式保持可见）——同库装饰存在绕过
 *   view alpha 的渲染嫌疑（健康 №11/文件管理/手机管家实证），走物理移除（微博方案）。
 * - 每 tab 视图：menuLayout（com.originui.widget.navigation.VMenuViewLayout）直接子项 =
 *   `com.originui.widget.navigation.navigation.VNavMenuItem`（**非混淆**）。元素 id 与同库
 *   应用一致（`vivo_navigation_bar_item_icon_view` / `vivo_navigation_bar_item_labels_group`）。
 *   恒 4 tab（闹钟/世界时钟/秒表/计时器，应用无 tab 增删路径）。
 * - 选中机制：VNavMenuItem 有**真 `setChecked(boolean)`**（public，纯视觉翻转 + 末尾
 *   setSelected(z)，标准 isSelected 读数），无需声明 itemCheckedSetterMethodName。
 * - 图标三形态（Activity o0() 按运行时标志三选一，字段单字母真名经 dexsql 核验）：
 *   ① 普通矢量（j0()）：未选中 = `{clock,worldtime,timer,jishi}` 24dp 灰矢量（字段 `e`）、
 *   选中落定 = `*_sel` 主题色矢量（字段 `f`）；切页 = `*_animated_on/off` AVD 播放（字段
 *   `i`/`j`）；② 动态色变体（k0()）：仅换 `f`/`i`/`j` 为 `*_dynamic_color_*`，`e` 不变；
 *   ③ ROM16 动态符号（f1437r && f1438s，n0() 经库 k() 注册）：选中/未选中 FontSymbolDrawable
 *   对（主题色/灰），存字段 `g`/`h`（ArrayList<Drawable>）并按库配方 r(drawable,1.0f) 驱动
 *   到落定进度。三种形态的**落定**外观都取自 Activity 字段（拖动悬停预览注入
 *   [session.previewIconSwapper]：**绕过 VNavMenuItem.setIcon 直挂图标 ImageView**——
 *   其开头实例去重 `drawable == f6463r` 会在同 tab 二次换入时提前 return，让预览图标
 *   停在低进度副本呈未选中观感，真机实证；每次换图 = 全新副本 + 挂载前驱动落定，
 *   不走 U(index,title,res) 库换图口——AVD 分支会 reset()+start() 重播动画）。
 * - 图标尺寸随形态：普通矢量 24dp（rom15 dimen）、符号/全局主题 32dp（rom16 dimen /
 *   setItemIconSize(tab_icon_size=32dp)）——descriptor.iconBoxDp 为计算属性，[tabViewsOf]
 *   时读条目图标视图实际布局尺寸（px→dp）按形态覆盖，默认 null（24dp）= 普通矢量逐位不变。
 * - 切页链路：条目 performClick → menuLayout 监听 → setChecked 翻转 + f6377a 回调 →
 *   库 H(item)（换图 M(item)——本应用普通/符号模式的实际换图都在 Activity I0() 的
 *   U/V 调用）→ g 监听 → Timer.z0(MenuItem) → I0(index)（切 Fragment + 换图）。已选中
 *   tab 再点走 P(index,…) 注册口 → z0 同 tab 分支（回顶类，闹钟/世界时钟/计时器列表回顶）。
 *   通用 performClick 转发全覆盖，无需 tabClickHandler。
 * - 标签色 = itemTextColor 真选择器（`tab_navigation_bar_text_color`：灰 → theme_color
 *   state_selected 两态），取色即得强调色/内容色，无需 colorOverride。
 * - 显隐跟随：悬浮工具条（BottomToolbarManager m.u，秒表/闹钟批量操作页惰性展开）
 *   出现/收起时对底栏做 translationY 0 ↔ 滑出 的 400ms 对滑动画（jadx 实锤）——
 *   mirrorVisibleGate 过半 Y 门控（原子笔记/小红书标准语义）；其余时间无隐藏路径。
 * - 冷启动闪现：底栏在 Activity 布局（setContentView 即装配）而探测/会话装配晚于首帧
 *   （真机同构场景已三度实证：手机管家/主题/健康），hook [BAR_CLASS] 的
 *   `(Context, AttributeSet)` 构造器做早期压制（按 [BAR_ID_NAME] id 门控 + 8s 兜底还原
 *   + 会话认领三段式）；**压制时一并 `setBlurEnable(false)` 中性化**——库构造器尾 `q()`
 *   在系统全局模糊开启时给栏挂 vivo 动态模糊（RenderEffect/mirror 实现，**绕过 view
 *   alpha**，真机表现为磨砂底条闪现），onResume 另有按 id 的未认领实例兜底压制。
 * - 主 tab 无 WebView、无 GL 内容——角标镜像与采样冻结通道均不投入。
 */
object VivoAlarmClockAdapter : AppAdapter {

    override val targetPackage: String = "com.android.BBKClock"
    override val displayName: String = "闹钟时钟"
    override val tabActivityClassName: String? = TAB_ACTIVITY
    override val baselineVersions: List<String> = listOf("8.0.3.7")

    private const val TAB_ACTIVITY = "com.android.BBKClock.Timer"
    // 底栏（originui 库类，应用直接使用、非混淆）与其在 activity_timer.xml 中的资源 id
    // （类名失配兜底）
    private const val BAR_CLASS = "com.originui.widget.navigation.VBottomNavigationView"
    private const val BAR_ID_NAME = "tabLayout"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/文件管理/手机管家/健康同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"

    // Timer Activity 的图标资源字段（单字母真名，dexsql 对 8.0.3.7 核验；随版本漂移
    // 风险最高——失配 = 预览图标换图静默降级为资源名兜底，不崩溃）：
    // e = int[] 未选中静态图标 res（clock/worldtime/timer/jishi）
    // f = int[] 选中落定静态图标 res（*_sel 或 *_dynamic_color_sel）
    // g/h = ArrayList<Drawable> ROM16 符号选中/未选中 drawable 对（FontSymbolDrawable）
    private const val FIELD_ICONS_NORMAL = "e"
    private const val FIELD_ICONS_SELECTED = "f"
    private const val FIELD_SYMBOLS_SELECTED = "g"
    private const val FIELD_SYMBOLS_UNSELECTED = "h"

    // —— 压制运行期状态（弱引用防泄漏；原状态供会话还原） ——
    @Volatile private var barRef: WeakReference<View>? = null
    @Volatile private var sessionRef: WeakReference<GlassSession>? = null
    /** Timer Activity（预览图标对的读取源：字段 e/f/g/h 都在其上）。 */
    @Volatile private var activityRef: WeakReference<Activity>? = null

    /** 已移除的底栏内部装饰（构造器 index 0 的无 id 顶边分割线等非 menuLayout 子视图），
     *  记录原挂载位置与可见性，会话 restore 重挂。 */
    private val hiddenDecorations = ArrayList<DecorationState>(2)

    /** 预览图标状态强制（真值源 = 条目 `isSelected`，事件与逐帧统一收敛）：
     *  - 符号模式落定实例缓存（源 drawable → 全新副本 + 驱动 1.0f 一次成型）；
     *  - 普通矢量/动态色落定实例缓存（resId → 解析 drawable）。
     *  WeakHashMap：主题/形态重放（j0()/k0()/n0() 整体重建源列表）后旧源键自然失效、
     *  新源首次消费时重建；实例未被引用后可回收。 */
    private val settledSymbolCache = java.util.WeakHashMap<android.graphics.drawable.Drawable, android.graphics.drawable.Drawable>()
    private val settledResCache = java.util.WeakHashMap<Int, android.graphics.drawable.Drawable>()
    /** 条目 → 图标 ImageView 缓存（逐帧强制路径免 findViewById）。 */
    private val iconViewCache = java.util.WeakHashMap<View, android.widget.ImageView?>()

    /** 条目图标盒实测尺寸（dp，null = 未捕获/默认 24dp）。[tabViewsOf] 装配期捕获：
     *  图标视图布局宽高由形态决定（普通矢量 24dp / 符号或全局主题 32dp），在 descriptor
     *  计算属性里消费——安装（createExtractor 之后）晚于捕获，取值即形态准确值。 */
    @Volatile private var observedIconBoxDp: Int? = null

    private class DecorationState(
        val view: View,
        val parent: ViewGroup,
        val index: Int,
        val visibility: Int,
    )

    /** 描述符：iconBoxDp 按实测覆盖（1:1 镜像原生尺寸，健康 №11 iconBoxDp=27 的同款
     *  诉求但本应用尺寸随形态二值——实测驱动），其余全部默认值。 */
    override val descriptor: TabBarDescriptor
        get() {
            val base = TabBarDescriptor(
                containerClassName = BAR_CLASS,
                iconMode = IconMode.WHOLE_TAB,
                fallbackProbe = false,
                // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景
                // （指示器在本应用恒禁用——应用未设 itemActiveIndicatorStyle 且 l() 未启用）
                elementIds = mapOf(
                    "icon" to ICON_ID_NAME,
                    "labels" to LABELS_ID_NAME,
                ),
            )
            val observed = observedIconBoxDp ?: return base
            // 与引擎默认盒（24dp）一致时不覆盖：普通矢量形态下其余应用/默认路径逐位不变
            return if (observed > 24) base.copy(iconBoxDp = observed) else base
        }

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
        // 结构兜底：类名失配时按 id 名找底栏
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
        val items = ArrayList<View>()
        for (layout in menuLayouts) {
            for (i in 0 until layout.childCount) items.add(layout.getChildAt(i))
        }
        if (items.size >= 2) {
            captureIconBoxDp(items)
            return items
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
        val deepest = candidates.filter { c -> candidates.none { it !== c && it.isAncestorOf(c) } }
        if (deepest.size >= 2) captureIconBoxDp(deepest)
        return deepest
    }

    /** 装配期捕获条目图标盒尺寸：图标视图布局宽 = 形态决定的原生渲染尺寸（普通矢量
     *  24dp / 符号·全局主题 32dp，VNavMenuItem.setIcon 会按 drawable 类改写布局宽高），
     *  px→dp 取整。探测时布局可能未走完但 layoutParams 已由 inflate/setIcon 写定。 */
    private fun captureIconBoxDp(items: List<View>) {
        if (items.isEmpty()) return
        runCatching {
            val iconId = idByName(items[0], ICON_ID_NAME)
            if (iconId == 0) return
            val icon = items[0].findViewById<View>(iconId) ?: return
            val width = icon.layoutParams?.width?.takeIf { it > 0 } ?: return
            val density = icon.resources.displayMetrics.density
            if (density > 0f) {
                observedIconBoxDp = Math.round(width / density)
            }
        }
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
        // onResume 兜底压制（早于首帧 traversal，onResume after → 首帧前）：会话尚未认领
        // 的底栏实例（冷启动 hook 之外的窗口/重进重建）按 id 定位直接中性化，探测装配后
        // 由会话接管。会话已认领（bar 即会话容器）时跳过，压制/还原由会话负责。
        runCatching {
            val barId = if (barIdCache == 0) {
                idByName(activity.window.decorView, BAR_ID_NAME)
            } else {
                barIdCache
            }
            if (barId != 0) {
                barIdCache = barId
                val claimed = sessionRef?.get()?.tabContainer
                activity.findViewById<View>(barId)?.let { bar ->
                    if (bar !== claimed) neutralizeBar(bar)
                }
            }
        }
        barRef?.get()?.takeIf { it.isAttachedToWindow }?.let { if (it.alpha != 0f) it.alpha = 0f }
        // 主题/形态重放会重建源 drawable 列表——恢复期重预热（getOrPut 幂等：已有源零
        // 操作、新源补建稳定落定实例；此刻新副本未挂可见条目、驱动不可见）
        sessionRef?.get()?.let { prewarmPreviewIcons(it) }
    }

    /** 早期压制兜底还原时限（未被会话认领时还原 alpha，防永久隐形）。 */
    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L

    /** 早期压制实例（弱键集合；会话装配时认领移除）。 */
    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    /** 底栏资源 id 缓存（getIdentifier 每实例解析开销可忽略，仅冷启动少数实例）。 */
    @Volatile private var barIdCache = 0

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        runCatching {
            // initialize=false：进程加载期宿主 Application 未建，连锁初始化会闪退（微博 §5.7 纪律）
            val barClass = Class.forName(BAR_CLASS, false, loader)
            // hook 布局 inflate 路径的 (Context, AttributeSet) 构造器：inflate 完成即压制
            // （早于首帧绘制，冷启动原栏闪现修复，健康/主题 §10.2 早期压制三段式同款）。
            // 类内其他构造（纯代码创建）不挂。本应用进程只有 tabLayout 一个该类实例，
            // 按 id 门控仍保留（防升级后新增页面同库底栏误伤）。
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
                "hooked VBottomNavigationView.<init>(Context,AttributeSet): early suppress",
            )
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook VBottomNavigationView ctor failed (app updated?)", it)
        }
    }

    /** 底栏早期压制（冷启动原栏闪现修复，健康 §11 同款三段式）：构造完成即 alpha=0
     *  （此时 android:id 已由 View 构造器应用，按 [BAR_ID_NAME] id 门控，防误伤应用内
     *  其他页面的同库底栏）；会话装配时认领（此后压制/还原由会话接管，restore 恢复
     *  alpha=1 与早期压制天然兼容）；[EARLY_SUPPRESS_FALLBACK_MS] 未认领（探测放弃/
     *  模块中途关闭/非主底栏实例）兜底还原 alpha=1。 */
    private fun suppressEarly(view: View, facade: HookFacade) {
        if (barIdCache == 0) {
            barIdCache = idByName(view, BAR_ID_NAME)
        }
        if (barIdCache == 0 || view.id != barIdCache) return
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        neutralizeBarBlur(view)
        facade.log(android.util.Log.INFO, "vivo-alarmclock early-suppress bar (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "vivo-alarmclock early-suppress fallback restore (session never claimed)",
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
        barRef = WeakReference(session.tabContainer)
        sessionRef = WeakReference(session)
        activityRef = WeakReference(activity)
        reassertBarDecorations(session.tabContainer)
        // 早期压制认领（冷启动闪现修复，见 installAppHooks）：装配即由会话接管压制/还原
        earlySuppressed.remove(session.tabContainer)
        // 预览图标落定实例预热（首悬停瞬时落定，见 prewarmPreviewIcons）
        prewarmPreviewIcons(session)
        // 预览图标换图（拖动悬停）：引擎 setTabChecked 翻转 setChecked 后调用——本应用
        // setChecked 不换图（图标 tint 恒 null、素材为普通矢量/符号 drawable），按
        // isSelected 真值源统一强制收敛（不走库 U(index,title,res) 换图口——非全局主题
        // 分支 reset()+start() 重播 AVD 动画）。事件 + 逐帧双路收敛见 enforcePreviewIcons。
        session.previewIconSwapper = { _, _ ->
            enforcePreviewIcons(session)
        }
        // 压制重申 + 装饰重申（帧循环每帧执行，仅失配时写入）+ 预览图标逐帧收敛（一次性
        // apply 失败/被顶掉的自愈通道）+ 显隐 Y 门控：悬浮工具条（BottomToolbarManager
        // m.u）出现/收起时对底栏做 translationY 0 ↔ 滑出 的 400ms ObjectAnimator 对滑
        // （jadx 实锤）——原生栏滑出屏幕时玻璃栏须同步隐藏（原子笔记/小红书同款过半
        // Y 门控标准语义）。
        val container = session.tabContainer
        session.mirrorVisibleGate = {
            reassertSuppressions()
            enforcePreviewIcons(session)
            container.isShown && container.translationY < container.height * 0.5f
        }
        session.onAuxGlassRestore = { restoreDecorations() }
    }

    /** 逐帧结构化扫描（会话装配时先行一轮，mirrorVisibleGate 顺带执行）——底栏内部装饰
     *  **物理移除**（微博/健康同库方案）：底栏全部非 menuLayout 直接子视图（构造器
     *  index 0 挂载的无 id 顶边分割线）。alpha=0 理论可压制，但同库装饰存在绕过
     *  view alpha 的渲染嫌疑（健康/文件管理/手机管家实证），移除后应用的可见性/背景
     *  写入落在游离视图上；帧循环对运行期新加的装饰检出即移除。menuLayout（tab 条目
     *  容器）不可动。底栏子树不变量 = 只剩 menuLayout；非 menuLayout 子视图首见记录
     *  原状态、检出即移除，已移除的不会出现在树里，稳态零写入。 */
    private fun reassertSuppressions() {
        val bar = barRef?.get() ?: return
        reassertBarDecorations(bar)
        if (bar.alpha != 0f) bar.alpha = 0f
        neutralizeBarBlur(bar)
    }

    /** blur 中性化已应用的实例（帧循环重申路径按实例一次性，避免逐帧反射调用
     *  `setBlurEnable`——其内部每次都走 VBlurUtils 模糊效果重设）。库内 blur 重开路径
     *  仅 ctor `q()` 与 `w(configuration)`（后者读 static `f6340p0`，已被本方法置 false
     *  → 重开不成立），一次关闭即稳态；实例销毁后 WeakHashMap 键自然失效。 */
    private val blurNeutralized = java.util.WeakHashMap<View, Boolean>()

    /** 底栏中性化（alpha 压制 + 模糊层关闭）：库构造器尾 `q()` 在系统全局视图模糊开启
     *  时 `setBlurEnable(true)` → vivo 动态模糊（RenderEffect/mirror 层实现）**绕过
     *  view alpha**——alpha=0 压住了图标/文字，磨砂底条仍从首帧可见直到玻璃装配覆盖
     *  （真机表现为"启动/重进应用短暂出现原底栏"，健康 №11.7 同库装饰绕过 alpha 渲染
     *  嫌疑的预言应验）。压制时一并 `setBlurEnable(false)` 中性化（public 非混淆；
     *  其回调把背景设回资源色卡，在 alpha=0 下不可见无害；`f6340p0` 为库内 static，
     *  本进程仅此一个底栏实例无污染）。幂等：仅在 alpha 失配时写 alpha，blur 关闭
     *  重复调用无操作。 */
    private fun neutralizeBar(view: View) {
        if (view.alpha != 0f) view.alpha = 0f
        neutralizeBarBlur(view)
    }

    private fun neutralizeBarBlur(view: View) {
        synchronized(blurNeutralized) {
            if (blurNeutralized.containsKey(view)) return
            blurNeutralized[view] = true
        }
        runCatching {
            view.javaClass
                .getMethod("setBlurEnable", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(view, false)
        }
        android.util.Log.println(
            android.util.Log.INFO, "LiquidGlass",
            "vivo-alarmclock bar blur neutralized",
        )
    }

    private fun reassertBarDecorations(bar: View) {
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
            "vivo-alarmclock decoration removed: ${view.javaClass.name} index=$index",
        )
        hiddenDecorations.add(
            DecorationState(
                view = view,
                parent = parent,
                index = index,
                visibility = view.visibility,
            )
        )
        parent.removeView(view)
    }

    /** 会话还原（模块关闭/视图树重建）：重新挂载被移除的装饰（倒序恢复原挂载位置与
     *  可见性）。 */
    private fun restoreDecorations() {
        for (d in hiddenDecorations.reversed()) {
            if (d.view.parent == null) {
                runCatching {
                    d.parent.addView(
                        d.view,
                        d.index.coerceAtMost(d.parent.childCount),
                    )
                }
            }
            d.view.visibility = d.visibility
        }
        hiddenDecorations.clear()
    }

    // —— 拖动悬停预览的图标换图（isSelected 真值源 + 逐帧强制收敛，全部 runCatching 静默降级） ——

    /** 预览图标强制收敛（§1.2 统一语义 = 悬停 tab 选中**落定**外观、离开恢复未选中
     *  落定）：以条目 `isSelected` 为**唯一真值源**，把全部条目的 iconView 收敛到期望
     *  落定 drawable（`drawable === desired` 引用相等 = 已就位零写入，天然幂等）。
     *  仅 [GlassSession.isTabPreviewActive] 期间执行——正式切页的应用换图（I0() 的
     *  AVD reset+start 动画）不被顶掉（isTabPreviewActive 在 performClick 前置 false）。
     *  期望实例全部来自**装配期预热**的稳定落定缓存（见 [prewarmPreviewIcons]）——
     *  首次悬停即瞬时落定，无"新副本首次驱动带 0→1 过渡"的变量（健康 №11.7 预驱动
     *  教训的实例化版本）。
     *  **为何逐帧**（真机 2026-11-03 教训 3）：事件驱动换图是"一次性 apply"——引擎
     *  `previewWholeTab` 对同一悬停目标去重后不再补发事件，一次 apply 失败/被框架
     *  副作用顶掉后，悬停期间永远没有第二次机会（真机表现 = 首次经过不稳定、移出
     *  再移进才恢复）。逐帧收敛把图标外观变成 isSelected 的纯函数：任何一帧的意外
     *  状态在下一帧（≤16ms）自愈，且引用相等判据保证稳态零写入。
     *  **为何直挂 iconView 绕过 `VNavMenuItem.setIcon`**（真机 2026-11-03 教训 1）：
     *  setIcon 开头 `if (drawable == f6463r) return` 实例去重——同 tab 同一源实例第二
     *  次换入即提前 return；直挂绕开该去重。图标布局尺寸已由初始 setIcon 按 drawable
     *  形态写定（矢量 24dp/符号 32dp），两态互换不改形态无需 `l()` 重写。 */
    private fun enforcePreviewIcons(session: GlassSession) {
        if (!session.isTabPreviewActive) return
        val timer = activityRef?.get() ?: return
        val tabCount = session.tabViews.size
        val fields = timer.javaClass
        // 源解析每帧一次（字段值重读不缓存——o0()/onRestoreInstanceState 按主题/形态
        // 整体重放重建；Field.get 本身廉价，无查找开销）
        val selSymbols = drawableList(fields, FIELD_SYMBOLS_SELECTED, timer)
        val unselSymbols = drawableList(fields, FIELD_SYMBOLS_UNSELECTED, timer)
        val symbolMode = selSymbols.size == tabCount && unselSymbols.size == tabCount
        val selRes = if (symbolMode) IntArray(0) else resArray(fields, FIELD_ICONS_SELECTED, timer)
        val normalRes = if (symbolMode) IntArray(0) else resArray(fields, FIELD_ICONS_NORMAL, timer)
        val resMode = selRes.size == tabCount && normalRes.size == tabCount
        if (!symbolMode && !resMode) return
        for ((index, item) in session.tabViews.withIndex()) {
            val iconView = iconViewCache.getOrPut(item) { iconViewOf(item) } ?: continue
            val desired = if (symbolMode) {
                val source = (if (item.isSelected) selSymbols else unselSymbols)
                    .getOrNull(index) ?: continue
                settledSymbolCache.getOrPut(source) { settledCopyOf(source) }
            } else {
                val resId = (if (item.isSelected) selRes else normalRes)
                    .getOrNull(index) ?: continue
                if (resId == 0) continue
                settledResCache.getOrPut(resId) { timer.getDrawable(resId) }
            }
            if (iconView.drawable === desired) continue
            runCatching { iconView.setImageDrawable(desired) }
        }
    }

    /** 装配期预热（onSessionCreated，健康 №11 预驱动纪律的实例化）：符号模式对 `g`/`h`
     *  全部源 drawable 各建一枚已驱动到落定进度的稳定副本入缓存，普通/动态色模式对
     *  `f`/`e` 全部 resId 预解析——此刻全部实例**未挂任何视图、驱动不可见**，首次悬停
     *  即瞬时落定（未预热则新副本首次驱动自带 0→1 过渡，悬停期发浅，健康 №11.7 同款
     *  真机教训）。恢复期（onTabActivityResumed）重预热覆盖主题/形态重放重建的源。 */
    private fun prewarmPreviewIcons(session: GlassSession) {
        val timer = activityRef?.get() ?: return
        val tabCount = session.tabViews.size
        val fields = timer.javaClass
        val selSymbols = runCatching {
            drawableList(fields, FIELD_SYMBOLS_SELECTED, timer)
        }.getOrNull().orEmpty()
        if (selSymbols.size >= tabCount) {
            val unselSymbols = runCatching {
                drawableList(fields, FIELD_SYMBOLS_UNSELECTED, timer)
            }.getOrNull().orEmpty()
            for (source in selSymbols) settledSymbolCache.getOrPut(source) { settledCopyOf(source) }
            for (source in unselSymbols) settledSymbolCache.getOrPut(source) { settledCopyOf(source) }
            return
        }
        val selRes = runCatching { resArray(fields, FIELD_ICONS_SELECTED, timer) }.getOrNull() ?: IntArray(0)
        val normalRes = runCatching { resArray(fields, FIELD_ICONS_NORMAL, timer) }.getOrNull() ?: IntArray(0)
        if (selRes.size >= tabCount) {
            for (resId in selRes) if (resId != 0) {
                settledResCache.getOrPut(resId) { timer.getDrawable(resId) }
            }
            for (resId in normalRes) if (resId != 0) {
                settledResCache.getOrPut(resId) { timer.getDrawable(resId) }
            }
        }
    }

    /** 源 drawable 的稳定落定副本：`constantState.newDrawable().mutate()` 全新实例 →
     *  进度直驱 1.0f（库 k() 创建配方 `p7.a.e().r(drawable,1.0f)` 同款落定；非符号
     *  drawable 无进度方法自然跳过）。每源仅成型一次，之后引用复用 = 每次挂载观感
     *  逐位一致。 */
    private fun settledCopyOf(source: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
        val copy = source.constantState?.newDrawable()?.mutate() ?: source
        driveSymbolProgress(copy, 1.0f)
        return copy
    }

    /** 条目的图标 ImageView（元素 id `vivo_navigation_bar_item_icon_view`）。 */
    private fun iconViewOf(item: View): android.widget.ImageView? {
        val iconId = idByName(item, ICON_ID_NAME)
        if (iconId == 0) return null
        return item.findViewById<View>(iconId) as? android.widget.ImageView
    }

    private fun drawableList(fields: Class<*>, name: String, owner: Any): List<android.graphics.drawable.Drawable> {
        val list = runCatching {
            fields.getDeclaredField(name).apply { isAccessible = true }.get(owner) as? ArrayList<*>
        }.getOrNull() ?: return emptyList()
        return list.filterIsInstance<android.graphics.drawable.Drawable>()
    }

    private fun resArray(fields: Class<*>, name: String, owner: Any): IntArray {
        return runCatching {
            fields.getDeclaredField(name).apply { isAccessible = true }.get(owner) as? IntArray
        }.getOrNull() ?: IntArray(0)
    }

    /** 符号进度直驱（`setAnimProgress(float)`，与库 k() 创建配方
     *  `p7.a.e().r(drawable,1.0f)` 落定同款）：门控 = 方法存在性（非类名包含——vivo
     *  符号框架运行时类名/框架版本差异免疫；静态矢量无此方法自然跳过）。失败静默。 */
    private fun driveSymbolProgress(drawable: android.graphics.drawable.Drawable, progress: Float) {
        val method = runCatching {
            drawable.javaClass.getMethod("setAnimProgress", Float::class.javaPrimitiveType)
        }.getOrNull() ?: return
        runCatching {
            method.apply { isAccessible = true }.invoke(drawable, progress)
        }
    }
}
