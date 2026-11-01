package com.moji.liquidglass.glass

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.moji.liquidglass.config.ModuleConfig

/** 玻璃栏样式参数（模块设置页可调，经 GlassInstaller.glassStyle 下发到玻璃层）。 */
data class GlassStyle(
    /** 两侧水平边距（dp）：玻璃胶囊不再顶到屏幕边缘。 */
    /** 玻璃栏最小安全边距（dp）：实际边距 = max(推导边距, 此值)，
     *  tab 少时栏收窄为内容宽居中，边距自动放大但不低于此下限。 */
    val minHorizontalMarginDp: Float = 20f,
    /** 小白条上方的额外间隙（dp），叠加在导航栏 inset 之上。 */
    val bottomGapDp: Float = 0f,
    /** 栏高（dp），内容行 = 栏高 - 8dp。 */
    val barHeightDp: Float = 60f,
    /** 折射模糊半径（dp）。 */
    val blurRadiusDp: Float = 8f,
    /** 玻璃表面色不透明度（0~1）：叠在模糊内容上的底色浓度，0 = 纯透明玻璃。 */
    val surfaceAlpha: Float = 0.4f,
    /** 色彩饱和度：模糊内容的鲜艳度（vibrancy），默认 1.5 = 历史硬编码。 */
    val saturation: Float = 1.5f,
    /** 折射强度：玻璃边缘与液泡折射位移的缩放系数，0 = 无折射平边。 */
    val refractionScale: Float = 1f,
    /** 是否显示 tab 文字标签。 */
    val showLabels: Boolean = true,
) {
    companion object {
        fun fromConfig(config: ModuleConfig): GlassStyle = GlassStyle(
            minHorizontalMarginDp = config.barMarginDp,
            bottomGapDp = config.barBottomGapDp,
            barHeightDp = config.barHeightDp,
            blurRadiusDp = config.barBlurDp,
            surfaceAlpha = config.barSurfaceAlpha,
            saturation = config.barSaturation,
            refractionScale = config.barRefractionScale,
            showLabels = config.barShowLabels,
        )
    }
}

/** 从原底栏提取出的单个 tab 数据。 */
class GlassTab(
    val title: String,
    /** LOTTIE_RESOLVE 模式：应用真实 Lottie 图标视图（玻璃层逐帧镜像绘制）。 */
    val iconView: View?,
    /** Lottie 图标按当前帧位图化的结果（3x 采样，视图镜像不可用时兜底）。 */
    val icon: android.graphics.Bitmap?,
    /** WHOLE_TAB 模式：整个 tab 视图（图标/标签/角标一体，逐帧镜像）。 */
    val tabView: View? = null,
    /** true = 镜像整个 tab 视图（WHOLE_TAB）；false = 图标镜像 + Compose 标签/角标。 */
    val mirrorWholeTab: Boolean = false,
    /** 角标视图（数字/红点，含自身动画），镜像绘制优先于文本/圆点兜底。 */
    val badgeView: View?,
    /** update_noti_num 数字角标内容，null 表示无数字角标。 */
    val badgeText: String?,
    /** 引导点/红点角标（guide_iv、trash_noti_red_dot 可见时为 true）。 */
    val badgeDot: Boolean,
)

/** 整条玻璃底栏的装配参数。 */
class GlassBarSpec(
    val tabs: List<GlassTab>,
    val initialIndex: Int,
    /** 未选中内容色（取自应用文字选择器 default 色）。null 时 GlassRoot 回退参考项目配色。 */
    val contentColor: Color?,
    /** 选中强调色（取自应用文字选择器 selected 色）。null 时回退参考项目 iOS 蓝。 */
    val accentColor: Color?,
)

/**
 * 一次玻璃底栏装配的会话：持有原视图引用用于选中同步 / 点击转发 / 还原。
 * hook 层（各 AppAdapter）与玻璃层（GlassRoot）共同持有。
 *
 * 覆盖层模式下应用内容视图原位不动，本会话只管理 ComposeView 覆盖层与底栏可见性。
 */
class GlassSession(
    /** 应用内容根视图（原位保留，作为玻璃采样的绘制源）。 */
    val appRoot: View,
    /** 原底栏容器（装配时可见但隐形 alpha=0）。 */
    val tabContainer: View,
    /** 原 tab 视图列表（保持在视图树中，用于 isSelected 读取与 performClick 转发）。 */
    val tabViews: List<View>,
    val composeView: View,
    /** 描述符驱动的提取器（动画驱动/拖动预览按角色解析视图）。 */
    val extractor: TabExtractor,
) {
    /** 装配时原底栏的可见性，还原时恢复。 */
    var originalContainerVisibility: Int = tabContainer.visibility
        private set

    /** 装配时原底栏的 a11y 参与度，还原时恢复（装配后被 NO_HIDE_DESCENDANTS 隐身）。 */
    var originalContainerImportance: Int = tabContainer.importantForAccessibility
        private set

    /** 装配前 decorView 上原有的 ViewTree owner（GlassInstaller 覆盖 decor owner 前捕获、
     *  经 [attachDecorOwners] 注入，还原时恢复——避免模块 owner 永久污染应用自身解析）。 */
    private var originalDecorOwners: Triple<Any?, Any?, Any?>? = null
    private var originalDecorRef: java.lang.ref.WeakReference<View>? = null

    /** 装配时（覆盖 decor owner 之前）由 GlassInstaller 注入的 owner 快照与载体视图。 */
    fun attachDecorOwners(
        decor: View,
        lifecycle: Any?,
        viewModel: Any?,
        savedState: Any?,
    ) {
        originalDecorRef = java.lang.ref.WeakReference(decor)
        originalDecorOwners = Triple(lifecycle, viewModel, savedState)
    }

    /** 玻璃层检测到原视图树被应用重建（失联）时回调，hook 层据此重装。 */
    @Volatile
    var onStale: (() -> Unit)? = null

    /** 镜像绘制门控（适配器注入）：返回 false 时玻璃镜像不绘制（应用要求隐藏底栏）。 */
    @Volatile
    var mirrorVisibleGate: (() -> Boolean)? = null

    // —— 辅助玻璃视图（声明式通道，默认 null = 其余应用零影响）——
    // 应用内除底栏外需要"同款玻璃底"的视图（如文件管理文件页的存储状态条
    // space_situation）：原生视图由适配器压制（alpha=0 + 背景置空 + 宽度包裹内容），
    // 引擎逐帧镜像内容 + 绘制同款玻璃胶囊（采样同源、样式参数取自 GlassStyle）。
    /** 辅助玻璃视图提供者（帧循环每 tick 调用，返回当前有效的视图列表；空/null = 不渲染）。
     *  压制（alpha/背景/宽度）由适配器负责（应用专属知识），引擎只负责镜像与玻璃绘制。 */
    @Volatile
    var auxGlassViews: (() -> List<View>)? = null

    /** 辅助玻璃还原回调（restore() 尾部调用）：适配器恢复压制视图的原 alpha/背景/宽度
     *  与分割线等一次性显隐修改。 */
    @Volatile
    var onAuxGlassRestore: (() -> Unit)? = null

    /** 辅助玻璃形状钩子（默认 null = 胶囊 Capsule）：按视图声明玻璃底形状（如
     *  文件管理存储条用圆角矩形更贴近原生形态）。 */
    @Volatile
    var auxGlassShape: ((View) -> androidx.compose.ui.graphics.Shape)? = null

    /** 背景采样去重（重内容页优化，适配器按配置打开）：true 时 ClippedViewBackdrop 每
     *  tick 只把内容条带录制一次（RenderNode），三个玻璃节点回放同一录制——相册照片
     *  网格为 GL 渲染，采样即触发 GL 绘制，3 次/帧 → 1 次/帧。false 保持各自直绘
     *  （默认；商店路径不变）。 */
    @Volatile
    var dedupeBackdropSampling: Boolean = false

    /** 玻璃特效渲染最小间隔（纳秒；0 = 每帧渲染，默认）。重内容页 GPU 预算紧张时
     *  （如相册 GL 网格 + 三层 vibrancy/blur/lens 特效 @120Hz 会顶爆 8.3ms 帧预算，
     *  触发刷新率调度 120↔60 摆动），适配器按配置设为 ~16ms 把特效重绘降到 60fps：
     *  玻璃带折射/镜像/模糊 60fps（8dp 模糊下无感），帧循环其余工作仍每帧执行；
     *  液泡拖拽/按压/手势高光/重现动画读各自动画状态独立失效，不受影响仍满帧率。 */
    @Volatile
    var glassRenderIntervalNs: Long = 0L

    /** 采样冻结门控（声明式通道，默认 null = 其余应用零影响）：适配器注入，返回 true
     *  时 backdrop 不对 appRoot 做逐帧外部 draw——**进入冻结时一次性录制**条带、冻结
     *  期间纯回放，离开后自动恢复实时采样。外部高频 view.draw 会打乱部分自绘渲染树的
     *  合成——WebView（Chromium）被外部重绘会间歇输出空白帧（整页闪白，vivo 主题 VIP
     *  专区 H5 页实证）、GL 内容（相册）采样即触发 GL 绘制——适配器在含此类内容的页面
     *  声明冻结，用定格快照换取零干扰。与采样去重（[dedupeBackdropSampling]）解耦：
     *  非冻结页保持原直绘路径（去重回放跨 Surface 重建的显示列表会被 HWUI 丢弃，
     *  表现为"玻璃轮廓在、内容空"——回放型采样必须配合 [invalidateBackdrop]）。冻结
     *  录制失败/无录制产物时冻结帧不绘制（透明，极端降级）。每帧调用（draw 阶段），
     *  闭包应自缓存、避免全树遍历热路径。 */
    @Volatile
    var backdropFreezeGate: (() -> Boolean)? = null

    /** 强制重录制请求（[invalidateBackdrop] 置位，backdrop 消费后自动清除）：跨 Surface
     *  重建（后台切换）会丢弃 bandNode 已录制的显示列表，去重/冻结回放在重录制前为空。
     *  hook 层在 Activity onResume 时调用 [invalidateBackdrop]（主线程）。 */
    @Volatile
    var backdropForceRecord: Boolean = false

    /** 回前台唤醒 backdrop：下一帧强制重录制采样条带一次（去重帧 = 多录一次；冻结帧 =
     *  单次外部 draw 后继续纯回放）。 */
    fun invalidateBackdrop() {
        if (!backdropForceRecord) {
            backdropForceRecord = true
            logger?.invoke("backdrop invalidate (re-record after resume)")
        }
    }

    /** tick 事件驱动（适配器按内容重绘信号打开，如相册 GLRootView.onDraw hook）：
     *  true 时帧循环不再每帧无条件 tick——应用内容静止时零重绘（不烧 GPU、开发者
     *  选项的 Surface/视图/硬件层更新不再闪烁），内容重绘信号经 [onContentInvalidated]
     *  驱动；选中 tab 进入动画期间由帧循环自驱，保证动画全程可见。false = 每帧 tick
     *  （默认；商店路径不变）。 */
    @Volatile
    var tickEventDriven: Boolean = false

    /** 内容重绘信号（[tickEventDriven] 时由 GlassRoot 注入）：应用内容真正重绘时调用，
     *  刷新玻璃。经 glassRenderIntervalNs 门控限帧。主线程调用。 */
    var onContentInvalidated: (() -> Unit)? = null

    /** 拖动悬停预览激活中（引擎维护，适配器 hook 回调只读）。部分应用的条目选中翻转
     *  方法带**切页副作用**（微博 TabViewGroupV2.setChecked(true) 的选中变化监听器直接
     *  TabHost.setCurrentTabByTag）——预览翻转条目选中外观时会误触切页。适配器应 hook
     *  该副作用入口并在本标志为 true 时吞掉；正式切页仍由松手后 onTabClicked 的
     *  performClick 完成（那时标志已复位）。主线程读写。 */
    @Volatile
    var isTabPreviewActive: Boolean = false
        private set

    /** 请求一次玻璃重绘（事件驱动模式）：底栏自身内容变化（拖动预览/点击转发等）时
     *  由交互点调用。经 glassRenderIntervalNs 门控限帧。主线程调用。 */
    fun requestTick() {
        onContentInvalidated?.invoke()
    }

    /** 选中 tab 的进入动画是否进行中（事件驱动模式下帧循环据此自驱 tick）。 */
    val tabAnimating: Boolean get() = animLottie != null

    // —— 相册的"静止降帧"档位：每帧渲染模式下，内容活动（GLRootView.onDraw）或底栏
    //    交互（点击/预览/选中同步/样式变化）后的窗口期内满帧率；静止后降到
    //    [STATIC_TICK_INTERVAL_MS]（30fps，GlassRoot 帧循环门控）。与旧事件驱动的本质
    //    区别：静止档**仍以低频持续采样**，GL 合成空窗的空白帧最多停留 ~33ms 自愈，
    //    不会冻帧；图标/光效动画在交互窗口的满帧率内完整转播 ———
    @Volatile
    private var activeUntilMs = 0L

    /** 内容/交互活动窗口（满帧率档）：GLRootView.onDraw 信号与各交互点调用。主线程调用。 */
    fun armActivityWindow(durationMs: Long = 1_000L) {
        activeUntilMs = android.os.SystemClock.elapsedRealtime() + durationMs
    }

    /** 满帧率档是否生效（false = 静止，帧循环按 30fps 低频 tick）。 */
    val contentActive: Boolean
        get() = android.os.SystemClock.elapsedRealtime() < activeUntilMs

    // —— 静态空闲让步（v1.8，事件驱动会话）：帧循环常驻会使宿主主线程消息队列整场
    //    不空闲，宿主挂在 MessageQueue.IdleHandler 上的任务被无限期饿死（小红书切页
    //    oec.f.S0 滞留实证，引擎级已知限制）。内容完全静止时周期性暂停 tick 一个短窗，
    //    让队列出现真正的空闲间隙冲刷积压任务。静止期玻璃无内容变化，让步零视觉代价；
    //    任何内容重绘信号/动画/交互活动窗口都会立即终止让步。每帧 tick 模式（无内容
    //    信号，静止不可判定）不启用，其 IdleHandler 依赖由适配器侧同步重放解决。——
    @Volatile
    private var lastIdleYieldMs = 0L
    @Volatile
    private var idleYieldUntilMs = 0L

    /**
     * 静止空闲让步窗口判定（仅 [tickEventDriven] 会话）。帧循环每帧调用（主线程），
     * [nowMs] 为帧时钟毫秒（与调用方同源即可，内部不做跨时钟比较）。
     *
     * @param contentDirty 本帧内容重绘脏标记（GlassRoot 持有）
     * @return true = 本帧跳过 tick（让步窗口生效中）
     */
    fun shouldIdleYield(nowMs: Long, contentDirty: Boolean): Boolean {
        if (!tickEventDriven) return false
        if (contentDirty || tabAnimating || contentActive) {
            idleYieldUntilMs = 0L
            return false
        }
        if (nowMs < idleYieldUntilMs) return true
        if (nowMs - lastIdleYieldMs >= IDLE_YIELD_PERIOD_MS) {
            lastIdleYieldMs = nowMs
            idleYieldUntilMs = nowMs + IDLE_YIELD_WINDOW_MS
            logger?.invoke("idle yield window ${IDLE_YIELD_WINDOW_MS}ms (flush host IdleHandlers)")
            return true
        }
        return false
    }

    /** WHOLE_TAB 图标帧预览钩子：返回指定 tab 的"选中动画 Lottie drawable"（任意对象，
     *  引擎反射 setProgress 驱动并临时挂载到该 tab 图标 ImageView 使帧可被镜像），取不到
     *  返回 null。未注入（相册走 setChecked 原生路径，图标外观自带选中态）时图标帧预览
     *  整段跳过。由适配器按应用注入（vivo 官网：底栏内部 item 状态表反射）。 */
    @Volatile
    var previewIconLottie: ((index: Int) -> Any?)? = null

    /** 预览图标换图钩子（默认 null = 其余应用零影响）：WHOLE_TAB 条目的图标为"随选中
     *  态换图"的双 drawable 应用（如文件管理 discolor 图标：选中 = 品牌色 drawable、
     *  非选中 = 灰色 drawable，换图由应用自身选择路径 H()/r() 驱动、setChecked 不换图）
     *  在适配器提供此闭包，setTabChecked 每次翻转后调用——拖动预览期间悬停 tab 换上
     *  选中态 drawable、被拖离的原选中换回非选中态（与商店"气泡内图标 = 换图后选中态
     *  资源的染色副本"机制对齐，否则气泡里是非选中细轮廓的染色，观感浅于强调色）。 */
    @Volatile
    var previewIconSwapper: ((index: Int, checked: Boolean) -> Unit)? = null

    /** 帧驱动钩子：对 [previewIconLottie] 返回的 drawable 驱动到指定进度（0=首帧，
     *  1=末帧）。未注入时回退反射 `setProgress`（Lottie 方法未被混淆的应用，如商店）；
     *  R8 连库方法一起混淆的应用（vivo 官网，进度真名 "L"）必须注入。 */
    @Volatile
    var previewIconProgress: ((lottie: Any, progress: Float) -> Unit)? = null

    /** WHOLE_TAB 帧预览的"反选还原"钩子（可选，默认 null = 维持动画首帧回退语义）：
     *  液泡离开被拖的原选中 tab 时，以应用自身的反选换图语义回到未选中状态（vivo
     *  官网 VBottomNavigationView.w(item)：y(false)+停动画+换回未选中静态图——其
     *  选中动画首帧仍带选中观感，不能当未选中外观）。返回 true = 已应用；null 或
     *  返回 false → 引擎回退动画首帧（旧行为，未注入的应用逐位不变）。 */
    @Volatile
    var previewIconUnselected: ((index: Int) -> Boolean)? = null

    /** 诊断日志出口（hook 层注入 facade.log）。 */
    @Volatile
    var logger: ((String) -> Unit)? = null

    /** tab 转发后的取证回调（hook 层注入，如 padding dump）。在主线程调用。 */
    @Volatile
    var afterForward: (() -> Unit)? = null

    /** 应用当前选中 tab 下标；读不到返回 -1。主线程调用。
     *  WHOLE_TAB 拖动预览期间冻结为预览起点：预览翻转的是条目自身 isSelected
     *  （setChecked 内部调 setSelected），不冻结会让帧循环误判应用已切页，
     *  松手时 onTabSelected 守卫会跳过 performClick 转发。 */
    fun selectedTabIndex(): Int {
        wholeTabPreviewOrigin.takeIf { it >= 0 }?.let { return it }
        return tabViews.indexOfFirst { it.isSelected }
    }

    /** 玻璃 tab 被点击：转发到原 TabView（其 OnClickListener 会走应用自身的切页逻辑）。 */
    fun onTabClicked(index: Int) {
        armActivityWindow()
        val view = tabViews.getOrNull(index) ?: return
        // WHOLE_TAB 预览收口（微博类应用）：预览已把目标条目 checked 翻成 true，而部分
        // 应用的切页信号恰是 checked 的"变化"（微博 setChecked(false→true) 的监听器才调
        // TabHost.setCurrentTabByTag；已选中时再 setChecked(true) 直接 return）——不还原
        // 的话松手转发 performClick 是无效转换，页面永远不切。此处先把条目还原到预览前
        // 状态（还原期间的切页副作用仍被吞咽：还原 origin 的 setChecked(true) 不切页，
        // TabHost 本就停在 origin），再放行 performClick 产生真实变化 → 应用正常切页。
        // 相册等无副作用应用：还原与后续应用自身的切换同栈完成、中间无绘制，无可见抖动。
        val previewing = wholeTabPreviewOrigin >= 0
        if (previewing) {
            val origin = wholeTabPreviewOrigin
            if (index != origin) {
                isTabPreviewActive = true
                setTabChecked(index, false)
                setTabChecked(origin, true)
            }
        }
        isTabPreviewActive = false // 正式点击切换：选中翻转的切页副作用必须放行
        val start = android.os.SystemClock.elapsedRealtime()
        // 描述符接管点击派发（如小红书：生效监听在祖先容器，条目 performClick 无效，
        // 走适配器的穿透式点击）；默认对条目视图 performClick 走应用自身监听器。
        extractor.descriptor.tabClickHandler?.invoke(index, tabViews, tabContainer)
            ?: view.performClick()
        val cost = android.os.SystemClock.elapsedRealtime() - start
        if (cost > 100) {
            logger?.invoke("performClick(index=$index) blocked main thread for ${cost}ms")
        }
        if (wholeTabPreviewOrigin >= 0) {
            // 拖动预览提交：清除冻结，交还应用真实选中读数（视觉由应用自身 setChecked 流程接管）。
            // 点击的 tab 图标由应用切换流程（I() 播放/换静态图）接管，不恢复保存的原 drawable；
            // 其余 tab 已在离开时逐一恢复，此处仅清缓存。
            wholeTabPreviewOrigin = -1
            wholeTabPreviewTarget = -1
            savedPreviewDrawables.clear()
        }
        afterForward?.invoke()
    }

    // —— 选中 tab 的 Lottie 进入动画由玻璃侧逐帧直控进度（不依赖应用动画器：
    //    playAnimation 在原视图隐藏等场景下不可靠）——
    private var animLottie: View? = null
    private var animStartMs = 0L
    private var animDurationMs = 400f
    /** 当前处于选中视觉末帧的 lottie（正式选中 tab）。 */
    private var selectedLottie: View? = null
    /** 拖动预览中临时置为末帧的 lottie（非正式选中）。 */
    private var previewLottie: View? = null
    /** 上一次进入动画的 tab 下标（选中切换时对其做双 Lottie 全量复位）。 */
    private var lastAnimatedIndex = -1
    /** 复位时观测到非零帧/动画器存活的取证（只记首条，防刷屏）。 */
    private var resetObservedDirty = false
    private val lottieCache = HashMap<Int, View?>()

    private fun lottieAt(index: Int): View? = lottieCache.getOrPut(index) {
        tabViews.getOrNull(index)?.let { extractor.findLottieView(it) }
    }

    private fun tabIndexOfLottie(lottie: View): Int =
        (0 until tabViews.size).firstOrNull { lottieAt(it) === lottie } ?: -1

    /**
     * 复位一个 Lottie 到未选中基帧：`cancelAnimation()` 先于 `setProgress(0)`——
     * 与应用自身复位语义逐字一致（商店 TabView.v(true)）。cancel 在动画器仍处
     * started 态时触发 onAnimationCancel，应用的图标选中色回调借此翻回未选中色
     * （TabView 的 t(D && isSelected())：onAnimationStart→品牌色、onAnimationCancel→
     * 按 isSelected 回归）；引擎的 setProgress 直写不产生任何动画器事件，选中色
     * 回调会滞留品牌色帧，呈"切走后图标未恢复未选中"（刷新完成动画播放过再切走
     * 时的低概率路径，真机实测）。失败静默（应用换非 Lottie 实现时由 setLottieProgress
     * 的既有软失败兜底）。
     */
    private fun resetLottieToBase(lottie: View) {
        runCatching {
            lottie.javaClass.getMethod("cancelAnimation").invoke(lottie)
        }
        setLottieProgress(lottie, 0f)
    }

    /**
     * 旧选中 tab 的双 Lottie（主 + 刷新）全量复位：商店首页是双 Lottie，可见性由
     * 应用刷新态状态机翻转——单跟踪 selectedLottie 只覆盖装配/选中时刻的可见者，
     * 刷新态中切走的路径会漏掉主 Lottie（其上还挂着引擎上次驱动的选中末帧或应用
     * 刷新完成动画的残留进度）。全量复位对齐应用自身不变量：未选中 = 两枚 Lottie
     * 都在基帧（v(true) 同款）。应用早一步复位时此处幂等无害。
     */
    private fun resetPreviousTabLotties(previousIndex: Int) {
        if (previousIndex !in tabViews.indices) return
        for (lottie in extractor.findLottieViews(tabViews[previousIndex])) {
            if (!resetObservedDirty) {
                val dirty = runCatching {
                    lottie.javaClass.getMethod("isAnimating").invoke(lottie) as Boolean
                }.getOrDefault(false) ||
                    runCatching {
                        lottie.javaClass.getMethod("getProgress").invoke(lottie) as Float
                    }.getOrDefault(0f) != 0f
                if (dirty) {
                    resetObservedDirty = true
                    logger?.invoke("tab reset: prev tab #$previousIndex lottie dirty (progress/animating) -> cancel+0")
                }
            }
            resetLottieToBase(lottie)
        }
    }

    /**
     * 开始播放指定 tab 的 Lottie 进入动画：记录起点与时长，由 [driveTabAnimation]
     * 逐帧推进。若目标已在拖动预览末帧（拖动后松手），跳过动画直接定帧；
     * 上一个选中 tab 复位到基础帧。主线程调用。
     */
    fun beginTabAnimation(index: Int) {
        // WHOLE_TAB 系（相册/微博）：选中变化（玻璃侧点击与应用侧同步都经此入口）伴随
        // 图标动画，开满帧率窗口让镜像逐帧跟随
        if (extractor.descriptor.iconMode != IconMode.LOTTIE_RESOLVE) armActivityWindow()
        // 旧选中 tab 先全量复位（双 Lottie、cancel+0）：拖动预览提交后这里同样走到，
        // 预览末帧残留一并清掉
        val previous = lastAnimatedIndex
        lastAnimatedIndex = index
        if (previous != index) resetPreviousTabLotties(previous)
        val lottie = lottieAt(index)
        if (lottie == null) {
            animLottie = null
            selectedLottie = null
            previewLottie = null
            return
        }
        val fromPreview = lottie === previewLottie
        previewLottie?.takeIf { it !== lottie }?.let { resetLottieToBase(it) }
        previewLottie = null
        selectedLottie?.takeIf { it !== lottie }?.let { resetLottieToBase(it) }
        selectedLottie = lottie
        if (fromPreview) {
            animLottie = null
            return
        }
        animLottie = lottie
        animStartMs = android.os.SystemClock.elapsedRealtime()
        animDurationMs = queryLottieDuration(lottie)
        logger?.invoke("tab animation: index=$index duration=${animDurationMs.toInt()}ms")
    }

    /** 每帧调用（主线程）：按墙钟推进进入动画进度到 [0,1]，结束后停在末帧（选中态）。
     *  原生动画器在跑时让位（容器可见但隐形后可见性恢复会自动播放原生动画）。 */
    fun driveTabAnimation() {
        val lottie = animLottie ?: return
        if (isLottieAnimating(lottie)) return
        val progress = ((android.os.SystemClock.elapsedRealtime() - animStartMs) / animDurationMs)
            .coerceIn(0f, 1f)
        setLottieProgress(lottie, progress)
        if (progress >= 1f) animLottie = null
    }

    /**
     * 拖动液泡的悬停预览：原选中 tab 立即回归未选中，悬停 tab 预览选中；拖回原 tab 时
     * 完全还原。同一目标去重。主线程调用。
     *
     * LOTTIE_RESOLVE（商店）：悬停 tab Lottie 直控选中末帧 + 标签 TextView 翻 isSelected
     * （颜色选择器随视图状态切换；子 TextView 翻转为 best-effort，duplicateParentState
     * 的视图可能不生效）。WHOLE_TAB 系（相册/微博）：翻转条目 setChecked（图标选中外观
     * 原生驱动，镜像实时跟随；selectedTabIndex 同步冻结防读数污染）。
     */
    fun previewTabChange(target: Int) {
        if (extractor.descriptor.iconMode != IconMode.LOTTIE_RESOLVE) {
            isTabPreviewActive = true // 预览翻转选中外观期间，适配器吞掉应用的切页副作用
            armActivityWindow()
            previewWholeTab(target)
            return
        }
        val targetLottie = lottieAt(target) ?: return
        if (targetLottie === previewLottie) return
        if (targetLottie === selectedLottie) {
            // 拖回原 tab：复位预览目标并还原选中态
            previewLottie?.let {
                resetLottieToBase(it)
                setTabSelected(tabIndexOfLottie(it), false)
            }
            previewLottie = null
            animLottie = null
            setLottieProgress(targetLottie, 1f)
            setTabSelected(target, true)
            return
        }
        // 悬停新 tab：挂起进行中的进入动画（避免双写进度），复位旧预览与原选中
        animLottie = null
        previewLottie?.let {
            resetLottieToBase(it)
            setTabSelected(tabIndexOfLottie(it), false)
        }
        selectedLottie?.let {
            resetLottieToBase(it)
            setTabSelected(tabIndexOfLottie(it), false)
        }
        setLottieProgress(targetLottie, 1f)
        setTabSelected(target, true)
        previewLottie = targetLottie
    }

    /** 翻转 tab 标签的选中态（TextView 颜色选择器随视图状态切换；best-effort）。 */
    private fun setTabSelected(index: Int, selected: Boolean) {
        val tv = tabViews.getOrNull(index)?.let { extractor.findTextView(it) } ?: return
        runCatching { tv.isSelected = selected }
    }

    // —— WHOLE_TAB（条目级选中态，如相册 VNavMenuItem）拖动预览：直接翻转条目 checked——
    /** 预览起点（原生选中读数冻结值），<0 = 预览未激活。 */
    private var wholeTabPreviewOrigin = -1
    /** 当前悬停预览目标，<0 = 无。 */
    private var wholeTabPreviewTarget = -1
    private val wholeTabSetChecked: java.lang.reflect.Method? = tabViews.firstOrNull()?.javaClass?.let { cls ->
        // 方法名按描述符解析（默认 setChecked）：部分库被方法级混淆（如 vivo 官网 originui
        // 13.3.0.1 的选中外观翻转为 y(boolean)，无 setChecked），适配器按取证声明。
        // 解析失败保持 null（预览静默失效、不崩溃），不回退瞎猜方法名。
        runCatching {
            cls.getMethod(
                extractor.descriptor.itemCheckedSetterMethodName ?: "setChecked",
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull()
    }

    /** WHOLE_TAB 悬停预览：悬停 tab = 动画末帧（选中外观），被拖离的原选中 tab = 动画
     *  首帧（未选中外观），其他经过后离开的 tab = 恢复原静态图标（非首帧）；拖回原 tab
     *  完全还原并结束预览。相册经 setChecked 原生驱动同语义（见 previewIconLottie）。 */
    private fun previewWholeTab(target: Int) {
        if (target == wholeTabPreviewTarget) return
        // 纯反射入口守卫：条目上无 void(boolean) 翻转方法**且**描述符未声明 applier 时
        // 预览无从翻转，放弃（静默降级，不崩溃）。有 applier 的适配器（高德/原子笔记）
        // 经 setTabChecked 走直控通道——守卫必须把 applier 算进去，否则纯 applier 适配器
        // （条目是普通 ViewGroup、无 setChecked 可解析）预览静默失效。
        if (wholeTabSetChecked == null && extractor.descriptor.itemCheckedApplier == null) return
        val origin = wholeTabPreviewOrigin.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                .also { wholeTabPreviewOrigin = it }
        if (target == origin) {
            // 悬停回原 tab：预览保持激活，仅清悬停目标——边界抖动不反复挂载/恢复
            // （那会表现为"动画被反复触发"）；原 tab 恢复保存的原选中静态图（拖离时
            // 被反选换图/挂首帧过，悬停本就该呈选中落定外观，§0.5 A.1）；还原统一在
            // 拖动结束的 endTabPreview() 做（LiquidBottomTabs onDragStopped 回原 tab）
            if (wholeTabPreviewTarget >= 0) {
                setTabChecked(wholeTabPreviewTarget, false)
                restorePreviewFrame(wholeTabPreviewTarget)
                wholeTabPreviewTarget = -1
            }
            restorePreviewFrame(origin)
            setTabChecked(origin, true)
            return
        }
        val previous = wholeTabPreviewTarget
        if (previous >= 0) {
            // 离开的条目恢复原静态图标（非动画首帧）
            setTabChecked(previous, false)
            restorePreviewFrame(previous)
        } else {
            // 被拖离的原选中条目 = 回到未选中状态：优先适配器注入的应用自身反选换图
            // 语义（换回未选中静态图——动画首帧可能仍带选中观感，不能当未选中外观）；
            // 未注入/失败回退动画首帧（旧行为）。当前 drawable 先存，拖动结束统一还原
            setTabChecked(origin, false)
            if (previewIconUnselected != null) savePreviewDrawable(origin)
            if (previewIconUnselected?.invoke(origin) != true) {
                showPreviewFrame(origin, 0f)
            }
        }
        setTabChecked(target, true)
        showPreviewFrame(target, 1f)
        wholeTabPreviewTarget = target
    }

    /** 帧预览期间被临时换上 Lottie 的 tab 下标 → 原图标 drawable（离开即恢复）。 */
    private val savedPreviewDrawables = android.util.SparseArray<android.graphics.drawable.Drawable>()

    private fun previewIconView(index: Int): android.widget.ImageView? {
        val tabView = tabViews.getOrNull(index) ?: return null
        val iconIdName = extractor.descriptor.elementIds["icon"] ?: return null
        val iconId = tabView.context.resources.getIdentifier(iconIdName, "id", tabView.context.packageName)
        if (iconId == 0) return null
        return tabView.findViewById<View>(iconId) as? android.widget.ImageView
    }

    /** 保存该 tab 图标当前 drawable（[previewIconUnselected] 应用反选换图前调用，
     *  供拖动结束 [endTabPreview] 统一还原；无图标视图则无操作）。 */
    private fun savePreviewDrawable(index: Int) {
        previewIconView(index)?.let { savedPreviewDrawables.put(index, it.drawable) }
    }

    /** WHOLE_TAB 图标帧预览：把适配器钩子提供的 Lottie drawable 临时挂到该 tab 图标
     *  ImageView 上并驱动到指定帧（未选中条目视图上挂的是静态图，不挂载帧变化无法被
     *  镜像）。挂载前保存原 drawable 供离开时恢复。钩子未注入或取不到 drawable（应用
     *  更新致反射失效）时静默跳过。 */
    private fun showPreviewFrame(index: Int, progress: Float) {
        val lottie = previewIconLottie?.invoke(index) ?: return
        val iconView = previewIconView(index) ?: return
        if (iconView.drawable !== lottie) {
            savedPreviewDrawables.put(index, iconView.drawable)
            iconView.setImageDrawable(lottie as android.graphics.drawable.Drawable)
        }
        val drive = previewIconProgress
        if (drive != null) drive(lottie, progress) else setLottieProgress(lottie, progress)
    }

    /** 恢复该 tab 的原静态图标（预览离开/拖回时调用；无保存记录则跳过）。 */
    private fun restorePreviewFrame(index: Int) {
        val saved = savedPreviewDrawables.get(index, null) ?: return
        previewIconView(index)?.let { iconView ->
            if (iconView.drawable !== saved) iconView.setImageDrawable(saved)
        }
        savedPreviewDrawables.remove(index)
    }

    /** 拖动结束（LiquidBottomTabs onDragStopped 回到原选中 tab 时调用）：全部条目恢复
     *  原静态图标、原 tab 回选中态，预览状态清零。幂等。 */
    fun endTabPreview() {
        isTabPreviewActive = false // 预览结束：还原的 setChecked 走应用原生语义（已在原页，无切页）
        val origin = wholeTabPreviewOrigin.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        for (i in 0 until savedPreviewDrawables.size()) {
            val index = savedPreviewDrawables.keyAt(i)
            previewIconView(index)?.let { iconView ->
                val saved = savedPreviewDrawables.valueAt(i)
                if (iconView.drawable !== saved) iconView.setImageDrawable(saved)
            }
        }
        savedPreviewDrawables.clear()
        setTabChecked(origin, true)
        wholeTabPreviewOrigin = -1
        wholeTabPreviewTarget = -1
    }

    /** 翻转 WHOLE_TAB 条目的 checked 态：VNavMenuItem.setChecked 驱动图标选中外观与标签
     *  显隐（内部调 setSelected），与应用自身切页同路径，无监听器副作用。best-effort。
     *  描述符声明了 itemCheckedApplier 时（条目选中外观由"数据模型 + setData 重渲染"
     *  驱动、视图上无 void(boolean) 翻转方法，如高德地图）走直控通道。翻转后调用
     *  [previewIconSwapper]（图标随选中态换图的应用，如文件管理 discolor 双 drawable）。 */
    private fun setTabChecked(index: Int, checked: Boolean) {
        val view = tabViews.getOrNull(index) ?: return
        extractor.descriptor.itemCheckedApplier?.let { applier ->
            runCatching { applier(view, checked) }
                .onFailure { logger?.invoke("itemCheckedApplier($index,$checked) failed: $it") }
            runCatching { previewIconSwapper?.invoke(index, checked) }
            return
        }
        val method = wholeTabSetChecked ?: return
        runCatching { method.invoke(view, checked) }
            .onFailure { logger?.invoke("setChecked($index,$checked) failed: $it") }
        runCatching { previewIconSwapper?.invoke(index, checked) }
    }

    private fun setLottieProgress(lottie: Any, progress: Float) {
        runCatching {
            lottie.javaClass.getMethod("setProgress", Float::class.javaPrimitiveType)
                .invoke(lottie, progress)
        }.onFailure { logger?.invoke("setLottieProgress($progress) failed: $it") }
    }

    private fun isLottieAnimating(lottie: View): Boolean = runCatching {
        lottie.javaClass.getMethod("isAnimating").invoke(lottie) as Boolean
    }.getOrDefault(false)

    private fun queryLottieDuration(lottie: View): Float = runCatching {
        (lottie.javaClass.getMethod("getDuration").invoke(lottie) as Int).toFloat()
    }.getOrNull()?.takeIf { it > 0f } ?: 400f

    /** 装配期缺色的延迟补取（应用可能晚于装配才应用主题色，如清除后台后的暖启动加载态）。
     *  当前有缺色且新取到不同值时返回新 (内容色, 强调色)，否则 null。主线程调用。 */
    fun reextractColorsIfMissing(
        contentColor: androidx.compose.ui.graphics.Color?,
        accentColor: androidx.compose.ui.graphics.Color?,
    ): Pair<androidx.compose.ui.graphics.Color?, androidx.compose.ui.graphics.Color?>? {
        if (contentColor != null && accentColor != null) return null
        val extracted = extractor.extractLabelColors(tabViews)
        if (extracted.first == null && extracted.second == null) return null
        if (extracted.first == contentColor && extracted.second == accentColor) return null
        return extracted.also {
            logger?.invoke("glass colors re-extracted: content=${it.first}, accent=${it.second}")
        }
    }

    /** 原视图树是否已被应用重建（tab 视图不再挂在本会话的 Compose 视图下）。 */
    fun isStale(): Boolean {
        val root = composeView.rootView
        return tabViews.any { it.rootView !== root }
    }

    /**
     * 原生底栏"可见但隐形"：VISIBLE + alpha=0 + 递归取消 clickable。应用侧的可见性
     * 守卫（isShown 类）、滚动监听、刷新状态机、Lottie 可见性恢复全部按原生逻辑工作
     * （GONE 会让点击转发的回顶+刷新分支失效——切页路径不检查可见性所以此前未察觉）；
     * 真实触摸穿透到信息流；performClick 转发直接调监听器、不受 clickable 影响。
     * 同时把底栏对无障碍整树隐身（NO_HIDE_DESCENDANTS）：隐形视图本不该被 a11y 遍历，
     * 且商店 TabView 的无障碍节点会解析自身缺失的 string 资源——系统 a11y prefetch
     * 一旦走到它就 Resources$NotFoundException 主线程崩溃（商店启动闪退的根因链）。
     */
    fun suppressNatively() {
        tabContainer.visibility = View.VISIBLE
        tabContainer.alpha = 0f
        tabContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setClickableTree(tabContainer, false)
    }

    /** 防应用刷新重设 clickable：帧循环低频重申。 */
    fun reassertClickSuppression() {
        if (tabContainer.visibility == View.VISIBLE) setClickableTree(tabContainer, false)
    }

    private fun setClickableTree(root: View, clickable: Boolean) {
        root.isClickable = clickable
        (root as? ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                group.getChildAt(i)?.let { setClickableTree(it, clickable) }
            }
        }
    }

    /** 还原：移除 ComposeView 覆盖层、恢复底栏可见性/透明度/可点击（内容视图从未被移动）。主线程调用。 */
    fun restore() {
        try {
            onAuxGlassRestore?.invoke()
        } catch (_: Throwable) {
        }
        try {
            (composeView as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()
        } catch (_: Throwable) {
        }
        try {
            composeView.parent?.let { (it as ViewGroup).removeView(composeView) }
            tabContainer.alpha = 1f
            setClickableTree(tabContainer, true)
            tabContainer.visibility = originalContainerVisibility
            tabContainer.importantForAccessibility = originalContainerImportance
            // decorView 的 ViewTree owner 换回装配前的原值（仅当仍是同一棵装饰视图）
            val decor = originalDecorRef?.get()
            val owners = originalDecorOwners
            if (decor != null && owners != null) {
                decor.setViewTreeLifecycleOwner(owners.first as? androidx.lifecycle.LifecycleOwner)
                decor.setViewTreeViewModelStoreOwner(owners.second as? androidx.lifecycle.ViewModelStoreOwner)
                decor.setViewTreeSavedStateRegistryOwner(owners.third as? androidx.savedstate.SavedStateRegistryOwner)
            }
        } catch (_: Throwable) {
        }
    }
}

/** 静态空闲让步的触发周期（静止持续多久让步一次）与窗口时长（GlassSession.shouldIdleYield）。 */
private const val IDLE_YIELD_PERIOD_MS = 10_000L
private const val IDLE_YIELD_WINDOW_MS = 120L
