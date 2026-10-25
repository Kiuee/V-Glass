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
 * vivo 健康（com.vivo.health，桌面名"健康"，版本 7.1.3.13 取证）适配器。
 *
 * - 主页链路：launcher = GuideActivity（引导/隐私页，跳转主界面），主 Tab Activity =
 *   [TAB_ACTIVITY]。布局 `activity_main.xml`：ConstraintLayout > 全屏 ViewPager2
 *   （`view_pager`）+ [BAR_CLASS]（id [BAR_ID_NAME]，constraint 底部 overlay 叠加，
 *   `vAdapterVerticalImmersive=true` 内容天然延伸到底，无内容延伸诉求）。冷启动先
 *   setContentView(loading_screen) 再换 activity_main，底栏随布局替换创建。
 * - 底栏结构：[BAR_CLASS]（应用自有子类，jadx 全程未混淆）继承 HealthBottomNavigationView
 *   → originui 库 VBottomNavigationView（非混淆，6.1.0.11），与文件管理/手机管家同库。
 *   构造器 index 0 挂载无 id 顶边分割线 View（库内字段 O，0.33dp，应用 k0() 里
 *   setDividerLineVisibility(true) 显式打开）——走物理移除（微博/手机管家方案，
 *   同库装饰存在绕过 view alpha 的渲染嫌疑实证）。
 * - 每 tab 视图：menuLayout（com.originui.widget.navigation.VMenuViewLayout）直接子项 =
 *   `com.originui.widget.navigation.navigation.VNavMenuItem`（**非混淆**，本应用 APK 未
 *   混淆 originui 库——比手机管家的混淆条目类 rh.b 更干净）。元素 id 与同库应用一致
 *   （`vivo_navigation_bar_item_icon_view` / `vivo_navigation_bar_item_labels_group`）。
 *   3~4 个 tab（健康/设备/我的 + 运动仅 VivoHealthFavor 或计步组件在装时经 n() 追加）。
 * - 选中机制：VNavMenuItem 有**真 `setChecked(boolean)`**（public，纯视觉翻转 + 末尾
 *   setSelected(z2)，标准 isSelected 读数），无需声明 itemCheckedSetterMethodName。
 * - 换图（图标双路径，均存底栏字段 F 的 LottieAttribute 映射，条目 → 图标对）：
 *   ① ROM16 动态符号路径（FontSymbolHelper.i()）：n() 添加 FontSymbolDrawable 对
 *   （选中=主题色、未选中=灰）；② 旧路径：l() 添加 静态未选中 res + 主题色染色的
 *   selected_anim Lottie。应用点击换图 = 未选 r(item) / 选中 O(item)（**携带
 *   `U(drawable)`=start() 状态动画副作用**，是正式点击的动画重播入口）。拖动悬停
 *   预览注入 [session.previewIconSwapper]：不走 r/O，按库"编程选中换图" `W()` 的
 *   落定配方直驱——checked = 图标对选中符号 `i` + setAnimProgress(1.0f)、unchecked =
 *   未选中符号 `h` + setAnimProgress(1.0f)（`n()` 创建落定配方），映射全缺失才降级
 *   r/O（见 swapPreviewIcon）；换主题（HealthThemeManager → a0/s0 重放）会整体重建
 *   映射——每次换图前实时解析、不缓存。
 * - 切页链路：条目 performClick → menuLayout 监听 → 未选中 I(item)（r(旧)+O(新)+p()
 *   回调 OnItemSelectedListener → ViewPager2.setCurrentItem）；已选中走
 *   OnItemClickListener → MainTitleClick 事件（回顶类）。通用 performClick 转发全覆盖，
 *   无需 tabClickHandler。
 * - 标签色 = itemTextColor 真选择器（state_checked/selected 差异色），取色即得
 *   强调色/内容色，无需 labelAccentFallbackToBubble。
 * - 应用对底栏无主动隐藏路径（子页全为独立 Activity，ViewPager2 不横滚）——
 *   mirrorVisibleGate 恒 true，仅借帧循环逐帧重申 alpha 压制与装饰移除。
 * - 冷启动闪现：底栏在 Activity 布局（setContentView 即装配）而探测/会话装配晚于首帧
 *   （真机同构场景已两度实证：手机管家/主题），hook [BAR_CLASS] 的
 *   `(Context, AttributeSet)` 构造器做早期压制（按 [BAR_ID_NAME] id 门控 +
 *   8s 兜底还原 + 会话认领三段式，§10.2/§11 同款）。
 * - 主 tab 无 WebView、无 GL 内容——角标镜像与采样冻结通道均不投入。
 */
object VivoHealthAdapter : AppAdapter {

    override val targetPackage: String = "com.vivo.health"
    override val displayName: String = "vivo 健康"
    override val tabActivityClassName: String? = TAB_ACTIVITY
    override val baselineVersions: List<String> = listOf("7.1.3.13")

    private const val TAB_ACTIVITY = "com.vivo.health.main.activity.MainActivity"
    // 底栏（应用自有子类，非混淆）与其在 activity_main.xml 中的资源 id（类名失配兜底）
    private const val BAR_CLASS = "com.vivo.health.main.widget.MainBottomNavigationView"
    private const val BAR_ID_NAME = "magic_indicator"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/文件管理/手机管家同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"
    // originui 库 VBottomNavigationView 的换图方法（public，非混淆；条目参数类型 VNavMenuItem）
    private const val BAR_CLASS_ORIGIN = "com.originui.widget.navigation.VBottomNavigationView"
    private const val ITEM_CLASS_ORIGIN =
        "com.originui.widget.navigation.navigation.VNavMenuItem"

    // —— 压制运行期状态（弱引用防泄漏；原状态供会话还原） ——
    @Volatile private var barRef: WeakReference<View>? = null
    @Volatile private var sessionRef: WeakReference<GlassSession>? = null

    /** 已移除的底栏内部装饰（构造器 index 0 的无 id 顶边分割线等非 menuLayout 子视图），
     *  记录原挂载位置与可见性，会话 restore 重挂。 */
    private val hiddenDecorations = ArrayList<DecorationState>(2)

    /** 预览换图同态去重（条目 → 最近应用的 checked 态）：换图引擎只在选中态翻转时调用，
     *  但悬停边界抖动（原 tab ⇄ 悬停 tab 往返）与重放路径的同态重复调用会反复 setIcon——
     *  同态跳过（手机管家同款去重）。 */
    private val lastAppliedChecked = java.util.WeakHashMap<View, Boolean>()

    private class DecorationState(
        val view: View,
        val parent: ViewGroup,
        val index: Int,
        val visibility: Int,
    )

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐同库应用）
        elementIds = mapOf(
            "icon" to ICON_ID_NAME,
            "labels" to LABELS_ID_NAME,
        ),
        // 选中外观翻转：本应用 originui 库未混淆，VNavMenuItem.setChecked 为真 public 方法
        // （纯视觉 + 末尾 setSelected），走默认 "setChecked" 反射，无需声明。
        // 图标镜像盒覆盖（全局 24dp）：应用 k0() 里 setItemIconSize(27dp)（符号模式符号
        // 字号 24sp、旧路径 res 图标同为 27dp 视图），1:1 镜像原生尺寸，图标观感与
        // 其他适配应用（24dp 盒 ~24dp 字形）一致；27 ≤ 28dp 布局预留，不挤标签
        iconBoxDp = 27,
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
        // 主题重放/未知路径把 alpha 写回 1 的纠错（帧循环恢复后由 mirrorVisibleGate 逐帧接管）
        barRef?.get()?.takeIf { it.isAttachedToWindow }?.let { if (it.alpha != 0f) it.alpha = 0f }
        // 主题重放（可在恢复期发生，如控制中心切深色后返回）会重建图标对、新选中符号
        // 进度归零——恢复期重驱动（幂等，已落定的驱动 = 无操作）
        sessionRef?.get()?.let { preDriveSelectedSymbols(it) }
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
            // （早于首帧绘制，冷启动原栏闪现修复，主题 §10.2 早期压制三段式同款）。
            // 类内其他构造（纯代码创建）不挂。
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
                "hooked MainBottomNavigationView.<init>(Context,AttributeSet): early suppress",
            )
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook MainBottomNavigationView ctor failed (app updated?)", it)
        }
    }

    /** 底栏早期压制（冷启动原栏闪现修复，手机管家 §11 同款三段式）：构造完成即 alpha=0
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
        facade.log(android.util.Log.INFO, "vivo-health early-suppress bar (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "vivo-health early-suppress fallback restore (session never claimed)",
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
        reassertBarDecorations(session.tabContainer)
        // 早期压制认领（冷启动闪现修复，见 installAppHooks）：装配即由会话接管压制/还原
        earlySuppressed.remove(session.tabContainer)
        // 选中符号预驱动（首悬停发浅修复，见 preDriveSelectedSymbols）
        preDriveSelectedSymbols(session)
        // 预览图标换图（拖动悬停）：引擎 setTabChecked 翻转 setChecked 后调用——悬停 tab
        // 与被拖离的原选中按库 W()/n() 落定配方直驱（映射 i/h 符号 + setAnimProgress 1.0f），
        // **不走 O()/r()**（其 U(start) 会重播状态动画）；映射全缺失才降级 r/O。方法每次
        // 换图前实时解析：换主题（HealthThemeManager → a0/s0 重放）会整体重建 F 映射，不缓存。
        session.previewIconSwapper = { index, checked ->
            swapPreviewIcon(session, index, checked)
        }
        // 压制重申 + 装饰重申（帧循环每帧执行，仅失配时写入）：应用无确证隐藏路径，
        // 恒返回 true（barWanted 不变）；顺带检出运行期新加的装饰即移除。
        session.mirrorVisibleGate = {
            reassertSuppressions()
            true
        }
        session.onAuxGlassRestore = { restoreDecorations() }
    }

    /** 逐帧结构化扫描（会话装配时先行一轮，mirrorVisibleGate 顺带执行）——底栏内部装饰
     *  **物理移除**（微博/手机管家方案）：底栏全部非 menuLayout 直接子视图（构造器
     *  index 0 挂载的无 id 顶边分割线等）。alpha=0 理论可压制，但同库装饰存在绕过
     *  view alpha 的渲染嫌疑（文件管理/手机管家实证），移除后应用的可见性/背景写入
     *  落在游离视图上；帧循环对运行期新加的装饰检出即移除。menuLayout（tab 条目容器）
     *  不可动。底栏子树不变量 = 只剩 menuLayout；非 menuLayout 子视图首见记录原状态、
     *  检出即移除，已移除的不会出现在树里，稳态零写入。 */
    private fun reassertSuppressions() {
        val bar = barRef?.get() ?: return
        reassertBarDecorations(bar)
        if (bar.alpha != 0f) bar.alpha = 0f
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
            "vivo-health decoration removed: ${view.javaClass.name} index=$index",
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

    /** 预览换图（拖动悬停，§1.2 统一语义 = 悬停 tab 选中**落定**外观、离开恢复未选中落定）：
     *  **不走 `O(item)`/`r(item)`**（库点击路径换图，符号分支 = `U(drawable)` =
     *  `FontSymbolDrawable.start()` **状态动画**——真机表现为悬停期动画不断重播）；改按
     *  库自身"编程选中换图" `W()` 的落定配方直驱：读底栏字段 `F`（`Map<VNavMenuItem,
     *  LottieAttribute>`，本应用库未混淆、字段真名）取图标对：
     *  - checked → 选中符号 `i`：条目 `setIcon(i)` + `setAnimProgress(1.0f)`（`i` 已由
     *    [preDriveSelectedSymbols] 预驱动到落定进度，此处驱动 = 无操作 = 瞬时落定；
     *    与文件管理 §9.2 `r(drawable,1.0f)` 直驱先例同族）；`i` 缺失按 `W()` 取值序
     *    降级静态 `d` → 单 drawable `g` → 兜底 `O(item)`（静默降级为重播）。
     *  - unchecked → 未选中符号 `h`：`setIcon(h)` + `setAnimProgress(1.0f)`（库 `n()` 创建
     *    条目时对未选中符号的同款落定配方——`o(drawable2, 1.0f)`，非 0.0）；`h` 缺失按
     *    `r()` 取值序降级静态 `b` → res `a` → 兜底 `r(item)`。
     *  - 同态去重（[lastAppliedChecked]）跳过重复调用；映射/方法每次实时解析（换主题
     *    HealthThemeManager → a0/s0 重放会整体重建映射），任一环节失败静默跳过（表现 =
     *    预览图标停留原 drawable，无崩溃）。 */
    private fun swapPreviewIcon(session: GlassSession, index: Int, checked: Boolean) {
        val item = session.tabViews.getOrNull(index) ?: return
        val bar = barRef?.get() ?: return
        synchronized(lastAppliedChecked) {
            if (lastAppliedChecked[item] == checked) return
        }
        val applied = runCatching {
            val pair = iconPairOf(bar, item)
            if (checked) applyCheckedSettled(item, pair) else applyUncheckedSettled(item, pair)
            true
        }.getOrDefault(false)
        if (applied) {
            synchronized(lastAppliedChecked) { lastAppliedChecked[item] = checked }
        }
    }

    /** 选中符号预驱动（装配/恢复期，首悬停发浅修复，真机 2026-10-25）：图标对的选中
     *  符号 `i` 在应用侧从不被驱动——创建路径 `n()` 只把未选中符号 `h` 驱到 1.0、主题
     *  重放 `c0()` 只对**当前选中项**驱 `i`——其余 tab 的 `i` 初始进度为 0，首次悬停
     *  [swapPreviewIcon] 的 `setAnimProgress(1.0f)` 触发 0→1 过渡，悬停期呈中间态
     *  （颜色发浅），第二次经过才因已到 1.0、重复驱动 = 无操作而瞬时呈现强调色。
     *  装配即对全部 tab 的 `i` 各驱动一次到落定进度（此刻 `i` 未挂上任何视图、驱动
     *  不可见），悬停时重复驱动即无操作 = 瞬时落定；真实点击换图走 `start()` 重播
     *  完整选中动画，不受预驱动影响。恢复期（[onTabActivityResumed]）重驱动覆盖主题
     *  重放重建的图标对。驱动失败静默（表现 = 该 tab 首悬停发浅一次，无崩溃）。 */
    private fun preDriveSelectedSymbols(session: GlassSession) {
        val bar = session.tabContainer
        for (item in session.tabViews) {
            runCatching {
                val pair = iconPairOf(bar, item) ?: return@runCatching
                drawableField(pair, "i")?.let { driveSymbolProgress(it, 1.0f) }
            }
        }
    }

    /** 反射读底栏图标对映射（字段真名 `F`：条目 → LottieAttribute；本应用 originui 库
     *  未混淆，jadx 无 renamed-from 注释即运行时真名）。映射或条目缺失返回 null。 */
    private fun iconPairOf(bar: View, item: View): Any? {
        return runCatching {
            val barClass = Class.forName(BAR_CLASS_ORIGIN, true, bar.context.classLoader)
            val map = barClass.getDeclaredField("F")
                .apply { isAccessible = true }
                .get(bar) as? Map<*, *>
            map?.get(item)
        }.getOrNull()
    }

    /** checked 落定外观 = 库 `W()` 符号分支配方：选中符号 `i` + setAnimProgress(1.0f)；
     *  降级链 d 静态 → g 单 drawable → `O(item)`（重播，仅全缺失兜底）。 */
    private fun applyCheckedSettled(item: View, pair: Any?) {
        drawableField(pair, "i")?.let {
            itemSetIcon(item, it)
            driveSymbolProgress(it, 1.0f)
            return
        }
        drawableField(pair, "d")?.let { itemSetIcon(item, it); return }
        drawableField(pair, "g")?.let { itemSetIcon(item, it); return }
        invokeItemSwap(item, "O")
    }

    /** unchecked 落定外观 = 库 `n()` 创建配方：未选中符号 `h` + setAnimProgress(1.0f)
     *  （**非 0.0**——`n()` 对未选中符号的落定即 1.0，两枚符号是不同字形码、各自 1.0 =
     *  各自落定态）；降级链 b 静态 → res `a` → `r(item)`（重播，仅全缺失兜底）。 */
    private fun applyUncheckedSettled(item: View, pair: Any?) {
        drawableField(pair, "h")?.let {
            itemSetIcon(item, it)
            driveSymbolProgress(it, 1.0f)
            return
        }
        drawableField(pair, "b")?.let { itemSetIcon(item, it); return }
        val resId = intField(pair, "a")
        if (resId != 0) {
            itemSetIcon(item, resId)
            return
        }
        invokeItemSwap(item, "r")
    }

    private fun drawableField(pair: Any?, name: String): android.graphics.drawable.Drawable? =
        pair?.let {
            runCatching {
                it.javaClass.getDeclaredField(name).apply { isAccessible = true }
                    .get(it) as? android.graphics.drawable.Drawable
            }.getOrNull()
        }

    private fun intField(pair: Any?, name: String): Int = pair?.let {
        runCatching {
            it.javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(it)
        }.getOrDefault(0)
    } ?: 0

    /** 条目换图（`VNavMenuItem.setIcon(Drawable/int)` public 非混淆）。 */
    private fun itemSetIcon(item: View, icon: android.graphics.drawable.Drawable) {
        item.javaClass.getMethod("setIcon", android.graphics.drawable.Drawable::class.java)
            .apply { isAccessible = true }
            .invoke(item, icon)
    }

    private fun itemSetIcon(item: View, resId: Int) {
        item.javaClass.getMethod("setIcon", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(item, resId)
    }

    /** 符号进度直驱（`FontSymbolDrawable.setAnimProgress(float)`，与库 `W()` 落定分支/
     *  文件管理 §9.2 `r(drawable,1.0f)` 同款；非符号 drawable 跳过——静态图无进度态）。 */
    private fun driveSymbolProgress(drawable: android.graphics.drawable.Drawable, progress: Float) {
        if (!drawable.javaClass.name.contains("FontSymbolDrawable")) return
        runCatching {
            drawable.javaClass.getMethod("setAnimProgress", Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(drawable, progress)
        }
    }

    /** 兜底换图（`O` 选中 / `r` 反选，public 非混淆；携带 `U(start)` 状态动画副作用，
     *  仅映射读取全失败时降级使用）。 */
    private fun invokeItemSwap(item: View, method: String) {
        val bar = barRef?.get() ?: return
        val barClass = Class.forName(BAR_CLASS_ORIGIN, true, bar.context.classLoader)
        val itemClass = Class.forName(ITEM_CLASS_ORIGIN, true, bar.context.classLoader)
        barClass.getDeclaredMethod(method, itemClass)
            .apply { isAccessible = true }
            .invoke(bar, item)
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
}
