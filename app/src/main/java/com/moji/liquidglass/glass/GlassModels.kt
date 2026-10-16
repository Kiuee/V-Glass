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
    val minHorizontalMarginDp: Float = 16f,
    /** 小白条上方的额外间隙（dp），叠加在导航栏 inset 之上。 */
    val bottomGapDp: Float = 0f,
    /** 栏高（dp），内容行 = 栏高 - 8dp。 */
    val barHeightDp: Float = 64f,
    /** 折射模糊半径（dp）。 */
    val blurRadiusDp: Float = 8f,
    /** 是否显示 tab 文字标签。 */
    val showLabels: Boolean = true,
) {
    companion object {
        fun fromConfig(config: ModuleConfig): GlassStyle = GlassStyle(
            minHorizontalMarginDp = config.barMarginDp,
            bottomGapDp = config.barBottomGapDp,
            barHeightDp = config.barHeightDp,
            blurRadiusDp = config.barBlurDp,
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
        val start = android.os.SystemClock.elapsedRealtime()
        view.performClick()
        val cost = android.os.SystemClock.elapsedRealtime() - start
        if (cost > 100) {
            logger?.invoke("performClick(index=$index) blocked main thread for ${cost}ms")
        }
        if (wholeTabPreviewOrigin >= 0) {
            // 拖动预览提交：清除冻结，交还应用真实选中读数（视觉由应用自身 setChecked 流程接管）
            wholeTabPreviewOrigin = -1
            wholeTabPreviewTarget = -1
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
    private val lottieCache = HashMap<Int, View?>()

    private fun lottieAt(index: Int): View? = lottieCache.getOrPut(index) {
        tabViews.getOrNull(index)?.let { extractor.findLottieView(it) }
    }

    private fun tabIndexOfLottie(lottie: View): Int =
        (0 until tabViews.size).firstOrNull { lottieAt(it) === lottie } ?: -1

    /**
     * 开始播放指定 tab 的 Lottie 进入动画：记录起点与时长，由 [driveTabAnimation]
     * 逐帧推进。若目标已在拖动预览末帧（拖动后松手），跳过动画直接定帧；
     * 上一个选中 tab 复位到基础帧。主线程调用。
     */
    fun beginTabAnimation(index: Int) {
        // WHOLE_TAB（相册）：选中变化（玻璃侧点击与应用侧同步都经此入口）伴随图标动画，
        // 开满帧率窗口让镜像逐帧跟随
        if (extractor.descriptor.iconMode == IconMode.WHOLE_TAB) armActivityWindow()
        val lottie = lottieAt(index)
        if (lottie == null) {
            animLottie = null
            selectedLottie = null
            previewLottie = null
            return
        }
        val fromPreview = lottie === previewLottie
        previewLottie?.takeIf { it !== lottie }?.let { setLottieProgress(it, 0f) }
        previewLottie = null
        selectedLottie?.takeIf { it !== lottie }?.let { setLottieProgress(it, 0f) }
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
     * 的视图可能不生效）。WHOLE_TAB（相册）：翻转条目 setChecked（图标选中外观原生驱动，
     * 镜像实时跟随；selectedTabIndex 同步冻结防读数污染）。
     */
    fun previewTabChange(target: Int) {
        if (extractor.descriptor.iconMode == IconMode.WHOLE_TAB) {
            armActivityWindow()
            previewWholeTab(target)
            return
        }
        val targetLottie = lottieAt(target) ?: return
        if (targetLottie === previewLottie) return
        if (targetLottie === selectedLottie) {
            // 拖回原 tab：复位预览目标并还原选中态
            previewLottie?.let {
                setLottieProgress(it, 0f)
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
            setLottieProgress(it, 0f)
            setTabSelected(tabIndexOfLottie(it), false)
        }
        selectedLottie?.let {
            setLottieProgress(it, 0f)
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
        runCatching { cls.getMethod("setChecked", Boolean::class.javaPrimitiveType) }.getOrNull()
    }

    /** WHOLE_TAB 悬停预览：上一个预览目标（或原选中）回归未选中，悬停目标预览选中；
     *  拖回原 tab 完全还原并结束预览。 */
    private fun previewWholeTab(target: Int) {
        if (target == wholeTabPreviewTarget) return
        if (wholeTabSetChecked == null) return
        val origin = wholeTabPreviewOrigin.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                .also { wholeTabPreviewOrigin = it }
        if (target == origin) {
            if (wholeTabPreviewTarget >= 0) setTabChecked(wholeTabPreviewTarget, false)
            setTabChecked(origin, true)
            wholeTabPreviewTarget = -1
            wholeTabPreviewOrigin = -1
            return
        }
        val previous = wholeTabPreviewTarget
        if (previous >= 0) {
            setTabChecked(previous, false)
        } else {
            setTabChecked(origin, false)
        }
        setTabChecked(target, true)
        wholeTabPreviewTarget = target
    }

    /** 翻转 WHOLE_TAB 条目的 checked 态：VNavMenuItem.setChecked 驱动图标选中外观与标签
     *  显隐（内部调 setSelected），与应用自身切页同路径，无监听器副作用。best-effort。 */
    private fun setTabChecked(index: Int, checked: Boolean) {
        val view = tabViews.getOrNull(index) ?: return
        val method = wholeTabSetChecked ?: return
        runCatching { method.invoke(view, checked) }
            .onFailure { logger?.invoke("setChecked($index,$checked) failed: $it") }
    }

    private fun setLottieProgress(lottie: View, progress: Float) {
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
