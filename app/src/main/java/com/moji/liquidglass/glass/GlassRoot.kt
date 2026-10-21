package com.moji.liquidglass.glass

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.moji.liquidglass.glass.components.LocalLiquidBottomTabBubbleCovered
import com.moji.liquidglass.glass.components.LocalTabPreviewIndex
import com.moji.liquidglass.glass.components.LiquidBottomTab
import com.moji.liquidglass.glass.components.LiquidBottomTabs
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * 玻璃底栏（覆盖层模式的条带布局）。
 *
 * 应用内容视图在 Compose 之外原位渲染（触摸路径原生），玻璃通过 ClippedViewBackdrop
 * 跨分支绘制内容视图的胶囊条带（view.draw 不要求同树），tick 驱动逐帧采样，滚动折射实时。
 *
 * 布局：ComposeView（WRAP_CONTENT、Gravity.BOTTOM）→ 根 Box 顶部 48dp 为辉光/液泡
 * 放大余量；玻璃栏底边内边距 = 应用真实导航栏 inset（navInsetProvider 三级回退）——
 * 玻璃栏抬升到小白条上方，下方条带透出全出血的信息流内容。
 * 二楼下拉位移经 GlassBarState 驱动 graphicsLayer 跟随，与原软件行为一致。
 *
 * 多目标：渲染由 GlassBarSpec（适配器提取）驱动——
 * - LOTTIE_RESOLVE（商店）：每帧解析可见 Lottie 图标 + Compose 标签 + 角标覆盖层，
 *   液泡折射副本整层强调色 tint（选中 tab 呈强调色）；
 * - WHOLE_TAB（相册）：图标元素镜像（原生配色 + ViewOverlay 角标）+ 统一标签绘制，
 *   折射副本同样整层强调色 tint（与商店观感一致，强调色取自应用标签选择器）；
 *   mirrorVisibleGate 跟随原生页面隐藏整条玻璃栏。
 */
@Composable
fun GlassRoot(
    appRoot: View,
    spec: GlassBarSpec,
    session: GlassSession
) {
    val density = LocalDensity.current
    val tick = remember { mutableIntStateOf(0) }
    // tick 门控时间戳与内容重绘脏标记（帧循环与内容重绘信号共用，主线程）
    val tickGate = remember { LongArray(1) }
    val contentDirty = remember { booleanArrayOf(false) }
    val backdrop = remember(appRoot, session) {
        ClippedViewBackdrop(
            view = appRoot,
            tick = { tick.intValue },
            viewPosition = {
                val out = IntArray(2)
                appRoot.getLocationOnScreen(out)
                Offset(out[0].toFloat(), out[1].toFloat())
            },
            dedupe = { session.dedupeBackdropSampling },
            log = { session.logger?.invoke(it) },
        )
    }

    // 底部 inset：优先应用侧真实值（navInsetProvider），不可用时回退 Compose insets
    var bottomInset by remember { mutableIntStateOf(-1) }

    // 样式（模块设置页可调，hook 层同步到 GlassInstaller，帧循环检测变化驱动重组）
    var style by remember { mutableStateOf(GlassInstaller.glassStyle) }

    var selectedIndex by remember { mutableIntStateOf(spec.initialIndex) }
    // 底栏显隐跟随原生页面：false = 应用声明当前页不显示底栏（如大图详情页二级界面）
    var barWanted by remember { mutableStateOf(true) }
    // 应用配色（可延迟补取热替换）：装配可能早于应用应用主题色（清除后台后的暖启动
    // 等加载态，取到缺省选择器），帧循环低频补取直到拿到真实配色
    var specColors by remember(spec) { mutableStateOf(spec.contentColor to spec.accentColor) }

    // 底栏重现（多选退出/大图返回/首次装配）的放大弹性动画：复刻点击底栏的按压放大
    // 回弹——上行高阈值提前折返（快胀到 ~90% 峰值立刻回弹，借上行残余速度顺滑回落，
    // 总时长 ~350ms 无停留平台），仅播放动画，无操作
    val appearProgress = remember { Animatable(0f) }
    LaunchedEffect(barWanted) {
        if (barWanted) {
            appearProgress.snapTo(0f)
            appearProgress.animateTo(1f, spring(1f, 1800f, 0.1f))
            appearProgress.animateTo(0f, spring(1f, 1200f, 0.001f))
        } else {
            appearProgress.snapTo(0f)
        }
    }

    // 标签文本跟随：原 TextView 文本变化（首页 ⇄ 刷新）实时同步到玻璃标签
    val labelTexts = remember(spec) {
        mutableStateMapOf<Int, String>().apply {
            spec.tabs.forEachIndexed { i, tab -> put(i, tab.title) }
        }
    }
    DisposableEffect(spec) {
        val watchers = spec.tabs.mapIndexed { index, _ ->
            val tv = session.tabViews.getOrNull(index)?.let { session.extractor.findTextView(it) }
            if (tv == null) {
                null
            } else {
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        s?.toString()?.takeIf { it.isNotBlank() }?.let { labelTexts[index] = it }
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }.also { tv.addTextChangedListener(it) }
            }
        }
        onDispose {
            watchers.forEachIndexed { index, watcher ->
                watcher ?: return@forEachIndexed
                session.tabViews.getOrNull(index)?.let {
                    session.extractor.findTextView(it)?.removeTextChangedListener(watcher)
                }
            }
        }
    }

    // 应用 → 玻璃：帧循环读取原 TabView 的 isSelected（应用自身切页时保持同步）。
    LaunchedEffect(session) {
        var lastFrame = withFrameNanos { it }
        var frames = 0
        var sumNs = 0L
        var maxNs = 0L
        var ticks = 0L
        while (true) {
            val now = withFrameNanos { it }
            val gapNs = now - lastFrame
            lastFrame = now
            frames++
            sumNs += gapNs
            if (gapNs > maxNs) maxNs = gapNs
            if (frames >= 120) {
                session.logger?.invoke(
                    "frame metrics: 120 frames avg=${sumNs / 120 / 1_000_000}ms max=${maxNs / 1_000_000}ms " +
                        "ticks=$ticks"
                )
                frames = 0
                sumNs = 0
                maxNs = 0
                ticks = 0
                // 低频重申原生底栏的 clickable 压制（应用刷新可能重设监听/可点击）
                session.reassertClickSuppression()
                // 装配期缺色的延迟补取（应用晚于装配才应用主题色的加载态自愈）
                session.reextractColorsIfMissing(specColors.first, specColors.second)?.let {
                    specColors = it
                }
            }
            if (gapNs > 500_000_000) {
                session.logger?.invoke("frame stall ${gapNs / 1_000_000}ms (main thread or render pipeline blocked)")
            }
            // tick 驱动：默认每帧（商店等）；事件驱动模式（商店，内容信号 hook）
            // 下由内容重绘脏标记驱动——脏标记在 vsync 的动画回调阶段转换为 tick（先于
            // traversal），玻璃与应用内容同帧绘制；静止（无信号）零重绘，Surface/视图/
            // 硬件层更新不再闪烁、空闲不烧 GPU。仅选中 tab 进入动画期间自驱。
            // 相册 tickEventDriven=false：内容/交互活动窗口内每帧（满帧率转播图标/光效
            // 动画、跟随滚动），静止后降 30fps 低频 tick——低频档仍持续采样，GL 合成空窗
            // 的空白帧最多停留 ~33ms 自愈（不会像纯事件驱动那样冻帧），省下大部分空闲开销
            val fullRate = !session.tickEventDriven || contentDirty[0] ||
                session.tabAnimating || session.contentActive
            if (!fullRate && session.shouldIdleYield(now / 1_000_000L, contentDirty[0])) {
                // 静止空闲让步：本帧不 tick，给宿主主队列留出 IdleHandler 冲刷间隙
                // （见 GlassSession.shouldIdleYield）
            } else if (fullRate) {
                contentDirty[0] = false
                val renderInterval = session.glassRenderIntervalNs
                if (!session.tickEventDriven || renderInterval <= 0L || now - tickGate[0] >= renderInterval) {
                    tickGate[0] = now
                    tick.intValue++
                    ticks++
                }
            } else if (now - tickGate[0] >= STATIC_TICK_INTERVAL_NS) {
                tickGate[0] = now
                tick.intValue++
                ticks++
            }
            GlassInstaller.navInsetProvider?.invoke()?.takeIf { it >= 0 }?.let {
                if (it != bottomInset) {
                    bottomInset = it
                    session.logger?.invoke("glass bar bottom inset = $it px (nav inset provider)")
                }
            }
            GlassInstaller.glassStyle.let {
                if (it != style) {
                    style = it
                    session.armActivityWindow() // 样式热切换满帧率过渡
                }
            }
            if (session.isStale()) {
                session.onStale?.invoke()
                break
            }
            session.driveTabAnimation()
            session.mirrorVisibleGate?.let { gate ->
                val wanted = gate()
                if (wanted != barWanted) barWanted = wanted
            }
            val appSelected = session.selectedTabIndex()
            if (appSelected >= 0 && appSelected != selectedIndex) {
                selectedIndex = appSelected
            }
        }
    }
    // 选中变化（玻璃点击/应用同步/初次装配）→ 由玻璃侧直控进度播放进入动画
    LaunchedEffect(session) {
        snapshotFlow { selectedIndex }
            .collectLatest { session.beginTabAnimation(it) }
    }
    // 内容重绘信号 → 置脏标记；帧循环在同一 vsync 的动画回调阶段统一转换为 tick
    // （先于 traversal）——玻璃与应用内容同帧绘制，单次 traversal，无多余空跑
    DisposableEffect(session) {
        session.onContentInvalidated = { contentDirty[0] = true }
        onDispose { session.onContentInvalidated = null }
    }
    val fallbackInset = WindowInsets.navigationBars.getBottom(density)
    val effectiveInset = if (bottomInset >= 0) bottomInset else fallbackInset
    LaunchedEffect(effectiveInset) {
        session.logger?.invoke("glass bar bottom inset (effective) = $effectiveInset px")
    }

    val isLightTheme = !isSystemInDarkTheme()
    // 系统主题在宿主内不可靠（应用可能强制明暗），以应用自身内容色明暗判定设计方向：
    // 未选中色偏深 → 浅色设计 → 纯黑，否则纯白。直接给纯黑/纯白，达到角标级的
    // 完全不透明对比；应用未提供取色时回退系统主题。
    val contentColor = remember(spec, isLightTheme, specColors) {
        val app = specColors.first?.copy(alpha = 1f)
        val appIsDark = (app?.luminance() ?: if (isLightTheme) 0f else 1f) < 0.5f
        if (appIsDark) Color.Black else Color.White
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        // 栏宽 = min(可用宽 − 2×最小安全边距, tab 数 × 舒适宽度上限 + 8dp)：
        // tab 多时铺满可用宽（与原行为一致），tab 少时收窄为内容宽、居中显示
        val barWidth = min(
            maxWidth - style.minHorizontalMarginDp.dp * 2,
            (spec.tabs.size * MAX_TAB_WIDTH_DP + 8).dp
        ).coerceAtLeast(0.dp)
        // 应用声明隐藏底栏时（大图详情等二级页面）整条玻璃栏不渲染（与原生一致，且零绘制成本）
        if (barWanted) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(barWidth)
                    .padding(
                        bottom = with(density) {
                            effectiveInset.coerceAtLeast(0).toDp() + style.bottomGapDp.dp
                        }
                    )
                    .graphicsLayer {
                        translationY = GlassBarState.translationY
                        // 重现弹性：与按压放大同公式（16dp/栏宽），appearProgress 脉冲驱动
                        val widthPx = barWidth.toPx()
                        if (widthPx > 0f) {
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / widthPx, appearProgress.value)
                            scaleX = scale
                            scaleY = scale
                        }
                    }
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { selectedIndex },
                    onTabSelected = { index ->
                        // 拖动指示器路径：确定性转发——仅当应用尚未处于目标页时点击原 TabView。
                        // 应用驱动的同步变更（selectedIndex 已等于该页）天然不转发，无竞态。
                        if (session.selectedTabIndex() != index) {
                            session.onTabClicked(index)
                            // 点击转发改动底栏原生视图（非 GL 内容源），事件驱动模式下需显式请求重绘
                            session.requestTick()
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = spec.tabs.size,
                    barHeight = style.barHeightDp.dp,
                    blurRadius = style.blurRadiusDp.dp,
                    accentColor = specColors.second,
                    onPreviewTabChanged = {
                        session.previewTabChange(it)
                        // 拖动预览翻动底栏原生选中态（非 GL 内容源），显式请求重绘
                        session.requestTick()
                    },
                    onSelectedTabTapped = {
                        session.onTabClicked(it)
                        session.requestTick()
                    },
                    // 拖动结束停在原选中 tab：还原悬停预览（各 tab 原图标/选中态）
                    onPreviewEnded = {
                        session.endTabPreview()
                        session.requestTick()
                    },
                    // 液泡折射副本整层强调色 tint：选中/悬停 tab 在液泡内呈强调色，观感全局一致；
                    // 描述符可关（vivo 官网等"白=底色"编码素材必须关，否则白间隙被染成强调色实心）
                    tintRefractionContent = session.extractor.descriptor.tintRefractionContent,
                    content = {
                        // 标签强调色：选中 tab 与拖动悬停预览 tab 用应用强调色，其余内容色。
                        // 旧版该反馈由折射副本整层染色承担；tintRefractionContent=false 的
                        // 应用（vivo 官网，"白=底色"素材）改由此通道承担（染色开启的应用
                        // 液泡内仍由染色主导，视觉不变）。
                        // 预览激活时 selectedIndex 冻结为原 tab（防读数污染），故仅悬停
                        // tab 吃强调色、原 tab 回内容色（即"原 tab 变回未选中"的视觉）。
                        val previewIndex = LocalTabPreviewIndex.current.value
                        val accentColor = specColors.second ?: contentColor
                        fun labelColor(index: Int) =
                            if (index == previewIndex || (previewIndex == -1 && index == selectedIndex)) {
                                accentColor
                            } else {
                                contentColor
                            }
                        // 液泡覆盖槽位抑制（descriptor 声明式开关，默认 off = 既有应用渲染
                        // 逐位不变）：被液泡覆盖的可见槽位整槽不绘制，泡内折射副本成为唯一
                        // 图标来源。graphicsLayer 块在绘制相读取判定（与液泡同一动画状态源），
                        // 运动期间随覆盖关系重建层，零重组。
                        val isBubbleCovered = LocalLiquidBottomTabBubbleCovered.current
                        val suppressBubbleCovered = session.extractor.descriptor.suppressBubbleCoveredSlots
                        spec.tabs.forEachIndexed { index, tab ->
                            LiquidBottomTab(
                                modifier = if (suppressBubbleCovered) {
                                    Modifier.graphicsLayer {
                                        alpha = if (isBubbleCovered(index)) 0f else 1f
                                    }
                                } else {
                                    Modifier
                                },
                                onClick = {
                                selectedIndex = index
                                session.onTabClicked(index)
                                session.requestTick()
                            }) {
                                if (tab.mirrorWholeTab) {
                                    if (session.extractor.descriptor.iconMode == IconMode.WHOLE_TAB_FULL) {
                                        // WHOLE_TAB_FULL（微博）：tab 内容全自绘、无图标/标签子
                                        // 视图，整个条目视图按原生布局配色镜像为 tab 内容（选中态
                                        // 外观由条目自身驱动，玻璃层不再绘制标签）。1:1 居中：
                                        // 槽位与原生条目宽高比不同，fit 缩放会双向拉伸变形
                                        val tabView = tab.tabView ?: return@LiquidBottomTab
                                        val fullPainter = remember(tabView) {
                                            LiveTabFullPainter(tabView, tick)
                                        }
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(Modifier.fillMaxSize().paint(fullPainter))
                                        }
                                    } else {
                                        // WHOLE_TAB（相册）：图标元素镜像（原生配色 + ViewOverlay 角标）
                                        // + 统一标签绘制——观感与商店同构
                                        val iconView = tab.iconView
                                        if (iconView != null) {
                                            val iconPainter = remember(iconView) {
                                                LiveViewPainter(iconView, tick)
                                            }
                                            Box(
                                                Modifier.size(28.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(Modifier.size(24.dp).paint(iconPainter))
                                            }
                                        }
                                        if (style.showLabels) {
                                            BasicText(
                                                tab.title,
                                                style = TextStyle(labelColor(index), 12.sp, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                } else {
                                    // LOTTIE_RESOLVE（商店）：每帧解析当前可见的 Lottie 视图（首页 ⇄ 刷新），
                                    // 解析不到回退装配期 3x 位图快照
                                    val iconPainter = remember(index) {
                                        LiveIconPainter(
                                            { session.tabViews.getOrNull(index)?.let { session.extractor.findLottieView(it) } },
                                            tab.icon,
                                            tick,
                                        )
                                    }
                                    Box(
                                        Modifier.size(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .size(24.dp)
                                                .paint(iconPainter)
                                        )
                                    }
                                    if (style.showLabels) {
                                        BasicText(
                                            labelTexts[index] ?: tab.title,
                                            style = TextStyle(labelColor(index), 12.sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    overlayContent = {
                        spec.tabs.forEachIndexed { _, tab ->
                            // WHOLE_TAB 模式：角标/标签内嵌在整视图镜像里，无需覆盖层
                            if (tab.mirrorWholeTab) return@forEachIndexed
                            Column(
                                Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.size(28.dp)) {
                                    val badgeView = tab.badgeView
                                    val badgePainter = remember(badgeView) {
                                        badgeView?.let { LiveViewPainter(it, tick) }
                                    }
                                    if (badgePainter != null && badgeView != null) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = (-2).dp, y = (-2).dp)
                                                .size(
                                                    with(density) { badgeView.width.toDp() },
                                                    with(density) { badgeView.height.toDp() },
                                                )
                                                .paint(badgePainter)
                                        )
                                    } else if (tab.badgeText != null) {
                                        BasicText(
                                            tab.badgeText,
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = (-2).dp, y = (-2).dp)
                                                .background(accentBadge, RoundedCornerShape(50))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = TextStyle(Color.White, 9.sp)
                                        )
                                    } else if (tab.badgeDot) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = (-2).dp, y = (-2).dp)
                                                .size(8.dp)
                                                .background(accentBadge, CircleShape)
                                        )
                                    }
                                }
                                if (style.showLabels) {
                                    // 占位：与真实标签同高，使角标列垂直几何与主层一致
                                    BasicText(" ", style = TextStyle(contentColor, 12.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

private val accentBadge = Color(0xFFEB5545)

/** 单个 tab 的舒适宽度上限（dp）：栏宽 = min(可用宽 − 2×最小安全边距, tab 数 × 此值 + 8dp)，
 *  tab 少时栏收窄为内容宽居中；tab 多且未达上限时铺满可用宽。 */
private const val MAX_TAB_WIDTH_DP = 70

/** 相册静止档 tick 间隔：内容/交互活动窗口结束后降 30fps 低频采样（~33ms）。
 *  低频档持续采样是关键——GL 合成空窗的空白录制最多停留一档即自愈，不会冻帧。 */
private const val STATIC_TICK_INTERVAL_NS = 33_333_333L

/**
 * 商店 tab 图标逐帧镜像 Painter：每帧经 [viewProvider] 解析当前应镜像的视图
 * （LOTTIE_RESOLVE 模式下应用切换 Lottie 可见性，首页 ⇄ 刷新），直绘硬件画布——
 * 原色、原生动画、原生清晰度；无视图可解析时回退装配期的 3x 位图快照。
 * 可见性按 isShown（沿父链）判定：应用隐藏底栏时镜像同步消失。
 */
private class LiveIconPainter(
    private val viewProvider: () -> View?,
    private val fallback: android.graphics.Bitmap?,
    private val tick: IntState,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    private val dstRect = android.graphics.RectF()

    override fun DrawScope.onDraw() {
        tick.intValue
        val target = viewProvider()
        val canvas = drawContext.canvas.nativeCanvas
        if (target == null) {
            // 装配期未取到 Lottie 视图：位图兜底
            fallback?.let { bmp ->
                dstRect.set(0f, 0f, size.width, size.height)
                canvas.drawBitmap(bmp, null, dstRect, null)
            }
            return
        }
        if (!target.isShown || target.width <= 0 || target.height <= 0) return
        canvas.save()
        canvas.scale(size.width / target.width, size.height / target.height)
        target.draw(canvas)
        canvas.restore()
    }
}

/**
 * WHOLE_TAB 图标/角标逐帧镜像 Painter：固定镜像目标视图，直绘硬件画布——
 * 原色、原始 alpha、原生动画，不做任何处理。可见性按 isShown（沿父链）判定：
 * 应用隐藏底栏（如进入照片大图）时镜像同步消失。
 */
private class LiveViewPainter(
    private val view: View,
    private val tick: IntState,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        tick.intValue
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || !view.isShown) return
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / vw, size.height / vh)
        view.draw(canvas)
        canvas.restore()
    }
}

/**
 * WHOLE_TAB_FULL 整条目镜像 Painter：**1:1 原尺寸居中**绘制，绝不缩放——
 * 玻璃槽位（barWidth/tab 数宽 × 栏高−8dp）与原生条目（屏宽/tab 数 × 应用栏高）
 * 宽高比必然不同，fit 缩放会双向不等比拉伸（微博实测图标/文字变形）；原生条目
 * 内容居中，1:1 居中后超出槽位的只有条目自身的空白边，视觉无裁切。
 */
private class LiveTabFullPainter(
    private val view: View,
    private val tick: IntState,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        tick.intValue
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || !view.isShown) return
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate((size.width - vw) / 2f, (size.height - vh) / 2f)
        view.draw(canvas)
        canvas.restore()
    }
}
