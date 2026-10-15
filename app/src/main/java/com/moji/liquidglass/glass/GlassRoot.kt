package com.moji.liquidglass.glass

import android.view.View
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
import com.moji.liquidglass.glass.components.LiquidBottomTab
import com.moji.liquidglass.glass.components.LiquidBottomTabs
import kotlinx.coroutines.flow.collectLatest

/**
 * 玻璃底栏（覆盖层模式的条带布局）。
 *
 * 应用内容视图在 Compose 之外原位渲染（触摸路径原生），玻璃通过 ClippedViewBackdrop
 * 跨分支绘制内容视图的胶囊条带（view.draw 不要求同树），tick 驱动逐帧采样，滚动折射实时。
 *
 * 布局：ComposeView（WRAP_CONTENT、Gravity.BOTTOM）→ 根 Box 顶部 48dp 为辉光/液泡
 * 放大余量；玻璃栏底边内边距 = 应用真实导航栏 inset（navInsetProvider 三级回退：原容器
 * paddingBottom → rootWindowInsets 的 navigationBars → Compose insets）——玻璃栏抬升
 * 到小白条上方，下方条带透出全出血的信息流内容。
 * 二楼下拉位移经 GlassBarState 驱动 graphicsLayer 跟随，与原软件行为一致。
 */
@Composable
fun GlassRoot(
    appRoot: View,
    spec: GlassBarSpec,
    session: GlassSession
) {
    val density = LocalDensity.current
    val tick = remember { mutableIntStateOf(0) }
    val backdrop = remember(appRoot) {
        ClippedViewBackdrop(
            view = appRoot,
            tick = { tick.intValue },
            viewPosition = {
                val out = IntArray(2)
                appRoot.getLocationOnScreen(out)
                Offset(out[0].toFloat(), out[1].toFloat())
            },
        )
    }

    // 底部 inset：优先应用侧真实值（navInsetProvider），不可用时回退 Compose insets
    var bottomInset by remember { mutableIntStateOf(-1) }

    // 标签文本跟随：原 TextView 文本变化（首页 ⇄ 刷新）实时同步到玻璃标签
    val labelTexts = remember(spec) {
        mutableStateMapOf<Int, String>().apply {
            spec.tabs.forEachIndexed { i, tab -> put(i, tab.title) }
        }
    }
    DisposableEffect(spec) {
        val watchers = spec.tabs.mapIndexed { index, tab ->
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

    // 样式（模块设置页可调，hook 层同步到 GlassInstaller，帧循环检测变化驱动重组）
    var style by remember { mutableStateOf(GlassInstaller.glassStyle) }

    var selectedIndex by remember { mutableIntStateOf(spec.initialIndex) }
    // 应用 → 玻璃：帧循环读取原 TabView 的 isSelected（应用自身切页时保持同步）。
    LaunchedEffect(session) {
        var lastFrame = withFrameNanos { it }
        var frames = 0
        var sumNs = 0L
        var maxNs = 0L
        while (true) {
            val now = withFrameNanos { it }
            val gapNs = now - lastFrame
            lastFrame = now
            frames++
            sumNs += gapNs
            if (gapNs > maxNs) maxNs = gapNs
            if (frames >= 120) {
                session.logger?.invoke(
                    "frame metrics: 120 frames avg=${sumNs / 120 / 1_000_000}ms max=${maxNs / 1_000_000}ms"
                )
                frames = 0
                sumNs = 0
                maxNs = 0
                // 低频重申原生底栏的 clickable 压制（应用刷新可能重设监听/可点击）
                session.reassertClickSuppression()
            }
            if (gapNs > 500_000_000) {
                session.logger?.invoke("frame stall ${gapNs / 1_000_000}ms (main thread or render pipeline blocked)")
            }
            tick.intValue++
            GlassInstaller.navInsetProvider?.invoke()?.takeIf { it >= 0 }?.let {
                if (it != bottomInset) {
                    bottomInset = it
                    session.logger?.invoke("glass bar bottom inset = $it px (nav inset provider)")
                }
            }
            GlassInstaller.glassStyle.let {
                if (it != style) style = it
            }
            if (session.isStale()) {
                session.onStale?.invoke()
                break
            }
            session.driveTabAnimation()
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
    val fallbackInset = WindowInsets.navigationBars.getBottom(density)
    val effectiveInset = if (bottomInset >= 0) bottomInset else fallbackInset
    LaunchedEffect(effectiveInset) {
        session.logger?.invoke("glass bar bottom inset (effective) = $effectiveInset px")
    }

    val isLightTheme = !isSystemInDarkTheme()
    // 系统主题在宿主内不可靠（应用可能强制明暗），以应用自身内容色明暗判定设计方向：
    // 未选中色偏深 → 浅色设计 → 纯黑，否则纯白。直接给纯黑/纯白，达到角标级的
    // 完全不透明对比；应用未提供取色时回退系统主题。
    val contentColor = remember(spec, isLightTheme) {
        val app = spec.contentColor?.copy(alpha = 1f)
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
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .width(barWidth)
                .padding(
                    bottom = with(density) {
                        effectiveInset.coerceAtLeast(0).toDp() + style.bottomGapDp.dp
                    }
                )
                .graphicsLayer { translationY = GlassBarState.translationY }
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex },
                onTabSelected = { index ->
                    // 拖动指示器路径：确定性转发——仅当应用尚未处于目标页时点击原 TabView。
                    // 应用驱动的同步变更（selectedIndex 已等于该页）天然不转发，无竞态。
                    if (session.selectedTabIndex() != index) {
                        session.onTabClicked(index)
                    }
                },
                backdrop = backdrop,
                tabsCount = spec.tabs.size,
                barHeight = style.barHeightDp.dp,
                blurRadius = style.blurRadiusDp.dp,
                accentColor = spec.accentColor,
                onPreviewTabChanged = { session.previewTabChange(it) },
                onSelectedTabTapped = { session.onTabClicked(it) },
                content = {
                    spec.tabs.forEachIndexed { index, tab ->
                        LiquidBottomTab(onClick = {
                            selectedIndex = index
                            session.onTabClicked(index)
                        }) {
                            if (tab.mirrorWholeTab) {
                                // WHOLE_TAB：整个原 tab 视图逐帧镜像直出（图标/标签/角标一体，零适配）
                                val wholeView = tab.tabView
                                if (wholeView != null) {
                                    val painter = remember(wholeView) { LiveViewPainter(wholeView, tick) }
                                    Box(Modifier.fillMaxSize().paint(painter))
                                }
                            } else {
                                // LOTTIE_RESOLVE：每帧解析当前可见的 Lottie 视图（首页 ⇄ 刷新切换），
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
                                        style = TextStyle(contentColor, 12.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                },
            overlayContent = {
                // 角标覆盖层：渲染在液泡之上，任何选中状态下都原生红/原色直出
                // （液泡折射副本层整层带强调色 tint，角标放主层会被染色）。
                // 每列复刻主层 LiquidBottomTab 的几何（28dp 图标框 + 2dp 间距 +
                // 标签占位），保证角标与主层图标位置逐像素对齐。
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

private val accentBadge = Color(0xFFEB5545)

/** 单个 tab 的舒适宽度上限（dp）：栏宽 = min(可用宽 − 2×最小安全边距, tab 数 × 此值 + 8dp)，
 *  tab 少时栏收窄为内容宽居中；tab 多且未达上限时铺满可用宽。 */
private const val MAX_TAB_WIDTH_DP = 70

/**
 * tab 图标逐帧镜像 Painter：每帧经 [viewProvider] 解析当前应镜像的视图
 * （如 LOTTIE_RESOLVE 模式下应用切换 Lottie 可见性），直绘硬件画布——
 * 原色、原生动画、原生清晰度；解析不到时回退装配期的 3x 位图快照。
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
        if (target != null && target.width > 0 && target.height > 0 && target.visibility == View.VISIBLE) {
            canvas.save()
            canvas.scale(size.width / target.width, size.height / target.height)
            target.draw(canvas)
            canvas.restore()
            return
        }
        fallback?.let { bmp ->
            dstRect.set(0f, 0f, size.width, size.height)
            canvas.drawBitmap(bmp, null, dstRect, null)
        }
    }
}

/**
 * 应用真实视图的逐帧镜像 Painter：draw 阶段读取帧循环 tick（Compose 在状态变化时
 * 触发重绘），把视图原样缩放绘制进 Compose 画布——原色、原始 alpha、原生动画，
 * 不做任何处理，与原底栏像素一致，与玻璃 backdrop 采样同帧同步。
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
        if (vw <= 0 || vh <= 0 || view.visibility != View.VISIBLE) return
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(size.width / vw, size.height / vh)
        view.draw(canvas)
        canvas.restore()
    }
}
