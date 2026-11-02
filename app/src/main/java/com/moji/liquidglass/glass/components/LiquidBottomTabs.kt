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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
 * 尺寸，无视父约束——width/height 会被父 max 收敛致外扩失效）、内容区经 padding 锁回
 * 原几何——上游结构里折射副本行与液泡 bounds 完全重合，ClippedViewBackdrop 的消费者
 * 自裁剪 + blur 边界淡出把"近透明带"搬进了胶囊/液泡的可见内部（选中态胶囊背景顶部
 * 透明根因，详见 MAINTENANCE.md §1.2 液泡折射源外扩）。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 64f.dp,
    blurRadius: Dp = 8f.dp,
    /** 玻璃表面色不透明度（0~1）：叠在模糊内容上的底色浓度，0 = 纯透明玻璃。 */
    surfaceAlpha: Float = 0.4f,
    /** 色彩饱和度（vibrancy = colorControls(saturation)）：模糊内容的鲜艳度。 */
    saturation: Float = 1.5f,
    /** 折射强度：玻璃边缘与液泡 lens 位移的缩放系数，0 = 无折射平边。 */
    refractionScale: Float = 1f,
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

    // 折射副本行四周外扩量 = 2×模糊半径：ClippedViewBackdrop 把每个消费者的采样裁剪到
    // 自身胶囊形（防矩形采样色带），blur 的高斯混合在裁剪边界内侧产生 ~1-2 个模糊半径的
    // 淡出带——折射副本行原 bounds 与液泡/胶囊完全重合（同为 barHeight−8dp 居中），胶囊
    // 顶边正好压在淡出带上（顶部磨砂近透明、原始内容透出）。外扩后淡出带落在胶囊采样区
    // 之外；同时 ≥ 液泡 lens 最大外向位移（refractionAmount = 14dp·progress），按压/拖动
    // 时边缘不再取样到空环。内容区经 padding 锁回原几何（见下方折射副本行）。
    val refractionCopyOverdraw = blurRadius * 2f

    BoxWithConstraints(
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
                                24f.dp.toPx() * refractionScale,
                                24f.dp.toPx() * refractionScale
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
                            lens(
                                24f.dp.toPx() * refractionScale * progress,
                                24f.dp.toPx() * refractionScale * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    // 外扩尺寸 + 补偿 padding：磨砂录制区超出液泡采样范围，内容区（图标/
                    // 标签/clickable）锁回原几何 barWidth−8dp × barHeight−8dp 原位居中。
                    // 必须用 required*：width/height 是优先尺寸（enforceIncoming），会被
                    // 父容器 max 约束收敛（父 Box 按 barWidth 定宽）——外扩静默失效而补偿
                    // padding 照常生效，内容区被挤偏/变窄（2026-10-23 真机回归根因）
                    .requiredHeight(barHeight - 8f.dp + refractionCopyOverdraw * 2)
                    .requiredWidth(maxWidth + refractionCopyOverdraw * 2)
                    .padding(horizontal = 4f.dp + refractionCopyOverdraw, vertical = refractionCopyOverdraw)
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
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * refractionScale * progress,
                            14f.dp.toPx() * refractionScale * progress,
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
