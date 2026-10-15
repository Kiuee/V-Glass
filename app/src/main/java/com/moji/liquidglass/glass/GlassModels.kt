package com.moji.liquidglass.glass

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
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

    /** 玻璃层检测到原视图树被应用重建（失联）时回调，hook 层据此重装。 */
    @Volatile
    var onStale: (() -> Unit)? = null

    /** 诊断日志出口（hook 层注入 facade.log）。 */
    @Volatile
    var logger: ((String) -> Unit)? = null

    /** tab 转发后的取证回调（hook 层注入，如 padding dump）。在主线程调用。 */
    @Volatile
    var afterForward: (() -> Unit)? = null

    /** 应用当前选中 tab 下标；读不到返回 -1。主线程调用。 */
    fun selectedTabIndex(): Int = tabViews.indexOfFirst { it.isSelected }

    /** 玻璃 tab 被点击：转发到原 TabView（其 OnClickListener 会走应用自身的切页逻辑）。 */
    fun onTabClicked(index: Int) {
        val view = tabViews.getOrNull(index) ?: return
        val start = android.os.SystemClock.elapsedRealtime()
        view.performClick()
        val cost = android.os.SystemClock.elapsedRealtime() - start
        if (cost > 100) {
            logger?.invoke("performClick(index=$index) blocked main thread for ${cost}ms")
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
     * 拖动液泡的悬停预览：原选中 tab 立即回归未选中（lottie 基础帧 + 标签取消选中态，
     * 子 TextView 翻转为 best-effort，duplicateParentState 的视图可能不生效），悬停 tab
     * 预览选中末帧；拖回原 tab 时完全还原。同一目标去重。主线程调用。
     */
    fun previewTabChange(target: Int) {
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
     */
    fun suppressNatively() {
        tabContainer.visibility = View.VISIBLE
        tabContainer.alpha = 0f
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
        } catch (_: Throwable) {
        }
    }
}
