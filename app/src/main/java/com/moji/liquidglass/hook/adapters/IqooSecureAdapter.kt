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
 * vivo 手机管家（com.iqoo.secure，桌面名"手机管家"，版本 9.3.19.0 取证）适配器。
 *
 * - 主页链路：主 Tab Activity = [TAB_ACTIVITY]（launcher，唯一探测目标），布局
 *   `main_tab_activity_layout`：RelativeLayout > `home_container`（FrameLayout，全屏）
 *   + [BAR_CLASS]（id `bottom_tab`，alignParentBottom overlay 叠加，内容天然延伸到底，
 *   无内容延伸诉求）。底栏**直接写在 Activity 布局**（setContentView 即装配，非 ViewStub
 *   惰性展开），但探测/会话装配晚于首帧——冷启动首帧到玻璃就位之间原栏可见（真机实证
 *   闪现），故 hook 底栏 `(Context, AttributeSet)` 构造器做早期压制（本类未声明
 *   onFinishInflate，无法按类 hook；8s 兜底还原 + 会话认领三段式，主题 §10.2 同款）。
 * - 底栏结构：[BAR_CLASS]（非混淆 originui 库类，与文件管理同库）内含 menuLayout
 *   （com.originui.widget.navigation.VMenuViewLayout）+ 构造器 index 0 挂载的**无 id**
 *   顶边分割线 View（f20814o，`originui_bottomnavigationview_divider_color`，与文件
 *   管理同款）。
 * - 每 tab 视图：menuLayout 直接子项 = `rh.b`（**混淆**，jadx 注释 "compiled from
 *   VNavMenuItem.java"，与相册/文件管理/主题的 VNavMenuItem 同源同构）。元素 id 与同库
 *   应用完全一致（`vivo_navigation_bar_item_icon_view` / `vivo_navigation_bar_item_labels_group`）。
 *   仅 2 个固定 tab（PhoneManageFragment 首页 / ToolsFragment 工具）。
 * - 选中机制（文件管理 discolor 同族，换图与翻转双通道分离）：视觉翻转 = **`y(boolean)`**
 *   （方法级混淆、无 setChecked；纯视觉、末尾 setSelected(z10)，与 vivo 官网 originui
 *   13.3.0.1 同款先例）；换图 = 底栏 `f(item)`/`M(int)` 驱动——反选 `m(item)`、选中
 *   `A(item)`（protected final，纯换图 + m 内含 y(false) 翻转，无 l(item) 监听回调
 *   即无切页副作用），图标对存底栏字段真名 `l`（HashMap 条目→`com.originui.widget.navigation.a`，
 *   jadx 显示名 f20811l 系改名）。拖动悬停预览注入 [session.previewIconSwapper]：
 *   unchecked 走 `m(item)`、checked 走"图标对选中态末帧"（A() 会重播选中动画，真机
 *   表现为悬停期动画不断播放；末帧 = 静态 `h` 优先、Lottie `f` 停止+setProgress 1.0f
 *   [混淆名 C(float)]，§1.2 悬停统一语义）+ 同态去重。换肤（ColorChangeUtils.y 回调 +
 *   `O`/`Q` 重放）会整体重放映射——反射锚点每次换图前实时解析、不缓存。
 * - 标签色 = `originui_bottomnavigation_view_item_color_*` 真选择器（state_selected
 *   差异色，res/color 取证），取色即得强调色/内容色，无需 labelAccentFallbackToBubble。
 * - 应用对底栏自带 vivo 空间模糊（`B(true)` 卡片式背景 + `VBlurUtils.setBlurEffect` +
 *   SpaceBlurAbility）：alpha=0 压制；内部分割线存在绕过 view alpha 的渲染嫌疑
 *   （文件管理四期实证同类），走**物理移除**（微博方案：记录原状态，会话 restore 重挂）。
 * - 可见性门控：未发现底栏隐藏/alpha 写回的确证路径（tab 切换为 FragmentTransaction
 *   show/hide 单 Activity；详情页独立 Activity 不在探测范围）——mirrorVisibleGate 恒
 *   true，仅借帧循环逐帧重申压制与装饰移除（防换肤重放等路径翻回，§10.7 压制重申条）。
 * - tab 角标为死路径（VMenuViewLayout badge 表无 put、应用无 VBadgeDrawable 实例化，
 *   "升级"红点走顶栏 VToolbar）；主 tab 无 WebView（WebView 全在独立 Activity）——
 *   角标镜像与采样冻结通道均不投入。
 */
object IqooSecureAdapter : AppAdapter {

    override val targetPackage: String = "com.iqoo.secure"
    override val displayName: String = "vivo 手机管家"
    override val tabActivityClassName: String? = "com.iqoo.secure.MainActivity"
    override val baselineVersions: List<String> = listOf("9.3.19.0")

    private const val TAB_ACTIVITY = "com.iqoo.secure.MainActivity"
    // 底栏（非混淆 originui 库类）与其在 main_tab_activity_layout 中的资源 id（类名失配兜底）
    private const val BAR_CLASS = "com.originui.widget.navigation.VBottomNavigationView"
    private const val BAR_ID_NAME = "bottom_tab"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/文件管理/主题同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"

    // —— 压制运行期状态（弱引用防泄漏；原状态供会话还原） ——
    @Volatile private var barRef: WeakReference<View>? = null
    /** 已移除的底栏内部装饰（构造器 index 0 的无 id 顶边分割线等非 menuLayout 子视图），
     *  记录原挂载位置与可见性，会话 restore 重挂。 */
    private val hiddenDecorations = ArrayList<DecorationState>(2)
    /** 预览换图同态去重（条目 → 最近应用的 checked 态）：换图引擎只在选中态翻转时调用，
     *  但悬停边界抖动（原 tab ⇄ 悬停 tab 往返）与任何重放路径的同态重复调用对静态 drawable
     *  无感、对本应用却是重播 Lottie 选中动画——同态跳过（真机"悬停时动画不断播放"修复）。 */
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
        // 选中外观翻转方法：本应用 originui 方法级混淆，rh.b（VNavMenuItem）无 setChecked，
        // 视觉翻转 = y(boolean)（jadx 实证：纯视觉、末尾 setSelected(z10)，无切页副作用）。
        // 应用更新后改名 = 预览静默失效（不崩溃，回退为无预览）
        itemCheckedSetterMethodName = "y",
        // 单 tab 舒适宽度上限覆盖（全局 70dp，真机反馈 2 tab 收窄栏内图标偏小、标签拥挤）：
        // 原生 2 tab 底栏铺满屏宽、单条目约屏宽之半，70dp 槽位远窄于原生比例；96dp 槽位
        // （栏宽 2×96+8=200dp）与原生观感协调，为单应用声明（§0.4，默认 null 其余应用不变）
        maxTabWidthDp = 96,
        // 图标镜像盒覆盖（全局 24dp，真机反馈两轮"图标偏小"）：本应用图标素材为 vivo
        // 动态符号（FontSymbolDrawable），原生图标视图 ROM16 = 32dp 且符号字形在视图内
        // 另有内边距——玻璃观感对齐其他适配应用需再放大：40dp 盒（32dp 视图 fit 放大
        // 1.25x，字形视觉约 25dp 与相册等一致）；布局预留仍为 28dp 盒居中溢出，不挤标签
        iconBoxDp = 40,
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
        // 首选：menuLayout 的直接子项（顺序 = tab 顺序，rh.b / VNavMenuItem）
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
        // 换肤重放/未知路径把 alpha 写回 1 的纠错（帧循环恢复后由 mirrorVisibleGate 逐帧接管）
        barRef?.get()?.takeIf { it.isAttachedToWindow }?.let { if (it.alpha != 0f) it.alpha = 0f }
    }

    /** 早期压制兜底还原时限（未被会话认领时还原 alpha，防永久隐形）。 */
    private const val EARLY_SUPPRESS_FALLBACK_MS = 8_000L

    /** 早期压制实例（弱键集合；会话装配时认领移除）。 */
    private val earlySuppressed: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
    /** 底栏资源 id 缓存（getIdentifier 每实例解析开销可忽略，仅冷启动少数实例）。 */
    @Volatile private var bottomTabIdCache = 0

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        runCatching {
            // initialize=false：进程加载期宿主 Application 未建，连锁初始化会闪退（微博 §5.7 纪律）
            val barClass = Class.forName(BAR_CLASS, false, loader)
            // 本类**未声明** onFinishInflate（继承 View，无法按类 hook）——改 hook 布局 inflate
            // 路径的 (Context, AttributeSet) 构造器：inflate 完成即压制（早于首帧绘制，冷启动
            // 原栏闪现修复，主题 §10.2 早期压制三段式同款）。类内其他构造（纯代码创建）不挂。
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

    /** 底栏早期压制（冷启动原栏闪现修复，真机反馈；主题 §10.2 三段式同款）：底栏虽在
     *  setContentView 即装配，但探测/会话装配晚于首帧——首帧到玻璃就位之间原栏可见。
     *  构造完成即 alpha=0（此时 android:id 已由 View 构造器应用，按 `bottom_tab` id 门控，
     *  防误伤应用内其他页面的同库底栏）；会话装配时认领（此后压制/还原由会话接管，
     *  restore 恢复 alpha=1 与早期压制天然兼容）；[EARLY_SUPPRESS_FALLBACK_MS] 未认领
     *  （探测放弃/模块中途关闭/非主底栏实例）兜底还原 alpha=1。 */
    private fun suppressEarly(view: View, facade: HookFacade) {
        if (bottomTabIdCache == 0) {
            bottomTabIdCache = idByName(view, BAR_ID_NAME)
        }
        if (bottomTabIdCache == 0 || view.id != bottomTabIdCache) return
        synchronized(earlySuppressed) {
            if (!earlySuppressed.add(view)) return
        }
        view.alpha = 0f
        facade.log(android.util.Log.INFO, "iqoo-secure early-suppress bar (pre-install)")
        view.postDelayed({
            if (synchronized(earlySuppressed) { earlySuppressed.remove(view) }) {
                view.alpha = 1f
                facade.log(
                    android.util.Log.WARN,
                    "iqoo-secure early-suppress fallback restore (session never claimed)",
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
        reassertBarDecorations(session.tabContainer)
        // 早期压制认领（冷启动闪现修复，见 installAppHooks）：装配即由会话接管压制/还原
        earlySuppressed.remove(session.tabContainer)
        // 预览图标换图（拖动悬停）：引擎 setTabChecked 翻转 y() 后调用——悬停 tab 走底栏
        // A(item) 选中换图、被拖离的原选中走 m(item) 反选换图，与底栏 f()/M(int) 换图
        // 逐位同语义（无 l(item) 监听回调，无切页副作用）。方法每次换图前重新解析：
        // 换肤（ColorChangeUtils.y + O/Q 重放）会整体重建 f20811l 映射，不缓存。
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
     *  **物理移除**（微博/文件管理方案）：底栏全部非 menuLayout 直接子视图（构造器 index 0
     *  挂载的无 id 顶边分割线 f20814o 等）。alpha=0 理论可压制，但文件管理四期实证该类
     *  装饰存在绕过 view alpha 的渲染路径（vivo VBlur 接管背景绘制嫌疑最大，本应用
     *  `B(true)` 主动开启底栏 VBlur）——移除后应用的可见性/背景写入落在游离视图上；
     *  帧循环对运行期新加的装饰检出即移除。menuLayout（tab 条目容器）不可动。
     *  底栏子树不变量 = 只剩 menuLayout；非 menuLayout 子视图首见记录原状态、检出即移除，
     *  已移除的不会出现在树里，稳态零写入。 */
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
            "iqoo-secure decoration removed: ${view.javaClass.name} index=$index",
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

    /** 预览换图（拖动悬停，§1.2 统一语义 = 悬停 tab 选中**末帧**外观、离开恢复未选中）：
     *  - unchecked → 反射调底栏 `m(item)`（应用自身反选：停选中动画 + 静态非选中图，
     *    内含 y(false) 幂等翻转）；
     *  - checked → **不走 `A(item)`**（其 Lottie 路径 = B(lottie)+u() 重播选中动画，真机
     *    表现为"悬停时动画不断播放"）——按图标对映射（底栏字段真名 `l`，jadx "renamed
     *    from: l" 注释取证，显示名 f20811l 按名查必失败，vivo 官网 f12699c→c 同款坑）
     *    取选中态：静态 `h` 优先（a 类字段真名 a~h，A() 消费顺序 h→f→c→d），
     *    否则 Lottie `f` 停止 + setProgress 末帧（本 APK 混淆名 C(float)，jadx 实证）后
     *    `B()` 挂图。映射读取失败回退 `A(item)`（静默降级为重播）。
     *  - 同态去重（[lastAppliedChecked]）跳过重复调用；方法/字段每次实时解析（换肤
     *    ColorChangeUtils.y + O/Q 重放会整体重建映射），任一环节失败静默跳过（表现 =
     *    预览图标停留原 drawable，无崩溃）。 */
    private fun swapPreviewIcon(session: GlassSession, index: Int, checked: Boolean) {
        val item = session.tabViews.getOrNull(index) ?: return
        val bar = barRef?.get() ?: return
        synchronized(lastAppliedChecked) {
            if (lastAppliedChecked[item] == checked) return
        }
        val applied = runCatching {
            if (checked) applySelectedEndFrame(bar, item) else invokeItemSwap(bar, item, "m")
            true
        }.getOrDefault(false)
        if (applied) {
            synchronized(lastAppliedChecked) { lastAppliedChecked[item] = checked }
        }
    }

    /** checked 末帧语义：图标对静态选中图优先，Lottie 选中则停动画 + 跳末帧，经条目
     *  `B(Drawable)`（真名，jadx 无改名注释）挂图；映射/字段失配回退 `A(item)` 重播。 */
    private fun applySelectedEndFrame(bar: View, item: View) {
        val pair = iconPairOf(bar, item)
        val static = pair?.first
        val lottie = pair?.second
        if (static != null) {
            invokeItemIcon(item, static)
            return
        }
        if (lottie != null) {
            runCatching {
                val running = lottie.javaClass.getMethod("s").invoke(lottie) as Boolean
                if (running) lottie.javaClass.getMethod("h").invoke(lottie)
                lottie.javaClass
                    .getMethod("C", Float::class.javaPrimitiveType)
                    .invoke(lottie, 1.0f)
            }
            invokeItemIcon(item, lottie)
            return
        }
        invokeItemSwap(bar, item, "A")
    }

    /** 反射读图标对映射（VBottomNavigationView 真名字段 `l`：条目 → 图标对；jadx
     *  "renamed from: l" 注释取证，显示名 f20811l 按名查必失败）并按 A() 消费顺序取
     *  **选中态**：静态选中 `h`（Drawable）优先、Lottie 选中 `f` 次之（图标对 a 类字段
     *  真名 a~h，jadx "renamed from" 注释取证；非选中态为 b/g/e，勿混）。 */
    private fun iconPairOf(bar: View, item: View): Pair<Drawable?, Drawable?>? = runCatching {
        val map = bar.javaClass.getDeclaredField("l")
            .apply { isAccessible = true }
            .get(bar) as? Map<*, *> ?: return null
        val pair = map[item] ?: return null
        val static = runCatching {
            pair.javaClass.getDeclaredField("h").apply { isAccessible = true }.get(pair) as? Drawable
        }.getOrNull()
        val lottie = runCatching {
            pair.javaClass.getDeclaredField("f").apply { isAccessible = true }.get(pair) as? Drawable
        }.getOrNull()
        if (static != null || lottie != null) static to lottie else null
    }.getOrNull()

    private fun invokeItemSwap(bar: View, item: View, method: String) {
        bar.javaClass.getDeclaredMethod(method, item.javaClass)
            .apply { isAccessible = true }
            .invoke(bar, item)
    }

    private fun invokeItemIcon(item: View, icon: Drawable) {
        item.javaClass.getDeclaredMethod("B", Drawable::class.java)
            .apply { isAccessible = true }
            .invoke(item, icon)
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
