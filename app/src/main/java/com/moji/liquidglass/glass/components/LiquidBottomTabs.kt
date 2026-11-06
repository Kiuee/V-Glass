package com.moji.liquidglass.glass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.moji.liquidglass.glass.LayerScaleScope
import com.moji.liquidglass.glass.utils.DampedDragAnimation
import com.moji.liquidglass.glass.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * 一比一移植自 Kyant0/AndroidLiquidGlass（kmp 分支）
 * app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 * 仅改包名与工具类导入，所有样式参数、动画曲线、层级结构保持原样。
 * 差异：barHeight/blurRadius/accentColor 抽为可选参数；新增 overlayContent 覆盖层
 * （渲染在液泡之上，用于角标等不应被液泡折射着色的元素，几何与折射副本层一致）；
 * 新增 onPreviewTabChanged（拖动中液泡悬停 tab 变化回调，供宿主做选中预览）；
 * 新增 onSelectedTabTapped（原地点击已选中 tab 回调——液泡手势层盖住内容层，
 * 该点击不会到达 LiquidBottomTab，由宿主转发实现原生"再点一次已选中 tab"行为）；
 * 新增 tintRefractionContent（折射副本层是否整层强调色 tint：LOTTIE_RESOLVE 与 WHOLE_TAB
 * 两模式均开启——选中/悬停 tab 在液泡内呈强调色，观感全局一致）。
 * 偏离上游 1:1（2026-10-23）：折射副本行（第二行 Row）四周外扩 2×blurRadius（required*
 * 尺寸）+ 盒高固定——用于胶囊裁剪时代的淡出带规避。**2.9.12 回退**：采样改条带矩形后
 * 淡出带整类消失，外扩失去存在原因；且副本行节点 = 节点高 + 2×(2×blur+14dp×量倍数)
 * **随模糊半径无界增长**（blur 24 时 176dp，盒/ComposeView/窗口全被撑溢）→ 溢出部分
 * 被窗口画布裁空（2.9.1 实证）= 磨砂透明条带 ∝ 半径、全套采样几何 ∝ 半径 = "位移随
 * 模糊半径增大"。2.9.12 恢复上游结构：副本行 = 液泡同界（无外扩），模糊外扩回归
 * **库效果层对称外扩**（效果纹理 = 节点 ± 半径，条带边距 ≥ 半径保证覆盖），盒高 wrap
 * （最高子项 = 主栏）——结构尺寸与模糊半径完全无关（上游即此，底栏不随半径移动）。
 * raw 腿/折射腿的采样补偿标定拨盘已完成标定使命并移除（2.9.16，默认参数即正确）。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 60f.dp,
    blurRadius: Dp = 6f.dp,
    /** 玻璃表面色不透明度（0~1）：叠在模糊内容上的底色浓度，0 = 纯透明玻璃。 */
    surfaceAlpha: Float = 0.2f,
    /** 色彩饱和度（vibrancy = colorControls(saturation)）：模糊内容的鲜艳度。 */
    saturation: Float = 1.5f,
    /** 折射量：玻璃边缘与液泡 lens 位移幅度（shader refractionAmount）的缩放系数，
     *  0 = 无折射平边。默认 1（100%）。 */
    refractionAmountScale: Float = 1f,
    /** 折射高度：折射带宽（边缘向内发生折射的距离，shader refractionHeight）的缩放系数，
     *  0 = 无折射平边。默认 0.35（35%）。 */
    refractionHeightScale: Float = 0.35f,
    accentColor: Color? = null,
    onPreviewTabChanged: ((Int) -> Unit)? = null,
    onSelectedTabTapped: ((Int) -> Unit)? = null,
    /** 拖动结束且气泡停在原选中 tab（索引未变）时回调：宿主据此还原悬停预览
     *  （恢复各 tab 原图标/选中态）。始终先于 onSelectedTabTapped 调用。 */
    onPreviewEnded: (() -> Unit)? = null,
    tintRefractionContent: Boolean = true,
    content: @Composable RowScope.() -> Unit,
    overlayContent: (@Composable RowScope.() -> Unit)? = null
) {
    val isLightTheme = !isSystemInDarkTheme()
    val resolvedAccentColor =
        accentColor ?: if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = surfaceAlpha)
        else Color(0xFF121212).copy(alpha = surfaceAlpha)

    val tabsBackdrop = rememberLayerBackdrop()
    // 副本行坐标（液泡组合采样源的对齐基准；见 BubbleCombinedBackdrop）
    var copyRowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    BoxWithConstraints(
        // 盒高 wrap（上游同款）：最高子项 = 主栏（barHeight），副本行/液泡同界且更矮、
        // 居中——**结构尺寸与模糊半径完全无关**（模糊外扩回归库效果层对称外扩，见类
        // KDoc 2.9.12 回退说明），底栏/背景不随模糊滑杆移动。
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // 拖动距离跟踪：区分"原地点击已选中 tab"（转发原生行为）与"真实拖动"
        val dragMoved = remember { mutableStateOf(false) }
        val dragDistancePx = remember { mutableStateOf(0f) }
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            val tapThresholdPx = with(density) { 12f.dp.toPx() }
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {
                    dragMoved.value = false
                    dragDistancePx.value = 0f
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    // 气泡停在原选中 tab：先收敛预览（恢复原图标/选中态），再视手势转发
                    // 原地点击（应用自身行为，如回顶+刷新/重播选中动画）
                    if (targetIndex == currentIndex) {
                        onPreviewEnded?.invoke()
                    }
                    // 原地点击已选中 tab：索引未变时 snapshotFlow 不发射，需显式回调
                    // （宿主转发 performClick → 应用"再点一次已选中 tab"行为，如回顶+刷新）
                    if (targetIndex == currentIndex && !dragMoved.value) {
                        onSelectedTabTapped?.invoke(targetIndex)
                    }
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    dragDistancePx.value += abs(dragAmount.x)
                    if (dragDistancePx.value > tapThresholdPx) dragMoved.value = true
                    onPreviewTabChanged?.invoke(
                        targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 拖动悬停预览下标：targetValue ≠ currentIndex 时为悬停 tab，松手 settle / 拖回原
        // tab 自动归 -1——与宿主 previewWholeTab/previewTabChange 同一语义来源，内容层
        // （标签强调色等）据此绘制悬停反馈
        val previewIndex = remember {
            derivedStateOf {
                val target = dampedDragAnimation.targetValue.fastRoundToInt()
                if (target != currentIndex) target else -1
            }
        }
        CompositionLocalProvider(LocalTabPreviewIndex provides previewIndex) {
        // 液泡覆盖槽位判定（suppressBubbleCoveredSlots 的数据源，默认无人消费 = 零影响）：
        // 与下方液泡 graphicsLayer 同一动画状态源——中心距 = |槽位 − 液泡中心（含
        // panelOffset 弹性平移）| < 半槽宽×scaleX（含速度拉伸）+ 图标半宽余量（0.15 槽宽）。
        // 槽位中心 i+0.5、液泡中心 value+0.5+panelOffset/tabWidth（单位 = 槽宽）。
        val bubbleCovered: (Int) -> Boolean = { index ->
            val bubbleScaleX = dampedDragAnimation.scaleX /
                (1f - (dampedDragAnimation.velocity / 10f * 0.75f).fastCoerceIn(-0.2f, 0.2f))
            abs(
                index - dampedDragAnimation.value - panelOffset / tabWidth
            ) < 0.5f * bubbleScaleX + 0.15f
        }
        CompositionLocalProvider(LocalLiquidBottomTabBubbleCovered provides bubbleCovered) {
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            colorControls(saturation = saturation)
                            blur(blurRadius.toPx())
                            lens(
                                24f.dp.toPx() * refractionHeightScale,
                                24f.dp.toPx() * refractionAmountScale
                            )
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                .then(interactiveHighlight.modifier)
                .height(barHeight)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            },
            // 折射副本行**不受液泡覆盖槽位抑制**（恒 false）：抑制只应隐藏可见行的
            // 原位镜像（防按压重影），折射副本行是液泡 lens 的采样源——若一并抑制，
            // 液泡滑到两槽之间时可见行与副本同时为空 = 图标整段消失（suppress
            // 声明方小黑盒真机实证）。默认无人消费 = 零影响（§0.4）。
            LocalLiquidBottomTabBubbleCovered provides { false },
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .onGloballyPositioned { copyRowCoordinates = it }
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            colorControls(saturation = saturation)
                            blur(blurRadius.toPx())
                            // 无 lens（2.9.14 移除，上游有但实际不可见）：副本行 2.9.12 起
                            // = 液泡同界，其 lens 折射带正好落在液泡边缘——上游 blur 固定
                            // 8dp < 最大外拉 24dp×p，折射带采样越界透明 → 恒塌落到 raw 腿
                            // 从未可见；本模块 blur 可调大（≥24dp 时全覆盖）→ 24dp 折射位
                            // 移完整显形 = "悬停折射背景位移"。磨砂层退化为纯磨砂，液泡
                            // 边缘折射观感由液泡自身 lens 承担（下方液泡 Box）。
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    // 上游同款（2.9.12 回退节点外扩）：副本行 = 液泡同界（栏高−8dp ×
                    // 全宽），模糊外扩回归库效果层对称外扩（效果纹理 = 节点 ± blur，
                    // 条带边距 max(8dp, clipOutset) ≥ blur 保证覆盖）——节点尺寸与模糊
                    // 半径无关，不再溢出盒/窗口被裁（2.9.1 实证的空带源）。
                    .height(barHeight - 8f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(
                        colorFilter = if (tintRefractionContent) {
                            ColorFilter.tint(resolvedAccentColor)
                        } else {
                            null
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = remember(backdrop, tabsBackdrop) {
                        BubbleCombinedBackdrop(backdrop, tabsBackdrop) { copyRowCoordinates }
                    },
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 效果层采样环：液泡自身无 blur（磨砂来自下方折射副本行录制层），
                        // 而 Kyant lens 只消耗、从不抬升 padding（库 effects/Lens.kt:25：
                        // `padding = (padding − refractionHeight) → 0`）——效果层退化为
                        // 液泡 bounds 本体、没有外扩环，lens 位移与色散（chromaticAberration
                        // 沿 grad 双向偏移）越出 bounds 的采样命中空层 = 液泡边缘透明
                        // （2.9.0 及以前所有版本的本体 bug）。环宽 = lens 最大位移
                        // （14dp×量倍数×progress）+ lens 自身消耗的折射高度（10dp×高倍数
                        // ×progress，2.9.14 补：旧公式漏此项，满按差 ~3.5dp）+ 色散余量
                        // 2dp；环的内容 = combined：raw（ClippedViewBackdrop clipOutset
                        // 已外扩）+ 副本磨砂（副本行 = 液泡同界，环超出副本界的部分落
                        // raw = 物理正确兜底，上游同款）。
                        val ring = 14f.dp.toPx() * refractionAmountScale * progress +
                            10f.dp.toPx() * refractionHeightScale * progress + 2f.dp.toPx()
                        if (padding < ring) padding = ring
                        lens(
                            10f.dp.toPx() * refractionHeightScale * progress,
                            14f.dp.toPx() * refractionAmountScale * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(barHeight - 8f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        overlayContent?.let { overlay ->
            // 角标"固定在玻璃表面"：应用与玻璃栏 Row 逐字一致的按压缩放 + panelOffset
            // 平移，角标像贴在玻璃面上随表面等比缩放移位（不做独立动画）
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .graphicsLayer {
                            translationX = panelOffset
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                        .height(barHeight - 8f.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = overlay
                )
            }
        }
        }
    }
}

/**
 * 液泡组合采样源（世界锚定）：raw 条带采样（ClippedViewBackdrop，自带 layerBlock 逆
 * 缩放）+ 折射副本行录制层。两层均以 layerBlock 逆缩放抵消正向缩放（**节点中心为轴**，
 * 与正向缩放轴点一致——正向经普通 `Modifier.graphicsLayer` 施加，Compose 默认
 * TransformOrigin.Center = layout 尺寸中点，androidx 源码实锤；**轴点取
 * coordinates.size/2，不得取 DrawScope.size/2**——效果层 = 节点 ± padding，DrawScope
 * 原点经库 translate(padding,padding) 对齐节点左上，size/2 = 节点中心 + padding）——
 * 不施加逆补偿时，按压/悬停缩放（k≈1.39）使采样内容随玻璃缩放偏移，压在玻璃 80% 透明
 * 可见的真实内容（栏体自身图标/标签）旁 = 图标/标签重影 + 内容错位（2.9.2 真机实证）。
 * **轴点不一致的残差 = (1−k)×轴点差**：顶左轴（2.9.6~2.9.8，含库 InverseLayerScope
 * 同款顶左——其纯色 backdrop demo 漂移不可见）残差 −(k−1)×节点中心：液泡磨砂上左漂
 * ~10dp、相对栏体 ~9dp = 边界磨砂断层 + 采样窗口越界透明条带；DrawScope.size/2 轴
 * （2.9.9 误修）残差 +(k−1)×padding = 右下位移 ∝ 模糊半径、悬停随液泡 ring 增大
 * （2.9.9 真机"折射背景右下位移"）；2.9.5"正向=顶左"系当时实验帧/枢轴混淆所误推，
 * 2.9.9/2.9.10 两轮修订。**算子序（2.9.13 修订）= 库 LayerBackdrop 同款：逆缩放在前
 * 声明、对齐平移在后声明**（先声明 = 外层 → 平移在缩放内；2.9.11 曾误统一为"平移在
 * 前" = 平移在缩放外，残差 (1−1/k)×对齐量，双腿同错）。轴点一致 → 精确抵消，采样内容
 * 世界锚定（官方教程语义 "content will scale but the backdrop won't scale"）。
 *
 * 对齐平移用副本行 positionInWindow 差值（变换感知；副本行内层 panelOffset 弹性平移
 * 属于录制内容自身，随录制走 = 拖动弹性时折射副本随动，与库行为一致）。
 */
private class BubbleCombinedBackdrop(
    private val raw: Backdrop,
    private val layer: LayerBackdrop,
    private val layerCoordinates: () -> LayoutCoordinates?,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        // layerBlock 缩放读数（size/density 先设——layerBlock 读 size/dp.toPx，裸实例
        // = NaN → scale(NaN) → 画布矩阵 NaN 整层不绘制，2.9.3 回归实证）。**读回尺寸 =
        // coordinates.size（节点 layout 尺寸）**：正向 layerBlock 在 graphicsLayer 里
        // 读到的 size = 节点尺寸，正/读两侧同源（2.9.10 前误用 DrawScope.size =
        // 节点+2×padding，含效果层 padding）。
        var layerScaleX = 1f
        var layerScaleY = 1f
        if (layerBlock != null) {
            layerScope.size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            layerScope.density = density.density
            layerScope.fontScale = density.fontScale
            layerScope.scaleX = 1f
            layerScope.scaleY = 1f
            layerScope.layerBlock()
            layerScaleX = layerScope.scaleX
            layerScaleY = layerScope.scaleY
            if (!layerScaleX.isFinite() || layerScaleX <= 0f) layerScaleX = 1f
            if (!layerScaleY.isFinite() || layerScaleY <= 0f) layerScaleY = 1f
        }
        // raw 条带采样（layerBlock 逆缩放由 ClippedViewBackdrop 自行施加，同轴同构）
        with(raw) { drawBackdrop(density, coordinates, layerBlock) }
        // 副本录制层消费：**库 LayerBackdrop.drawBackdrop 逐字对齐（catalog 同款）**——
        // scale(1/s, Offset.Zero) 在前声明（外层）、translate(-offset) 在后声明（内，
        // 平移在缩放内）；轴点 = 画布原点（非节点中心，2.9.13 曾误取）；offset = 消费者
        // 在副本行坐标系中的位置（localPositionOf 优先，异常回退 positionInWindow 差）。
        val layerCoords = layerCoordinates() ?: return
        val offset = try {
            layerCoords.localPositionOf(coordinates)
        } catch (_: Exception) {
            coordinates.positionInWindow() - layerCoords.positionInWindow()
        }
        withTransform({
            scale(1f / layerScaleX, 1f / layerScaleY, Offset.Zero)
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(layer.graphicsLayer)
        }
    }

    private val layerScope = LayerScaleScope()
}
