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
 * vivo 健康（com.vivo.health，桌面名"健康"，版本 7.1.3.23 取证，兼容窗口含 7.1.3.13）适配器。
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
 * - 换图（图标双路径，均存底栏 LottieAttribute 映射 `Map<VNavMenuItem, LottieAttribute>`
 *   ——7.1.3.23 字段名 `p`、7.1.3.13 字段名 `F`，条目 → 图标对）：
 *   ① ROM16 动态符号路径（FontSymbolHelper.i()）：n() 添加 FontSymbolDrawable 对
 *   （选中=主题色、未选中=灰）；② 旧路径：l() 添加 静态未选中 res + 主题色染色的
 *   selected_anim Lottie。应用点击换图 = 未选 r(item) / 选中 O(item)（**携带
 *   `U(drawable)`=start() 状态动画副作用**，是正式点击的动画重播入口）。拖动悬停
 *   预览 = [enforcePreviewIcons] 以条目 isSelected 为真值源逐帧强制收敛（事件路
 *   previewIconSwapper + 帧循环路 mirrorVisibleGate 双入口）：不走 r/O，按库"编程
 *   选中换图" `W()` 的落定配方实例直挂 iconView——checked = 图标对选中符号 `i`（已
 *   预驱动 1.0f）、unchecked = 未选中符号 `h`，映射降级链 d/g/b/a-res；映射全缺失
 *   该条目跳过（标签强调色仍由 setChecked 原生驱动）；换主题（HealthThemeManager →
 *   a0/s0 重放）会整体重建映射——映射每次实时解析、不缓存，重建 = 新实例 = 引用
 *   失配 = 下一帧自动重挂（自愈）。
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
    override val baselineVersions: List<String> = listOf("7.1.3.13", "7.1.3.23")

    private const val TAB_ACTIVITY = "com.vivo.health.main.activity.MainActivity"
    // 底栏（应用自有子类，非混淆）与其在 activity_main.xml 中的资源 id（类名失配兜底）
    private const val BAR_CLASS = "com.vivo.health.main.widget.MainBottomNavigationView"
    private const val BAR_ID_NAME = "magic_indicator"
    // originui 库内部 menuLayout：非混淆类，tab 条目的稳定父容器
    private const val MENU_LAYOUT_SIMPLE_NAME = "VMenuViewLayout"
    // originui 库公共元素 id（与相册/文件管理/手机管家同库同命名）
    private const val ICON_ID_NAME = "vivo_navigation_bar_item_icon_view"
    private const val LABELS_ID_NAME = "vivo_navigation_bar_item_labels_group"
    // 图标对映射字段候选（沿底栏超类链按「字段名为 Map」判定）：7.1.3.23 = p，
    // 7.1.3.13 = F（库升版字段重排；两版菜单监听映射分别位于 q/G，勿混）
    private val ICON_PAIR_MAP_FIELD_CANDIDATES = listOf("p", "F")
    // 选中动画器字段候选（沿条目超类链按「字段类型 = ValueAnimator」判定）：
    // 7.1.3.23 = u，7.1.3.13 = K（库升版字段重排，同版本 K 已改为 int 徽标半径）
    private val SELECTION_ANIMATOR_FIELD_CANDIDATES = listOf("u", "K")

    // —— 压制运行期状态（弱引用防泄漏；原状态供会话还原） ——
    @Volatile private var barRef: WeakReference<View>? = null
    @Volatile private var sessionRef: WeakReference<GlassSession>? = null

    /** 已移除的底栏内部装饰（构造器 index 0 的无 id 顶边分割线等非 menuLayout 子视图），
     *  记录原挂载位置与可见性，会话 restore 重挂。 */
    private val hiddenDecorations = ArrayList<DecorationState>(2)

    /** 预览图标状态强制缓存：条目 → 图标 ImageView（逐帧路径免 findViewById）、
     *  resId → 已解析静态 drawable（引用相等判据需要稳定实例——`getDrawable` 每次
     *  新实例会让幂等判据失效而逐帧重挂）。恢复期主题重放清 res 缓存重建。 */
    private val iconViewCache = java.util.WeakHashMap<View, android.widget.ImageView?>()
    private val settledResCache = java.util.WeakHashMap<Int, android.graphics.drawable.Drawable>()

    /** 图标对映射字段实际命中名的单次日志守卫（升版核对 logcat 用）。 */
    @Volatile private var iconPairFieldLogged = false

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
        barRef?.get()?.let { neutralizeBarBlur(it) }
        // 主题重放（可在恢复期发生，如控制中心切深色后返回）会重建图标对、新选中符号
        // 进度归零——恢复期重驱动（幂等，已落定的驱动 = 无操作）；res 降级缓存一并清
        // （动态色主题下同名 res 的解析结果随主题变）
        settledResCache.clear()
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
        // 底栏动态模糊层中性化（见 neutralizeBarBlur）：全局模糊开启时 VBlur 磨砂条绕过
        // view alpha 直绘进窗口，越界翻转触发其重渲染 = 磨砂底亮度跳变（2.9.47 修复）
        neutralizeBarBlur(session.tabContainer)
        // 早期压制认领（冷启动闪现修复，见 installAppHooks）：装配即由会话接管压制/还原
        earlySuppressed.remove(session.tabContainer)
        // 选中符号预驱动（首悬停发浅修复，见 preDriveSelectedSymbols）
        preDriveSelectedSymbols(session)
        // 预览图标换图（拖动悬停）：引擎 setTabChecked 翻转 setChecked 后调用 + 帧循环
        // 逐帧收敛双路（见 enforcePreviewIcons）——以 isSelected 为真值源统一收敛到
        // 库 W()/n() 落定配方实例（映射 i/h 符号，已由 preDriveSelectedSymbols 预驱动），
        // **不走 O()/r()**（其 U(start) 会重播状态动画）；映射每次实时解析：换主题
        // （HealthThemeManager → a0/s0 重放）会整体重建 F 映射，重建 = 新实例 → 引用
        // 失配 → 下一帧自动重挂（自愈），不缓存。
        // 翻转条目的图标过渡动画瞬时落定（snapSelectionAnimation，2.9.47 滑动切换瞬间
        // 光斑/提亮闪烁修复）——setChecked 末尾 x(1.0f/0.0f) 对挂载条目启动 ~250ms 图标
        // 缩放 ValueAnimator，每次翻转重播、被逐 tick 镜像。
        session.previewIconSwapper = { index, checked ->
            snapSelectionAnimation(session, index, checked)
            enforcePreviewIcons(session)
        }
        // 压制重申 + 装饰重申（帧循环每帧执行，仅失配时写入）+ 预览图标逐帧收敛（一次性
        // apply 失败/被顶掉的自愈通道，闹钟 №16.7 教训 3 同款）：应用无确证隐藏路径，
        // 恒返回 true（barWanted 不变）；顺带检出运行期新加的装饰即移除。
        session.mirrorVisibleGate = {
            reassertSuppressions()
            enforcePreviewIcons(session)
            true
        }
        // 预览期采样冻结（2.9.47 滑动切换瞬间整栏提亮闪烁修复）：拖动悬停窗口内条带
        // 一次性录制后纯回放——越界翻转触发的底栏重渲染（含全局模糊开启时 VBlur 动态
        // 模糊层的重渲染，其绕过 view alpha 直绘进采样条带）不再进入磨砂底；预览结束
        // 自动解冻恢复实时采样。
        session.backdropFreezeGate = { session.isTabPreviewActive }
        session.onAuxGlassRestore = { restoreDecorations() }
    }

    private val blurNeutralized: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    /** 底栏动态模糊层中性化（2.9.47 滑动切换瞬间"整栏提亮/光斑"闪烁修复）：originui
     *  构造器尾在系统全局模糊开启时 setBlurEnable(true)——VBlur 磨砂条绕过 view alpha
     *  直绘进应用窗口（真机 logcat 实证：HWUI BlurEffectManager 逐帧重绘，早于模块
     *  注入且贯穿拖动悬停窗口），透过半透明玻璃可见；越界翻转触发其重渲染 = 磨砂底
     *  亮度跳变。反射调用公开 API setBlurEnable(false) 一次性关闭（内部同步置静态
     *  开关，setCardStyle 等重开路径同读一静态，重开不成立）；once-per-instance 守卫
     *  防重复反射。失败静默（表现 = 磨砂条残留，无崩溃）。 */
    private fun neutralizeBarBlur(bar: View) {
        synchronized(blurNeutralized) {
            if (!blurNeutralized.add(bar)) return
        }
        runCatching {
            generateSequence<Class<*>>(bar.javaClass) { it.superclass }
                .firstNotNullOfOrNull { cls ->
                    runCatching {
                        cls.getDeclaredMethod(
                            "setBlurEnable",
                            Boolean::class.javaPrimitiveType,
                        ).apply { isAccessible = true }
                    }.getOrNull()
                }?.invoke(bar, false)
        }
        android.util.Log.println(
            android.util.Log.INFO, "LiquidGlass",
            "vivo-health bar blur neutralized",
        )
    }

    /** 预览翻转后的图标过渡动画瞬时落定（2.9.47 滑动切换瞬间光斑/提亮闪烁修复）：
     *  originui `VNavMenuItem.setChecked` 末尾 `x(1.0f/0.0f)` 对挂载条目启动
     *  `motionDurationLong1` (~250ms、FastOutSlowIn) `ValueAnimator`（选中动画器字段，
     *  7.1.3.23 真名 `u` / 7.1.3.13 真名 `K`；两版均 public、类型 ValueAnimator——库升版
     *  字段整体重排，同版本 `K` 已改为 int 徽标半径）驱动选中过渡（`D(f,f)` → 辅助器
     *  `v.d(f,f,view)` 写落定），每次翻转重播、被逐 tick 镜像 = 每次切换一次"呼吸"式
     *  亮度跳变（深色放大）。本方法取消在飞动画器并反射 `D(float,float)`（两版均
     *  public final，jadx 方法体实证：`v.d(f2,f3,k)` + `w=f2` 进度记录）以 (end,end)
     *  瞬时写落定——同栈赶在下一个采样 tick 前，镜像只见落定帧，与引擎"悬停 = 动画末帧
     *  落定外观"预览语义一致。锚点沿超类链按「字段类型 = ValueAnimator」判定（子类
     *  HealthBottomNavigationView 不改声明）；任一环节失败静默（表现 = 动画照播，与既有
     *  行为一致，无回归面）。 */
    private fun snapSelectionAnimation(session: GlassSession, index: Int, checked: Boolean) {
        val item = session.tabViews.getOrNull(index) ?: return
        val end = if (checked) 1f else 0f
        runCatching {
            val animatorField = generateSequence<Class<*>>(item.javaClass) { it.superclass }
                .firstNotNullOfOrNull { cls ->
                    SELECTION_ANIMATOR_FIELD_CANDIDATES.firstNotNullOfOrNull { name ->
                        runCatching { cls.getDeclaredField(name) }.getOrNull()
                            ?.takeIf {
                                android.animation.ValueAnimator::class.java
                                    .isAssignableFrom(it.type)
                            }
                    }
                }
            animatorField?.isAccessible = true
            (animatorField?.get(item) as? android.animation.ValueAnimator)?.cancel()
        }
        runCatching {
            val snap = generateSequence<Class<*>>(item.javaClass) { it.superclass }
                .firstNotNullOfOrNull { cls ->
                    runCatching {
                        cls.getDeclaredMethod(
                            "D",
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                        ).apply { isAccessible = true }
                    }.getOrNull()
                }
            snap?.invoke(item, end, end)
        }
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

    /** 预览图标强制收敛（拖动悬停，§1.2 统一语义 = 悬停 tab 选中**落定**外观、离开
     *  恢复未选中落定）：以条目 `isSelected` 为**唯一真值源**，把全部条目的 iconView
     *  收敛到期望落定 drawable（`drawable === desired` 引用相等 = 已就位零写入，天然
     *  幂等）。仅 [GlassSession.isTabPreviewActive] 期间执行——正式切页的应用换图不被
     *  顶掉。期望实例来自底栏图标对映射（`Map<VNavMenuItem, LottieAttribute>`，7.1.3.23
     *  字段 `p` / 7.1.3.13 字段 `F`，每次实时
     *  解析——换主题 HealthThemeManager → a0/s0 重放会整体重建映射，映射重建 = 新实例
     *  → 引用失配 → 下一帧自动重挂，自愈）：
     *  - checked → 选中符号 `i`（已由 [preDriveSelectedSymbols] 预驱动到落定进度，挂载
     *    即瞬时落定）；`i` 缺失按 `W()` 取值序降级静态 `d` → 单 drawable `g`；
     *  - unchecked → 未选中符号 `h`（库 `n()` 创建配方落定 = 1.0f）；`h` 缺失降级静态
     *    `b` → res `a`（经 [settledResCache] 稳定实例）。
     *  **不走 `O(item)`/`r(item)` 兜底**（库点击路径换图携带 `U(drawable)`=
     *  `FontSymbolDrawable.start()` 状态动画，逐帧路径复用即动画重播，№11.7 教训 2）；
     *  **直挂 iconView 绕过 `VNavMenuItem.setIcon`**（其开头实例去重会让同源二次换入
     *  提前 return，闹钟 №16.7 教训 1 同款陷阱）。
     *  **为何逐帧**（闹钟 №16.7 教训 3 同款）：事件驱动换图是"一次性 apply"——引擎对
     *  同一悬停目标去重后不再补发事件，一次 apply 失败/被顶掉后悬停期间没有第二次
     *  机会；逐帧把图标外观变成 isSelected 的纯函数，任何一帧意外状态 ≤16ms 自愈。 */
    private fun enforcePreviewIcons(session: GlassSession) {
        if (!session.isTabPreviewActive) return
        val bar = barRef?.get() ?: return
        for (item in session.tabViews) {
            val iconView = iconViewCache.getOrPut(item) { iconViewOf(item) } ?: continue
            val desired = desiredIconFor(bar, item, item.isSelected) ?: continue
            if (iconView.drawable === desired) continue
            runCatching { iconView.setImageDrawable(desired) }
        }
    }

    /** 期望落定 drawable（映射/字段每次实时解析，任一环节失败返回 null = 该条目本帧
     *  跳过，标签强调色切换仍由 setChecked 原生驱动）。 */
    private fun desiredIconFor(bar: View, item: View, checked: Boolean): android.graphics.drawable.Drawable? {
        val pair = iconPairOf(bar, item) ?: return null
        val direct = if (checked) {
            drawableField(pair, "i") ?: drawableField(pair, "d") ?: drawableField(pair, "g")
        } else {
            drawableField(pair, "h") ?: drawableField(pair, "b")
        }
        if (direct != null) return direct
        if (!checked) {
            val resId = intField(pair, "a")
            if (resId != 0) {
                return settledResCache.getOrPut(resId) { item.context.getDrawable(resId) }
            }
        }
        return null
    }

    /** 条目的图标 ImageView（originui 库公共元素 id）。 */
    private fun iconViewOf(item: View): android.widget.ImageView? {
        val iconId = idByName(item, ICON_ID_NAME)
        if (iconId == 0) return null
        return item.findViewById<View>(iconId) as? android.widget.ImageView
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

    /** 反射读底栏图标对映射（条目 → LottieAttribute；7.1.3.23 字段名 `p` / 7.1.3.13
     *  字段名 `F`——originui 库升版字段整体重排，同版本 `q`/`G` 为菜单点击监听映射，
     *  不是图标对）。候选按名 + 「字段类型 = Map」+ 「映射含该条目」判定，沿底栏超类链
     *  查找（不锚库类名）；映射或条目缺失返回 null（静默降级，无崩溃面）。 */
    private fun iconPairOf(bar: View, item: View): Any? {
        var typeOnlyMatch: java.lang.reflect.Field? = null
        for (cls in generateSequence<Class<*>>(bar.javaClass) { it.superclass }) {
            for (name in ICON_PAIR_MAP_FIELD_CANDIDATES) {
                val field = runCatching { cls.getDeclaredField(name) }.getOrNull() ?: continue
                if (!Map::class.java.isAssignableFrom(field.type)) continue
                runCatching { field.isAccessible = true }
                val pair = runCatching { (field.get(bar) as? Map<*, *>)?.get(item) }.getOrNull()
                if (pair != null) {
                    logIconPairField(field)
                    return pair
                }
                if (typeOnlyMatch == null) typeOnlyMatch = field
            }
        }
        // 字段命中但本帧映射尚无该条目（主题重放重建空窗等）：按同类型字段取一次（可能仍 null）
        val fallback = typeOnlyMatch ?: return null
        logIconPairField(fallback)
        return runCatching { (fallback.get(bar) as? Map<*, *>)?.get(item) }.getOrNull()
    }

    /** 图标对映射字段首次命中名日志（升版 logcat 核对用，应用级一次）。 */
    private fun logIconPairField(field: java.lang.reflect.Field) {
        if (iconPairFieldLogged) return
        iconPairFieldLogged = true
        android.util.Log.println(
            android.util.Log.INFO, "LiquidGlass",
            "vivo-health icon pair map field = ${field.name} (Map)",
        )
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

    /** 符号进度直驱（`FontSymbolDrawable.setAnimProgress(float)`，与库 `W()` 落定分支/
     *  文件管理 §9.2 `r(drawable,1.0f)` 直驱先例同款；非符号 drawable 跳过——静态图无进度态）。 */
    private fun driveSymbolProgress(drawable: android.graphics.drawable.Drawable, progress: Float) {
        if (!drawable.javaClass.name.contains("FontSymbolDrawable")) return
        runCatching {
            drawable.javaClass.getMethod("setAnimProgress", Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(drawable, progress)
        }
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
