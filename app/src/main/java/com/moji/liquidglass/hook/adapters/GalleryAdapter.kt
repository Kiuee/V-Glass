package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.os.Handler
import android.os.Looper
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
    // 照片网格 GL 渲染根视图：onDraw 仅作"内容在动"的活动信号（升满帧率档），
    // 不作绘制门控（事件驱动的教训见 installAppHooks 注释）
    private const val GL_ROOT_VIEW_CLASS = "com.android.gallery3d.ui.GLRootView"

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
    private val handler = Handler(Looper.getMainLooper())

    /** 处于多选中的 SelectionManager 实例（弱引用键：页面销毁后条目自动失效）。 */
    private val selectionManagers = WeakHashMap<Any, Boolean>()
    private var selectionPollAt = 0L
    private var selectionInSelectionMode: java.lang.reflect.Method? = null

    /** 内容活动信号接收端（onSessionCreated 注入 → session.armActivityWindow）。 */
    @Volatile
    private var contentSignalListener: (() -> Unit)? = null

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
        // 事件驱动 tick：相册**刻意停用**（tickEventDriven 写死 false，见 onSessionCreated）。
        // GL 内容无法可靠转为重绘信号：页面切换过渡期首批 onDraw 是合成前的空白帧
        // （玻璃采样空白冻成"透明"）、图标动画与光效动画不触发 GLRootView.onDraw
        // （镜像静止/闪烁）。改为"静止降帧"方案（效果优先 + 控耗电）：
        // GLRootView.onDraw 只作**活动信号**（升满帧率档），不作绘制门控——静止后玻璃
        // 仍以 30fps 低频采样，空白帧最多停留 ~33ms 自愈，不会重蹈事件驱动的冻帧。
        runCatching {
            val glRoot = Class.forName(GL_ROOT_VIEW_CLASS, true, loader)
            facade.around(glRoot.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java), null) { _ ->
                contentSignalListener?.invoke()
                null
            }
            facade.log(android.util.Log.INFO, "hooked GLRootView.onDraw as content activity signal")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook GLRootView.onDraw failed (static 30fps tier always)", it)
        }
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
        // 背景采样去重：照片网格为 GL 渲染，三玻璃节点各自直绘采样 = 每帧 3 次 GL 绘制，
        // 滑动掉帧主因；开启后每 tick 单次录制全宽条带、三节点回放（远程配置可关）
        session.dedupeBackdropSampling = config().lightweightSampling
        // 事件驱动写死停用（GL 合成空窗/图标动画/光效三类效果问题，见 installAppHooks 注释）；
        // onDraw 信号仅用于升满帧率档（静止降帧方案），不门控绘制
        session.tickEventDriven = false
        contentSignalListener = { session.armActivityWindow() }
        // 玻璃特效渲染限帧（实验旋钮，默认 0=不限）：60Hz 实测产生拍频已废弃默认启用，
        // 仅留作 A/B 实验；与事件驱动叠加时会节流内容信号，慎用
        session.glassRenderIntervalNs = config().glassRenderCapHz
            .takeIf { it > 0 }?.let { 1_000_000_000L / it } ?: 0L
        // 拖离原选中 tab 的反选还原 = 应用自身 i(item) 原生语义（jadx 10.1.6.4 实证：
        // setChecked(false) + 停动画 + 按状态表换回未选中图）。setChecked 不换图标——
        // 选中观感由 bar 经 setItemSelected 挂选中 lottie@1f，仅 setChecked 时拖离后
        // 原 tab 仍显选中（与 vivo 官网 w(item) 同构问题，教训见 №03.7 教训 10）；
        // 钩子失败由引擎回退动画首帧（不崩溃）。
        session.previewIconUnselected = { index ->
            runCatching { unselectTabIcon(session, index) }.getOrDefault(false)
        }
        // 取证：定时输出底栏可见性/透明度与门控输入，验证隐藏跟随是否生效
        for (delay in longArrayOf(2_000, 4_000, 6_000, 8_000, 10_000)) {
            handler.postDelayed({
                runCatching {
                    val bar = session.tabContainer
                    val stateManager = activity.javaClass.getMethod("getStateManager").invoke(activity)
                    val topState = stateManager?.javaClass?.getMethod("getTopState")?.invoke(stateManager)
                    facade.log(
                        android.util.Log.INFO,
                        "bar probe: visibility=${bar.visibility} isShown=${bar.isShown} alpha=${bar.alpha} " +
                            "topState=${topState?.javaClass?.name} wanted=$barWantedVisible " +
                            "photoPage=$photoPageActive selection=${selectionManagers.size}",
                    )
                }.onFailure { facade.log(android.util.Log.WARN, "bar probe failed", it) }
            }, delay)
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
     *  （不崩溃）。 */
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
