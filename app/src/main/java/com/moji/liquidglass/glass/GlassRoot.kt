package com.moji.liquidglass.glass

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.moji.liquidglass.glass.components.REFRACTION_BOX_OVERDRAW_BELOW
import com.moji.liquidglass.glass.components.LocalLiquidBottomTabBubbleCovered
import com.moji.liquidglass.glass.components.LocalTabPreviewIndex
import com.moji.liquidglass.glass.components.LiquidBottomTab
import com.moji.liquidglass.glass.components.LiquidBottomTabs
import com.moji.liquidglass.glass.utils.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 玻璃底栏（覆盖层模式的条带布局）。
 *
 * 应用内容视图在 Compose 之外原位渲染（触摸路径原生），玻璃通过 ClippedViewBackdrop
 * 跨分支绘制内容视图的胶囊条带（view.draw 不要求同树），tick 驱动逐帧采样，滚动折射实时。
 *
 * 布局：ComposeView（WRAP_CONTENT、Gravity.BOTTOM）→ 根 Box 顶部 48dp 为辉光/液泡
 * 放大余量；玻璃栏底边内边距 = 应用真实导航栏 inset（navInsetProvider 三级回退）——
 * 玻璃栏抬升到小白条上方，下方条带透出全出血的信息流内容。
 * 底部语义（v3.2）：bottomGap=0 = 玻璃胶囊底边贴导航条顶——折射副本行下沿外扩
 * （REFRACTION_BOX_OVERDRAW_BELOW，alpha=0 视觉不可见）已从底部 padding 扣除。
 * 二楼下拉位移经 GlassBarState 驱动 graphicsLayer 跟随，与原软件行为一致。
 *
 * 多目标：渲染由 GlassBarSpec（适配器提取）驱动——
 * - LOTTIE_RESOLVE（商店）：每帧解析可见 Lottie 图标 + Compose 标签 + 角标覆盖层，
 *   液泡折射副本整层强调色 tint（选中 tab 呈强调色）；
 * - WHOLE_TAB（相册）：图标元素镜像 + 统一标签绘制，条目 foreground 角标走顶层覆盖层
 *   （overlayContent，浮在液泡之上、随玻璃表面按压缩放/平移），
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
            liveNodeSampling = { session.backdropLiveNodeSampling },
            freeze = { session.backdropFreezeGate?.invoke() ?: false },
            forceRecord = {
                val flag = session.backdropForceRecord
                if (flag) session.backdropForceRecord = false
                flag
            },
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
    // 辅助玻璃视图边界（auxGlassViews 声明式通道，帧循环逐 tick 读取；仅变化时写入
    // → 静止零重组。偏移相对 ComposeView 原点，px）
    var auxBounds by remember { mutableStateOf(emptyList<AuxGlassBounds>()) }
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
                    private val labelOverrides = session.extractor.descriptor.labelOverrides

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        s?.toString()?.takeIf { it.isNotBlank() }?.let { text ->
                            labelTexts[index] = labelOverrides[text] ?: text
                        }
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
        // 帧序号（单调递增，高刷减半分频的相位源）与分频值（120 帧窗口按实测平均
        // 帧间隔重估，LTPO 动态刷新率下自动跟随）
        var frameIndex = 0L
        var halfRateDivider = 1
        while (true) {
            val now = withFrameNanos { it }
            val gapNs = now - lastFrame
            lastFrame = now
            frameIndex++
            frames++
            sumNs += gapNs
            if (gapNs > maxNs) maxNs = gapNs
            if (frames >= 120) {
                session.logger?.invoke(
                    "frame metrics: 120 frames avg=${sumNs / 120 / 1_000_000}ms max=${maxNs / 1_000_000}ms " +
                        "ticks=$ticks"
                )
                // 高刷减半分频判定（idleDecay 会话）：**实测平均帧间隔**而非
                // display.refreshRate 一次性采样——LTPO 动态刷新率下后者随状态漂移
                // （重进应用/页面切换时 120/90/60 读数各异，一次性判定失真且跨会话
                // 不一致）；分频按**帧计数**（偶数帧 tick）不按 ns 时长——任意刷新率下
                // 恒为整数倍、零拍频（ns 门控在非整数比率下必拍频，相册教训）
                halfRateDivider = if (session.idleDecayTickEnabled &&
                    sumNs > 0L && 1_000_000_000L / (sumNs / 120) > 66L
                ) 2 else 1
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
            // 静止降帧两档（idleDecayTickEnabled，相册设计/小黑盒首接线）：tickEventDriven=
            // false 且已接内容活动信号的会话，活动窗口（内容重绘/交互 armActivityWindow
            // 后 1s）内每帧，静止后降 30fps 低频 tick——低频档仍持续采样，合成空窗的
            // 空白帧最多停留 ~33ms 自愈（不会像纯事件驱动那样冻帧），省下大部分空闲开销。
            // **无信号源不得开启**：静止档跟不上无信号的内容变化（持续滞后）。
            // 注意：`!tickEventDriven && !idleDecayTickEnabled` 兜底项 = 无信号会话的
            // 每帧恒满档——旧表达式（!tickEventDriven || …）曾使两档设计从未生效
            // （tickEventDriven=false 恒真短路，静止降帧档不可达，adc56c0 起的潜伏回归）。
            val fullRate = contentDirty[0] || session.tabAnimating || session.contentActive ||
                (!session.tickEventDriven && !session.idleDecayTickEnabled)
            if (!fullRate && session.shouldIdleYield(now / 1_000_000L, contentDirty[0])) {
                // 静止空闲让步：本帧不 tick，给宿主主队列留出 IdleHandler 冲刷间隙
                // （见 GlassSession.shouldIdleYield）
            } else if (fullRate) {
                contentDirty[0] = false
                val renderInterval = session.glassRenderIntervalNs
                // renderInterval 仅事件驱动会话生效（adc56c0 起既有语义——ns 时长门控
                // 在非整数刷新率比率下拍频，非事件驱动会话的节流走 idleDecay 两档 +
                // 高刷减半分频；曾试改为对所有会话生效，相册残留 capHz 配置被激活拍频，
                // 真机回退）
                val intervalOk = !session.tickEventDriven || renderInterval <= 0L ||
                    now - tickGate[0] >= renderInterval
                // 高刷减半分频（idleDecay 会话）：偶数帧才 tick——玻璃条带重录制与
                // Compose traversal 减半，宿主自绘/液泡/按压/选中动画读各自状态独立
                // 失效不受影响；8dp 模糊下玻璃 60fps 无感
                if (intervalOk && (halfRateDivider <= 1 || frameIndex % halfRateDivider == 0L)) {
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
            // 辅助玻璃视图边界：逐 tick 读取各视图屏幕坐标与 isShown（视图被应用隐藏/
            // 移动时胶囊同步跟随），仅变化时写入 state
            session.auxGlassViews?.let { provider ->
                val views = runCatching { provider() }.getOrNull().orEmpty()
                val origin = IntArray(2)
                session.composeView.getLocationOnScreen(origin)
                val list = views.mapNotNull { v ->
                    if (!v.isShown || v.width <= 0 || v.height <= 0) return@mapNotNull null
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    AuxGlassBounds(loc[0] - origin[0], loc[1] - origin[1], v.width, v.height, v)
                }
                if (list != auxBounds) auxBounds = list
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

    Box(Modifier.fillMaxWidth()) {
        // 辅助玻璃视图层（声明式通道：auxGlassViews 未注入 = auxBounds 恒空，渲染零影响）。
        // 放在外层 Box：坐标相对 ComposeView 原点（内层 BoxWithConstraints 有 48dp 顶部
        // padding，会引入坐标偏移）。胶囊可位于 overlay 布局边界之外（不裁剪、无点击
        // 消费——触摸穿透到被压制的原生视图，保留应用自身的点击行为）。
        auxBounds.forEach { bounds ->
            AuxGlassPill(
                bounds,
                backdrop,
                style,
                tick,
                shape = session.auxGlassShape?.invoke(bounds.view) ?: Capsule(),
            )
        }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        // 栏宽 = min(可用宽 − 2×最小安全边距, tab 数 × 舒适宽度上限 + 8dp)：
        // tab 多时铺满可用宽（与原行为一致），tab 少时收窄为内容宽、居中显示；
        // 舒适宽度上限按 descriptor 覆盖（tab 极少且原生条目远宽于 70dp 槽位的应用，
        // 默认 null = 全局值，其余应用逐位不变）
        val maxTabWidthDp = session.extractor.descriptor.maxTabWidthDp ?: MAX_TAB_WIDTH_DP
        // 附加操作按钮（descriptor 声明式通道，默认 null = 既有应用零影响）：玻璃栏右侧
        // 独立圆形着色玻璃图标按钮（参考项目 Glass Bottom Bar 教程 "Exercise: Add a
        // tinted glass icon button" 同构：Row spacedBy(8dp)，栏与按钮平行等高，按钮
        // size = 栏高、aspectRatio 1f 外接方盒 + CircleShape 圆形玻璃）。声明时栏宽
        // 可用量扣除 按钮直径（= 栏高）+ 间距；通道 null 时布局与旧实现逐位不变（§0.4 红线）。
        val actionButton = session.extractor.descriptor.extraActionButton
        val barWidth = min(
            maxWidth - style.minHorizontalMarginDp.dp * 2 -
                (if (actionButton != null) style.barHeightDp.dp + 8.dp else 0.dp),
            (spec.tabs.size * maxTabWidthDp + 8).dp
        ).coerceAtLeast(0.dp)
        // 相邻玻璃耦合（库内置 CombinedBackdrop 采样，仅声明按钮时启用）：**单向**——
        // 栏录制为采样层，按钮采样 combined(内容, 栏层)，按钮边缘 lens 折射栏体。
        // **禁止双向**（栏再采样按钮层）：layerBackdrop 录制的内容又去消费它 = 跨层
        // 绘制环 → RenderThread SIGSEGV 无限递归（backdrop 官方 FAQ 点名的已知崩溃，
        // 真机实证；单向时被采样层的内容只消费原始 backdrop，环消失）。层未录制的
        // 首帧空采样 ≤ 1 帧（气泡既有行为，可接受）。
        val barLayer = rememberLayerBackdrop()
        val buttonBackdrop = rememberCombinedBackdrop(backdrop, barLayer)
        // 应用声明隐藏底栏时（大图详情等二级页面）整条玻璃栏不渲染（与原生一致，且零绘制成本）
        if (barWanted) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = with(density) {
                    // 底部语义（v3.2 定案）：bottomGap=0 = 玻璃胶囊底边贴导航条顶（inset
                    // 上沿）。LiquidBottomTabs 外层盒比栏高多出折射行下沿外扩（alpha=0
                    // 视觉不可见，REFRACTION_BOX_OVERDRAW_BELOW），此处扣除还原语义；
                    // inset+gap 不足该值时收敛 0（负 padding 非法，玻璃最低贴内容底）。
                    (effectiveInset.coerceAtLeast(0).toDp() + style.bottomGapDp.dp -
                        REFRACTION_BOX_OVERDRAW_BELOW).coerceAtLeast(0.dp)
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
                    },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(barWidth)
                .then(
                    // 栏体录制为采样层：供右侧按钮折射（仅声明按钮时；单向——栏自身
                    // 只消费原始 backdrop，见上方耦合注释）
                    if (actionButton != null) Modifier.layerBackdrop(barLayer) else Modifier
                )
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
                    surfaceAlpha = style.surfaceAlpha,
                    saturation = style.saturation,
                    refractionScale = style.refractionScale,
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
                        // 标签强调色：描述符声明回退时（强调色缺失或与内容色相同——原生
                        // 选中态不靠颜色表达的应用，如文件管理），回退到液泡折射染色的
                        // 强调色，与气泡观感一致（§9.2）；否则维持现状。
                        val accentColor = if (session.extractor.descriptor.labelAccentFallbackToBubble) {
                            specColors.second?.takeIf { it != specColors.first } ?: bubbleAccent(isLightTheme)
                        } else {
                            specColors.second ?: contentColor
                        }
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
                                            LiveTabFullPainter(
                                                tabView,
                                                tick,
                                                session.extractor.descriptor.wholeTabAlignBottom,
                                            )
                                        }
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(Modifier.fillMaxSize().paint(fullPainter))
                                        }
                                    } else {
                                        // WHOLE_TAB（相册）：图标元素镜像（原生配色）+ 统一标签绘制
                                        // ——观感与商店同构；角标（条目 foreground）走顶层覆盖层，
                                        // 不进内容层（内容层是液泡折射副本的采样源，角标会被液泡
                                        // 折射着色/盖住，见 overlayContent）
                                        val iconView = tab.iconView
                                        if (iconView != null) {
                                            val iconPainter = remember(iconView) {
                                                LiveViewPainter(iconView, tick)
                                            }
                                            // 图标镜像盒按 descriptor 覆盖（默认 24dp）：镜像把
                                            // 原生图标视图 fit 缩放进盒内，原生图标视图非 24dp 的
                                            // 应用（手机管家 ROM16 动态符号 32dp）按原尺寸声明避免
                                            // 被等比缩小；盒大于 28dp 预留时居中溢出（内容层无裁剪）
                                            val iconBox = (session.extractor.descriptor.iconBoxDp ?: 24).dp
                                            Box(
                                                Modifier.size(28.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(Modifier.size(iconBox).paint(iconPainter))
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
                        spec.tabs.forEachIndexed { index, tab ->
                            // WHOLE_TAB_FULL：角标内嵌在整条目镜像里（条目 draw 自含前景），无需覆盖层
                            if (tab.mirrorWholeTab &&
                                session.extractor.descriptor.iconMode == IconMode.WHOLE_TAB_FULL
                            ) {
                                return@forEachIndexed
                            }
                            Column(
                                Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.size(28.dp)) {
                                    if (tab.mirrorWholeTab) {
                                        // WHOLE_TAB 元素级角标（条目 foreground）顶层覆盖：与内容层
                                        // 图标镜像盒同几何（28dp 预留居中 iconBox 盒，iconBox 按
                                        // descriptor 覆盖默认 24dp），ItemForegroundPainter
                                        // 按 icon 在条目内的相对几何把条目 foreground 映射到盒内——
                                        // 浮在液泡之上原色直出、随玻璃表面按压缩放/弹性平移
                                        // （与商店角标覆盖层同通道，不进折射副本层）
                                        val tabView = tab.tabView
                                        val iconView = tab.iconView
                                        if (tabView != null && iconView != null) {
                                            val foregroundPainter = remember(tabView, iconView) {
                                                ItemForegroundPainter(
                                                    tabView,
                                                    iconView,
                                                    tick,
                                                    with(density) { ITEM_BADGE_SINK_DP.toPx() },
                                                )
                                            }
                                            Box(
                                                Modifier.size(
                                                    (session.extractor.descriptor.iconBoxDp ?: 24).dp
                                                ).paint(foregroundPainter)
                                            )
                                        }
                                    } else {
                                        // LOTTIE_RESOLVE（商店）：镜像角标 > 文本 > 红点三分支
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
            // 附加操作按钮：栏右侧独立圆形着色玻璃图标按钮（参考项目教程同构：
            // Row spacedBy(8dp) + 等高 CircleShape 圆形 + aspectRatio 1f 外接盒）
            if (actionButton != null) {
                ExtraActionButton(actionButton, buttonBackdrop, style, tick, style.barHeightDp.dp)
            }
        }
        }
    }
    }
}

private val accentBadge = Color(0xFFEB5545)

/** WHOLE_TAB 条目角标（条目 foreground 镜像）的整体下沉量：原生条目图标上方留白比
 *  玻璃栏大（图标盒距栏顶 ~3dp vs 原生符号 marginTop ~10dp），严格按"相对图标原生
 *  位置"锚定会把 TOP_END 悬出量顶出胶囊上缘（真机反馈"有点超出底栏"），下沉收回栏内。 */
private val ITEM_BADGE_SINK_DP = 6.dp

/** 液泡折射染色默认强调色（与 LiquidBottomTabs.resolvedAccentColor 同源）：标签强调色
 *  回退（labelAccentFallbackToBubble）时使用，保证标签与气泡观感一致。 */
private fun bubbleAccent(isLightTheme: Boolean): Color =
    if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)

/** 辅助玻璃视图的逐帧边界快照（偏移相对 ComposeView 原点，px）。 */
private data class AuxGlassBounds(
    val offsetX: Int,
    val offsetY: Int,
    val widthPx: Int,
    val heightPx: Int,
    val view: View,
)

/**
 * 附加操作按钮（extraActionButton 声明式通道）：玻璃栏右侧的**独立**圆形着色玻璃
 * 图标按钮，参考项目 Glass Bottom Bar 教程 "Exercise: Add a tinted glass icon button"
 * 同构——栏与按钮平行等高、Row spacedBy(8dp)、按钮 size = 栏高（aspectRatio 1f 外接
 * 方盒）+ `CircleShape` 圆形玻璃；同一采样源（[backdrop] = 内容 + 栏层的组合，相邻
 * 耦合见调用点）+ 同一 GlassStyle 模糊/饱和/折射参数（样式页热生效一致），表面 =
 * `tint` Hue 混合 + 0.75 alpha 覆盖（参考配方原样；tint 为 null = 纯玻璃不着色），
 * 内容 = 应用图标逐帧绘制（intrinsic 像素 1:1 居中，超盒等比收缩）。
 *
 * 液态交互 = catalog 源码 **LiquidButton 逐字移植**（驱动 = InteractiveHighlight 移植件
 * pressProgress/offset，demo 手感含按压回弹以其弹簧为准）：
 * - layerBlock = 官方公式逐字：按压缩放（4dp/高）+ tanh 跟随平移（maxOffset = 短边、
 *   initialDerivative = 0.05）+ 拖拽方向性拉伸（maxDragScale = 4dp/高按偏移轴比线性）
 *   ——内容变形而采样背景不跟随；
 * - 高光随按压渐入（`Highlight.Default.copy(alpha = progress)`）；
 * - 手势 = `interactiveHighlight.gestureModifier`（纯观察不消费事件，全文核对），
 *   与 clickable 叠用安全。点击消费并转发 [ExtraActionButtonSpec.onClick]（栏内触摸
 *   不穿透——与栏外 aux 胶囊的穿透语义相反，后者见 AuxGlassPill）。
 */
@Composable
private fun ExtraActionButton(
    spec: ExtraActionButtonSpec,
    backdrop: com.kyant.backdrop.Backdrop,
    style: GlassStyle,
    tick: IntState,
    diameter: androidx.compose.ui.unit.Dp,
) {
    val animationScope = rememberCoroutineScope()
    // 交互驱动 = 参考项目 catalog LiquidButton 原版：InteractiveHighlight（本项目一比一
    // 移植件）pressProgress/offset——demo 手感（含按压回弹）以其弹簧为准，1:1 保真
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    Box(
        Modifier
            .size(diameter)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    colorControls(saturation = style.saturation)
                    blur(style.blurRadiusDp.dp.toPx())
                    lens(
                        24f.dp.toPx() * style.refractionScale,
                        24f.dp.toPx() * style.refractionScale,
                    )
                },
                // LiquidButton layerBlock 逐字移植：按压缩放（4dp/高）+ tanh 跟随平移
                // （maxOffset = 短边、initialDerivative = 0.05——跟手但极收敛，永不拉满）
                // + 拖拽方向性拉伸（水平拖横向拉、竖直拖纵向拉，各按偏移比例线性增长）。
                // 变形放 layerBlock：内容变形而采样背景不变形。
                layerBlock = {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                    scaleY =
                        scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                },
                highlight = { Highlight.Default.copy(alpha = interactiveHighlight.pressProgress) },
                onDrawSurface = {
                    spec.accent()?.let { tint ->
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                },
            )
            .clickable {
                spec.onClick()
            }
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().paint(remember(spec) { ExtraIconPainter(spec, tick) }))
    }
}

/** 附加操作按钮的图标逐帧绘制器：每次绘制实时解析 [ExtraActionButtonSpec.icon]
 *  （换肤/深浅色自动跟随，不缓存），按 intrinsic 像素尺寸 1:1 居中绘制，超出按钮
 *  外接盒时等比收缩（不放大）。DrawScope 坐标与 drawable intrinsic 同为像素，无
 *  密度换算。 */
private class ExtraIconPainter(
    private val spec: ExtraActionButtonSpec,
    private val tick: IntState,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        tick.value
        val drawable = spec.icon() ?: return
        val intrinsicW = drawable.intrinsicWidth
        val intrinsicH = drawable.intrinsicHeight
        if (intrinsicW <= 0 || intrinsicH <= 0) return
        var widthPx = intrinsicW.toFloat()
        var heightPx = intrinsicH.toFloat()
        if (widthPx > size.width || heightPx > size.height) {
            val scale = minOf(size.width / widthPx, size.height / heightPx)
            widthPx *= scale
            heightPx *= scale
        }
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate((size.width - widthPx) / 2f, (size.height - heightPx) / 2f)
        drawable.setBounds(0, 0, widthPx.toInt().coerceAtLeast(1), heightPx.toInt().coerceAtLeast(1))
        drawable.draw(canvas)
        canvas.restore()
    }
}


/**
 * 辅助玻璃底（auxGlassViews 声明式通道）：与玻璃底栏同款的玻璃底（同采样源、同
 * GlassStyle 参数），形状按 `session.auxGlassShape` 声明（默认胶囊 Capsule），内容 =
 * 原生视图 1:1 逐帧镜像（原生视图已被适配器压制：alpha=0 + 背景置空——`View.draw()`
 * 手动调用不施加 alpha，镜像取到完整内容且无鬼影；点击不被玻璃底消费，穿透到原生
 * 视图）。样式随玻璃栏样式页热生效。
 */
@Composable
private fun AuxGlassPill(
    bounds: AuxGlassBounds,
    backdrop: com.kyant.backdrop.Backdrop,
    style: GlassStyle,
    tick: IntState,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = style.surfaceAlpha)
        else Color(0xFF121212).copy(alpha = style.surfaceAlpha)
    val density = LocalDensity.current
    val mirrorPainter = remember(bounds.view) { LiveViewPainter(bounds.view, tick) }
    Box(
        Modifier
            .offset { IntOffset(bounds.offsetX, bounds.offsetY) }
            .size(
                with(density) { bounds.widthPx.toDp() },
                with(density) { bounds.heightPx.toDp() },
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(saturation = style.saturation)
                    blur(style.blurRadiusDp.dp.toPx())
                    lens(
                        24f.dp.toPx() * style.refractionScale,
                        24f.dp.toPx() * style.refractionScale,
                    )
                },
                onDrawSurface = { drawRect(containerColor) }
            )
    ) {
        Box(Modifier.fillMaxSize().paint(mirrorPainter))
    }
}

/** 单个 tab 的舒适宽度上限（dp，默认值）：栏宽 = min(可用宽 − 2×最小安全边距,
 *  tab 数 × 此值 + 8dp)，tab 少时栏收窄为内容宽居中；tab 多且未达上限时铺满可用宽。
 *  descriptor `maxTabWidthDp` 可按应用覆盖（手机管家 2 tab 先例）。 */
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
 * WHOLE_TAB 条目 foreground 镜像 Painter（角标通道，顶层覆盖层渲染）：originui 族
 * 底栏（主题/相册/官网/文件管理）的角标 VBadgeDrawable 经 setBadge 挂在**条目视图的
 * foreground**（updateBadgeCoordinates(iconView, item) 把条目设为 customBadgeParent →
 * 条目 setForeground(badge)；挂图标 ViewOverlay 的路径在无 customBadgeParent 时才走）——
 * 角标不在 icon/labels 元素子树内，元素级镜像取不到。foreground 的坐标系是条目本地
 * 坐标，这里按图标元素（anchor）在条目内的相对几何映射到 24dp 图标盒：先缩放
 * （盒宽/图标宽，与 LiveViewPainter 图标镜像同缩放，角标尺寸随图标镜像一致），
 * 再平移 -(icon 相对条目的偏移) 把图标原点映射到画布原点——角标按"相对图标的原生
 * 位置"锚定（TOP_END 悬出图标边界的部分照常画出，Compose paint 不裁切）。
 * [verticalShiftPx] 为整体下沉修正：原生条目图标上方留白比玻璃栏大（图标盒距栏顶
 * ~3dp vs 原生符号 marginTop ~10dp），严格锚定会把 TOP_END 悬出量顶出胶囊上缘，
 * 下沉几 dp 收回栏内（仍贴图标右上角）。外层再叠加画布平移（缩放前调用 = 设备
 * 像素空间的最终位移）。
 * 经 GlassRoot overlayContent 渲染在液泡之上（不进折射副本层），与玻璃栏 Row 同款
 * 按压缩放 + panelOffset 平移（"贴在玻璃表面"，与商店角标覆盖层同通道）。
 * 每帧动态读 foreground：角标可晚于装配 setBadge/清除（setBadge(null) 还原），
 * 显隐/缩放动画经 drawable invalidate 流入，帧循环 tick 重绘时生效。
 */
private class ItemForegroundPainter(
    private val item: View,
    private val anchor: View,
    private val tick: IntState,
    private val verticalShiftPx: Float = 0f,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    private val itemLoc = IntArray(2)
    private val anchorLoc = IntArray(2)

    override fun DrawScope.onDraw() {
        tick.intValue
        val foreground = item.foreground ?: return
        if (!foreground.isVisible) return
        val anchorWidth = anchor.width
        if (anchorWidth <= 0 || anchor.height <= 0 || !anchor.isShown) return
        item.getLocationOnScreen(itemLoc)
        anchor.getLocationOnScreen(anchorLoc)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        // 下沉修正在最外层（缩放前）= 设备像素空间的最终位移
        canvas.translate(0f, verticalShiftPx)
        val scale = size.width / anchorWidth
        canvas.scale(scale, scale)
        // icon 相对条目的屏幕位移差 = 条目本地偏移（条目自身平移在两侧相消）
        canvas.translate(-(anchorLoc[0] - itemLoc[0]).toFloat(), -(anchorLoc[1] - itemLoc[1]).toFloat())
        foreground.draw(canvas)
        canvas.restore()
    }
}

/**
 * WHOLE_TAB_FULL 整条目镜像 Painter：**1:1 原尺寸**绘制，绝不缩放——
 * 玻璃槽位（barWidth/tab 数宽 × 栏高−8dp）与原生条目（屏宽/tab 数 × 应用栏高）
 * 宽高比必然不同，fit 缩放会双向不等比拉伸（微博实测图标/文字变形）；默认居中：
 * 原生条目内容居中，1:1 居中后超出槽位的只有条目自身的空白边，视觉无裁切。
 * [alignBottom]（descriptor 声明，默认 false = 居中，既有应用逐位不变）：原生条目
 * 内容贴底的条目改底对齐——条目底边贴槽位底边，超出槽位的只剩条目顶部空白边
 * （芒果TV：条目 80dp 内容在底部 51dp，居中会把标签裁出槽位）。
 */
private class LiveTabFullPainter(
    private val view: View,
    private val tick: IntState,
    private val alignBottom: Boolean = false,
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        tick.intValue
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || !view.isShown) return
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        val offsetY = if (alignBottom) size.height - vh else (size.height - vh) / 2f
        canvas.translate((size.width - vw) / 2f, offsetY)
        view.draw(canvas)
        canvas.restore()
    }
}
