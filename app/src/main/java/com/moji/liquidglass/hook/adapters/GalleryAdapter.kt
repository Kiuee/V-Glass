package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.util.WeakHashMap

/**
 * vivo 相册（com.vivo.gallery）适配器。
 *
 * - 底栏：`com.android.gallery3d.vivo.main.base.GalleryBottomBar`
 *   （extends com.originui.widget.navigation.VBottomNavigationView），类名非混淆。
 * - 每 tab 视图：内部 menuLayout 的 `com.originui.widget.navigation.VNavMenuItem`
 *   （FrameLayout，含 Lottie/静态图标 + 双 VMenuTextView 标签 + 角标），选中态 = 标准
 *   View.isSelected（setChecked → setSelected）→ 选中同步/点击转发与商店完全同构。
 * - 图标镜像：WHOLE_TAB 元素级直绘（图标/标签组一体，应用更新免疫；无刷新变形场景）。
 * - 详情页跟随：照片大图 = PhotoPage State，应用用全屏页面"盖住"底栏而非 GONE
 *   （PhotoPage 不调 changedBottomBarVisible；shouldShowBottomBar 基类只看布局模式，
 *   普通手机布局恒 true），故 hook 其 onResume/onPause 翻转门控隐藏/恢复玻璃栏。
 * - 多选模式跟随：网格/相册集多选时应用以选择操作栏盖住底栏（shouldShowBottomBar =
 *   !inSelectionMode），hook data_api SelectionManager 的 enter/leaveSelectionMode
 *   维护活跃实例弱引用表（随页面销毁自愈），门控内低频复核 inSelectionMode 兜底。
 * - 事件驱动 tick：**停用**（GL 内容无法可靠转为重绘信号：页面切换空窗期玻璃采样空白
 *   冻成透明、图标/光效动画不触发 GLRootView.onDraw 导致镜像静止或闪烁）；玻璃每帧渲染，
 *   背景采样去重（dedupe）控制每帧成本。
 * - 内容延伸：暂未发现列表底部留白问题，Noop（反编译确认需要时再补专属策略）。
 */
object GalleryAdapter : AppAdapter {

    override val targetPackage: String = "com.vivo.gallery"
    override val displayName: String = "vivo 相册"
    override val tabActivityClassName: String? = "com.android.gallery3d.vivo.GalleryTabActivity"
    override val baselineVersions: List<String> = listOf("10.1.6.4")

    private const val BAR_CLASS = "com.android.gallery3d.vivo.main.base.GalleryBottomBar"
    // 注意双层 navigation 包（jadx 目录 navigation/navigation/ 实证）
    private const val ITEM_CLASS = "com.originui.widget.navigation.navigation.VNavMenuItem"
    // 照片大图详情 State（GalleryTabActivity 状态栈内，非混淆类）
    private const val PHOTO_PAGE_CLASS = "com.android.gallery3d.app.PhotoPage"
    // 多选模式管理器（公开 data_api 类，非混淆）：网格/相册集等页面共用
    private const val SELECTION_MANAGER_CLASS = "com.vivo.data_api.manager.c"
    // 照片网格 GL 渲染根视图：onDraw 仅作"内容在动"的活动信号（升满帧率档，引擎
    // 单管线广播），不作绘制门控（GL 空窗/动画不可靠的教训见 onSessionCreated 注释）
    private const val GL_ROOT_VIEW_CLASS = "com.android.gallery3d.ui.GLRootView"

    /**
     * 应用专属内容活动信号类（引擎单管线声明，hook/分发/降帧由引擎统一负责）：
     * GLRootView.onDraw = GL 渲染帧（页面切换/滑动/内容变化时触发，静止不触发）。
     * 本应用此前手写 facade.around hook 已退役并入单管线（walk-up 落 GLRootView 自身
     * 的 onDraw 声明点，行为等价）。
     */
    override val contentActivitySignalClasses: List<String> = listOf(GL_ROOT_VIEW_CLASS)

    private fun isNavItem(v: View): Boolean =
        v.javaClass.name == ITEM_CLASS || v.javaClass.simpleName == "VNavMenuItem"

    private fun isBottomBar(v: View): Boolean =
        v.javaClass.name == BAR_CLASS || v.javaClass.simpleName == "GalleryBottomBar"

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB,
        fallbackProbe = false,
        // 元素级镜像：只画图标与标签组，排除 M3 选中胶囊指示器与条目背景（观感对齐商店）
        elementIds = mapOf(
            "icon" to "vivo_navigation_bar_item_icon_view",
            "labels" to "vivo_navigation_bar_item_labels_group",
        ),
    )

    // —— 底栏可见性跟踪 ——
    // ① changedBottomBarVisible/setVisibility：平板 side-view 布局与儿童模式走原生真隐藏。
    // ② 照片详情（PhotoPage）：应用以全屏页面盖住底栏，不走①的任何方法——
    //    唯一可靠信号是 PhotoPage 状态生命周期（进入大图 onResume，返回列表 onPause）。
    // ③ 多选模式（SelectionManager）：选择操作栏盖住底栏，hook 进出维护活跃实例表，
    //    弱引用随页面销毁自愈，门控内低频复核 inSelectionMode 兜底。
    @Volatile
    private var barWantedVisible = true
    @Volatile
    private var photoPageActive = false

    /** 处于多选中的 SelectionManager 实例（弱引用键：页面销毁后条目自动失效）。 */
    private val selectionManagers = WeakHashMap<Any, Boolean>()
    private var selectionPollAt = 0L
    private var selectionInSelectionMode: java.lang.reflect.Method? = null

    /** 是否有页面处于多选模式（500ms 节流复核：应用绕过 leave 的退出路径也能自愈）。 */
    private fun selectionModeActive(): Boolean {
        if (selectionManagers.isEmpty()) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - selectionPollAt > 500) {
            selectionPollAt = now
            val method = selectionInSelectionMode ?: return true
            val iterator = selectionManagers.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val inSelection = runCatching { method.invoke(entry.key) as? Boolean }.getOrNull()
                if (inSelection != true) iterator.remove()
            }
        }
        return selectionManagers.isNotEmpty()
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        try {
            val barClass = Class.forName(BAR_CLASS, true, loader)
            val changed = barClass.getDeclaredMethod(
                "changedBottomBarVisible",
                Boolean::class.javaPrimitiveType,
            )
            facade.around(changed, null) { call ->
                barWantedVisible = call.args.firstOrNull() as? Boolean ?: true
                null
            }
            val setVisibility = runCatching {
                barClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            }.getOrNull()
            if (setVisibility != null) {
                facade.around(setVisibility, null) { call ->
                    barWantedVisible = (call.args.firstOrNull() as? Int) == View.VISIBLE
                    null
                }
            }
            facade.log(android.util.Log.INFO, "hooked GalleryBottomBar visibility tracking")
        } catch (t: Throwable) {
            facade.log(android.util.Log.WARN, "hook GalleryBottomBar visibility tracking failed", t)
        }
        runCatching {
            val pageClass = Class.forName(PHOTO_PAGE_CLASS, true, loader)
            facade.around(pageClass.getDeclaredMethod("onResume"), null) { _ ->
                photoPageActive = true
                null
            }
            facade.around(pageClass.getDeclaredMethod("onPause"), null) { _ ->
                photoPageActive = false
                null
            }
            facade.log(android.util.Log.INFO, "hooked PhotoPage lifecycle tracking")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook PhotoPage lifecycle tracking failed", it)
        }
        runCatching {
            val managerClass = Class.forName(SELECTION_MANAGER_CLASS, true, loader)
            selectionInSelectionMode = managerClass.getDeclaredMethod("inSelectionMode")
            facade.around(managerClass.getDeclaredMethod("enterSelectionMode"), null) { call ->
                call.thisObj?.let { selectionManagers[it] = true }
                null
            }
            facade.around(managerClass.getDeclaredMethod("leaveSelectionMode"), null) { call ->
                call.thisObj?.let { selectionManagers.remove(it) }
                null
            }
            facade.log(android.util.Log.INFO, "hooked SelectionManager selection-mode tracking")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook SelectionManager selection-mode tracking failed", it)
        }
        // 内容活动信号：GLRootView.onDraw 经引擎单管线接线（contentActivitySignalClasses
        // 声明，TargetHook 进程级安装）——GLRootView.onDraw 只作**活动信号**（升满帧率
        // 档），不作绘制门控：GL 内容无法可靠转为重绘信号（页面切换过渡期首批 onDraw
        // 是合成前空白帧、图标/光效动画不触发 GLRootView.onDraw），静止后玻璃仍以 30fps
        // 低频采样，空白帧最多停留 ~33ms 自愈。手写 hook 已退役（2026-11-04 单管线收敛）。
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 镜像显隐门控：进入照片大图（PhotoPage onResume）或多选模式整条隐藏，退出恢复；
        // 平板 side-view / 儿童模式的原生真隐藏走 barWantedVisible。
        session.mirrorVisibleGate = {
            barWantedVisible && !photoPageActive && !selectionModeActive()
        }
        // 背景采样去重：由 TargetHook 会话创建期统一设置（引擎单管线，默认开；远程配置
        // lightweightSampling 可关）。照片网格为 GL 渲染，去重后每 tick 单次录制条带、
        // 三节点回放（GL 整屏重绘 3 次 → 1 次）
        // 实时显示列表采样：相册 `view.draw(canvas)` 会进入 GLRootView.onDraw 把整屏照片
        // 网格重画一遍（主线程每帧重编码，低速滑动掉帧直接可见）。改为引用内容视图自身
        // 已录制的 RenderNode：条带只在几何变化时重录，其余帧纯回放，内容仍是当帧实时
        // （无降频滞后）；隐藏 API 取不到时引擎自动回退 view.draw（不崩溃）。
        session.backdropLiveNodeSampling = true
        // 预览期采样冻结（2.9.47 滑动切换瞬间整栏提亮闪烁修复）：拖动悬停窗口内条带
        // 一次性录制后纯回放，越界翻转触发的底栏重渲染不再进入磨砂底；预览结束自动
        // 解冻恢复实时采样（实时显示列表采样随之恢复）。
        session.backdropFreezeGate = { session.isTabPreviewActive }
        // 底栏动态模糊层中性化（见 neutralizeBarBlur）：全局模糊开启时 VBlur 磨砂条绕过
        // view alpha 直绘进窗口，越界翻转触发其重渲染 = 磨砂底亮度跳变（2.9.47 修复）
        neutralizeBarBlur(session.tabContainer)
        // 静止降帧由 TargetHook 会话创建期统一接线（引擎单管线）；GLRootView.onDraw
        // 信号仅用于升满帧率档（GL 合成空窗/图标动画/光效不触发 GL 绘制，不门控绘制
        // ——GL 空窗的空白帧由静止档低频采样自愈）。旧事件驱动通道已退役。
        // 拖离原选中 tab 的反选还原（2.9.47 落定化优先）：落定变体复刻 i(item) 静态
        // 前缀、剪掉动画分支（滑动切换瞬间光斑/提亮闪烁修复——镜像逐 tick 跟随，动画
        // 重播帧读作切换瞬间抖动）；反射失效回退应用原生 i(item)（带动画但外观正确
        // ——API 漂移时降级不黑图标）。setChecked 不换图标——选中观感由 bar 经
        // setItemSelected 挂选中 lottie@1f，仅 setChecked 时拖离后原 tab 仍显选中
        // （与 vivo 官网 w(item) 同构问题，教训见 №03.7 教训 10）。
        session.previewIconUnselected = { index ->
            runCatching { unselectTabIconSettled(session, index) }.getOrDefault(false) ||
                runCatching { unselectTabIcon(session, index) }.getOrDefault(false)
        }
    }

    /** 仅主 Tab Activity 探测：OnlineAgentActivity 等页面也挂了同类底栏，避免误装。 */
    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    /** 拖离原选中 tab 的反选还原 = 调应用自身 `VBottomNavigationView.i(item)`（jadx
     *  10.1.6.4 方法体实证：setChecked(false) + 动画播放中则取消 + 按状态表逐级换回
     *  未选中图（drawable → res → 未选中 lottie 播放 / 选中 lottie 回 0f）；应用的
     *  选中路径 setItemSelected 对称地挂选中 lottie@1f）。单锚点 = 方法真名 `"i"`
     *  （protected，声明在父类 VBottomNavigationView，沿超类链查找），参数取条目
     *  运行时类（VNavMenuItem，不硬编码）。失效返回 false → 引擎回退动画首帧
     *  （不崩溃）。**2.9.47 起为落定变体（unselectTabIconSettled）失效时的回退路径**
     *  （带动画但外观正确）。 */
    private fun unselectTabIcon(session: GlassSession, index: Int): Boolean {
        val item = session.tabViews.getOrNull(index) ?: return false
        val bar = session.tabContainer
        val unselect = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                runCatching { cls.getDeclaredMethod("i", item.javaClass) }.getOrNull()
            } ?: return false
        unselect.isAccessible = true
        unselect.invoke(bar, item)
        return true
    }

    /** 拖离原选中 tab 的反选还原·**落定变体**（2.9.47 滑动切换瞬间光斑/提亮闪烁修复）：
     *  反射复刻应用自身 `i(item)`（jadx 10.1.6.4 方法体取证）的**静态前缀**，剪掉两处
     *  动画源——①静态首选分支原版在 setIcon 后调 `vfontsymbol.c.start(h)`
     *  （FontSymbolDrawable 状态动画重播）→ 落定变体仅 setIcon 不 start；②lottie 回退
     *  （原版未选中 e `playAnimation`）改停止 + 定帧：e 定 1.0f（OUT 动画终点 = 未选中
     *  落定，库内选中路径对 e 同款 `setProgress(1.0f)` 实证）、f 定 0.0f（应用自身
     *  "选中 lottie 回 0f"同款停止语义）。字段真名（jadx "renamed from"，单字母与包名
     *  冲突被改显示名）：bar 映射 `t`（Map<VNavMenuItem,b>，沿超类链找声明类），状态对
     *  `h`/`b` 静态图、`a` res、`e` 未选中 lottie、`f` 选中 lottie、`i` 当前挂载。
     *  setChecked(false) 由引擎 setTabChecked 先行（本钩子在其后调用），不复翻。
     *  反射任一环节失效返回 false → 调用方回退应用原生 i(item)（API 漂移降级不黑图标）。 */
    private fun unselectTabIconSettled(session: GlassSession, index: Int): Boolean {
        val item = session.tabViews.getOrNull(index) ?: return false
        val bar = session.tabContainer
        val mapField = generateSequence<Class<*>>(bar.javaClass) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                runCatching { cls.getDeclaredField("t").apply { isAccessible = true } }.getOrNull()
            } ?: return false
        val state = (mapField.get(bar) as? Map<*, *>)?.get(item) ?: return false

        fun stateField(name: String): Any? =
            generateSequence<Class<*>>(state.javaClass) { it.superclass }
                .firstNotNullOfOrNull { cls ->
                    runCatching {
                        cls.getDeclaredField(name).apply { isAccessible = true }.get(state)
                    }.getOrNull()
                }

        fun stopLottieAt(lottie: Any, progress: Float) {
            runCatching {
                if (lottie.javaClass.getMethod("isRunning").invoke(lottie) as Boolean) {
                    lottie.javaClass.getMethod("cancelAnimation").invoke(lottie)
                }
            }
            runCatching {
                lottie.javaClass
                    .getMethod("setProgress", Float::class.javaPrimitiveType)
                    .invoke(lottie, progress)
            }
        }

        // 停在播动画（选中 lottie f / 当前挂载 drawable i——i(item) 原版同款前缀）
        stateField("f")?.let { f ->
            runCatching {
                if (f.javaClass.getMethod("isRunning").invoke(f) as Boolean) {
                    f.javaClass.getMethod("cancelAnimation").invoke(f)
                }
            }
        }
        (stateField("i") as? android.graphics.drawable.Drawable)?.let { current ->
            runCatching {
                if (current.javaClass.getMethod("isRunning").invoke(current) as Boolean) {
                    current.javaClass.getMethod("cancelAnimation").invoke(current)
                }
            }
        }

        val setIconDrawable = runCatching {
            item.javaClass.getMethod("setIcon", android.graphics.drawable.Drawable::class.java)
                .apply { isAccessible = true }
        }.getOrNull() ?: return false
        val setIconRes = runCatching {
            item.javaClass.getMethod("setIcon", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull()

        fun mount(drawable: android.graphics.drawable.Drawable): Boolean =
            runCatching { setIconDrawable.invoke(item, drawable) }.isSuccess

        // 状态表静态前缀（h → b → a，i(item) 原版同序；不调 vfontsymbol start）
        (stateField("h") as? android.graphics.drawable.Drawable)?.let { return mount(it) }
        (stateField("b") as? android.graphics.drawable.Drawable)?.let { return mount(it) }
        (stateField("a") as? Int)?.takeIf { it != 0 }?.let { resId ->
            if (setIconRes == null) return false
            return runCatching { setIconRes.invoke(item, resId) }.isSuccess
        }
        // 静态全缺 → lottie 落定定帧（不播放）
        (stateField("e") as? android.graphics.drawable.Drawable)?.let { e ->
            stopLottieAt(e, 1.0f)
            return mount(e)
        }
        (stateField("f") as? android.graphics.drawable.Drawable)?.let { f ->
            stopLottieAt(f, 0.0f)
            return mount(f)
        }
        return false
    }

    private val blurNeutralized: MutableSet<View> =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    /** 底栏动态模糊层中性化（2.9.47 滑动切换瞬间"整栏提亮/光斑"闪烁修复）：originui
     *  构造器尾在系统全局模糊开启时 setBlurEnable(true)——VBlur 磨砂条绕过 view alpha
     *  直绘进应用窗口（真机 logcat 实证：HWUI BlurEffectManager 逐帧重绘），透过半透
     *  明玻璃可见；越界翻转触发其重渲染 = 磨砂底亮度跳变。反射调用公开 API
     *  setBlurEnable(false) 一次性关闭（内部同步置静态开关，重开路径同读一静态，重开
     *  不成立）；once-per-instance 守卫防重复反射。失败静默（表现 = 磨砂条残留，无崩溃）。 */
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
            "vivo-gallery bar blur neutralized",
        )
    }

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
        return found.firstOrNull()
    }

    override fun tabViewsOf(bar: View): List<View> {
        val items = ArrayList<View>()
        fun walk(v: View?) {
            if (v == null) return
            if (isNavItem(v)) {
                items.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(bar)
        return items
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }
}
