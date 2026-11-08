package com.moji.liquidglass.glass

import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 条带裁剪的内容视图 Backdrop：玻璃只采样底栏条带区域，绘制时把应用内容视图裁剪到
 * **条带矩形**（四向 = 条带边距）后直接画进玻璃画布。
 *
 * 为什么不用库的 layerBackdrop：LayerBackdropNode 是 DrawModifierNode，内容视图每次
 * 失效都会把整个首页重录制一遍——首页顶端下拉（二楼）时动画+列表同时失效，每帧多次
 * 全屏重录制直接打爆帧预算，表现为顶端滑动卡死、中部正常。裁剪后每帧只绘制一条
 * 条带，成本约为全屏的 1/10，实时折射保留。
 *
 * 裁剪为矩形而非胶囊形（2026-11-06 起；历史上曾为胶囊形）：胶囊裁剪边界内侧会因
 * blur 的高斯混合产生 ~1-2 个模糊半径的淡出带（玻璃边缘透明/发虚，液泡折射采样取空
 * ——衍生出外扩/采样环等整族补丁）；矩形下内容在效果层外扩环内**完整填充**、淡出带
 * 整类消失。可见输出由库 layout 层（`placeWithLayer` clip=true + shape）遮罩回节点
 * 形状，矩形采样区不外泄（外扩采样 24dp 实证无色带）。
 *
 * layerBlock 逆变换（世界锚定采样）：玻璃 layer 的正向缩放（layerBlock，如液泡按压
 * 1.39×+速度拉伸）会缩放整块效果输出——消费侧施加其逆缩放，采样背景不随玻璃变形。
 * **逆缩放轴点 = 正向缩放轴点 = 节点中心**：正向经普通 `Modifier.graphicsLayer(layerBlock)`
 * 施加，不设 transformOrigin → Compose 默认 `TransformOrigin.Center`（androidx 源码实锤：
 * GraphicsLayerScope 默认 Center、渲染侧 pivotOffset = pivotFraction×layerSize）= layout
 * 尺寸中点。**轴点必须取 `coordinates.size/2`（节点 layout 尺寸）而非 DrawScope.size/2**：
 * 效果层 = 节点 ± padding（blur/lens 抬升），DrawScope.size = 节点 + 2×padding，而
 * DrawScope 原点经库 `translate(padding,padding)` 已对齐节点左上——DrawScope.size/2 =
 * 节点中心 + padding（2.9.9 即误用此处 → 残差 (k−1)×padding 右下位移 ∝ 模糊半径、悬停
 * 随液泡 ring 增大，真机"折射背景右下位移"反向证实）。轴点不一致残差 = (1−k)×轴点差：
 * 顶左轴（2.9.6~2.9.8）残差 −(k−1)×节点中心 = 液泡磨砂上左漂（"悬停上移"）、相对栏体
 * 差 ~9dp = 边界断层 + 采样越界透明条带；2.9.5"正向=顶左"系当时实验帧/枢轴混淆所误推
 * （2.9.9 一度改回"画布中心"同样错）；库 InverseLayerScope 自身顶左轴，其纯色 backdrop
 * demo 漂移不可见，非规范（见 [applyInverseLayerScale]/[drawBackdrop]）。
 *
 * 采样去重（[dedupe]，默认开；TargetHook 会话创建期按配置统一设置）：液泡/主层/折射
 * 副本三个玻璃节点各自直绘 = 每帧 3 次全树 view.draw（"全屏采样"级开销，GL/TextureView
 * 内容页更是整屏内容重绘）。开启后每 tick 只把**全宽条带**录制进一条
 * RenderNode（内容坐标系），各节点回放时按当次消费者的 viewPos/glassPos 对齐、用自己
 * 的条带矩形裁剪——与直绘逐像素等价，全树遍历 3 次/帧 → 1 次/帧。
 *
 * 采样源边界镜像（**已移除**，2026-11-08）：2.9.16 squash 曾引入 extendBeyondContent
 * （录制期把内容视图底/顶边界外的条带空区用翻转回放填满，修贴屏底模糊淡出带，2.9.7
 * 真机 24dp 实证场景）——真机未见对目标症状生效（液泡边缘透明真修复 = 液泡 effects
 * 抬效果层采样环，§1.2），且镜像把含 native 消费 TextureLayer 的内容算子在特效层内
 * **翻转回放 2 次**（执行 1→3 次），与高德地图引擎渲染冲突致死（整页黑屏，№05.7 教训
 * 9）。整体移除回归单节点直录；默认 blur 6dp 下淡出带起点在全部可见采样区之外，
 * 移除无视觉代价（大模糊档下缘淡出带回归，随样式档复验）。
 *
 * 实现要点（上次失败版的三处修正）：① 录制不含任何消费者的采样窗口（液泡随拖动平移，
 * 窗口各不相同，只有全宽条带能一次覆盖）；② 条带原点 = glassPos − viewPos（内容坐标系，
 * 注意与画布平移方向 viewPos − glassPos 相反）；③ 条带高度加边距——主层条带
 * （barHeight）与副本/液泡条带（barHeight−8dp）垂直错位 4dp，边距保证互不越界。
 */
class ClippedViewBackdrop(
    private val view: View,
    /** 每帧递增的 tick：在 draw 阶段读取以触发玻璃节点逐帧重绘（实时采样滚动内容）。 */
    private val tick: () -> Int,
    /** 内容视图在屏幕中的原点（getLocationOnScreen）。 */
    private val viewPosition: () -> Offset,
    /** 采样去重开关（默认 { false } = 各自直绘，历史行为）。 */
    private val dedupe: () -> Boolean = { false },
    /** 实时显示列表采样（默认 { false } = 每帧 `view.draw()` 重编码，历史行为；由
     *  GlassSession.backdropLiveNodeSampling 注入，仅相册声明）。true 时去重路径不再
     *  重画内容树，改为引用内容视图自身已录制的 RenderNode——主线程每帧零重编码，
     *  条带内容仍是应用当帧录制的显示列表（无降频滞后）；取不到 RenderNode 时自动
     *  回退 `view.draw()`。 */
    private val liveNodeSampling: () -> Boolean = { false },
    /** 采样冻结门控（默认 { false } = 不冻结；声明式通道 backdropFreezeGate 经 GlassRoot
     *  注入）。true = 不对 [view] 做逐帧外部 draw：**进入冻结时一次性录制**条带进
     *  bandNode，冻结期间纯回放（零外部 draw）——外部高频 view.draw 会打乱 WebView 等
     *  自绘渲染树的合成（整页闪白）。录制失败/无 bandNode（API<29）时冻结帧不绘制
     *  （透明，极端降级）。 */
    private val freeze: () -> Boolean = { false },
    /** 强制重录制请求（resume 唤醒经 GlassSession.invalidateBackdrop 注入）：true 时本帧
     *  无视 recordedTick 强制录制一次（冻结帧 = 单次外部 draw 后继续纯回放；去重帧 =
     *  多录一次）。窗口 Surface 重建（后台切换）会丢弃 bandNode 已录制的显示列表，
     *  而玻璃缓存图层按引用回放它——不重录制则表现为"玻璃轮廓在、内容空"。 */
    private val forceRecord: () -> Boolean = { false },
    /** 采样外扩量（px，主线程每帧读取）：条带边距与采样裁剪向外放宽的量。库（Kyant
     *  backdrop）为 blur/lens 把效果层录制成 size+2×padding（blur 抬 padding 至模糊半径、
     *  lens 抬至折射量）——采样环必须有真实内容，否则 blur/lens 在玻璃边缘命中透明
     *  （模糊越大淡出环越宽、折射位移取空）。调用方按当前样式取 max(模糊半径, 折射量
     *  上限)（GlassRoot 注入；默认 { 0 } = 紧贴消费者 bounds 的 8dp 基础边距，逐位旧行为）。
     *  可见输出由库 layout 层（clip=true + shape）遮罩回节点形状，外扩不外泄。 */
    private val clipOutset: () -> Float = { 0f },
    /** 条带纵向联合外扩（px，主线程每帧读取，dedupe 共享条带按最大消费者覆盖）：dedupe
     *  开启时条带每 tick 只按"首个绘制消费者"（通常主层）的几何录制一次，其余消费者
     *  回放同一录制——模糊拉满时液泡悬停采样范围（±26dp 槽位半高 + 采样环 16dp + blur
     *  混合半径 24dp）超出主层条带，折射副本行回放得到的磨砂层边缘混入空纹理淡出带内
     *  移（与已移除的采样源边界镜像同根因的"条带版本"）。调用方注入 = 采样外扩量
     *  （GlassRoot；仅 dedupe 开启时生效——各消费者自绘路径条带已自带自覆盖）；内容
     *  坐标系锚定使多录无害（回放按自身裁剪取用）。默认 { 0 } = 旧行为。 */
    private val bandExtraExtent: () -> Float = { 0f },
    /** 诊断日志出口（dedupe/冻结翻转/重录制时输出）。 */
    private val log: ((String) -> Unit)? = null,
    /** 内容采样停用（声明式通道 `GlassSession.backdropSamplingDisabled` 注入）：true =
     *  条带不录制/不回放/不直绘（渲染引擎侧：采样条带在特效层内回放 native 消费
     *  TextureView 的绘制算子会杀死宿主 GL 渲染，见 GlassSession KDoc）。 */
    private val samplingDisabled: () -> Boolean = { false },
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    /** 每 tick 单次录制的全宽条带（内容坐标系）。 */
    private val bandNode: android.graphics.RenderNode? =
        if (Build.VERSION.SDK_INT >= 29) android.graphics.RenderNode("glass-content-band") else null

    /** 条带内容的原始录制（与 [bandNode] 同矩形、位置 (0,0)）：单节点直录（2.7.2 结构）。
     *  历史注记：2.9.16 squash 曾引入 srcNode 双节点 + extendBeyondContent 边界镜像
     *  （修贴屏底模糊淡出带，真机未见生效），且镜像把含 native 消费 TextureLayer 的
     *  内容算子在特效层内翻转回放 2 次、与高德地图引擎渲染冲突致死（整页黑屏），
     *  2026-11-08 整体移除（含声明式禁用通道）回归单节点结构。 */
    private var recordedTick = Int.MIN_VALUE

    /** 录制/回放异常后永久回退直绘（个别画布实现不支持回放嵌套显示列表）。 */
    @Volatile
    private var dedupeDisabled = false

    /** layerBlock 缩放读数复用（draw 主线程单线程；size/density 可写覆盖，运行
     *  layerBlock 前由 [drawBackdrop] 设置——库 InverseLayerScope 同款成员面）。 */
    private val layerScope = LayerScaleScope()

    private var recordCostCount = 0
    private var recordCostSumUs = 0L
    private var enableLogged = false
    @Volatile private var frozen = false

    // —— 实时显示列表采样（liveNodeSampling）状态 ——
    /** 内容视图自身的 RenderNode（隐藏 API，API 29+）：取不到即为 null → 回退 view.draw。 */
    private val renderNodeField: java.lang.reflect.Field? by lazy {
        runCatching {
            android.view.View::class.java.getDeclaredField("mRenderNode").apply { isAccessible = true }
        }.getOrNull()
    }
    /** 条带显示列表当前是否引用着实时内容节点（true 时后续 tick 只回放、不重录）。 */
    private var liveBandRecorded = false
    /** 当前条带引用的内容节点（视图重建/换节点时身份变化 → 重录）。 */
    private var recordedLiveNode: android.graphics.RenderNode? = null
    /** live 路径的 tick 标记（与既有路径 recordedTick 相互独立，互不污染回退语义）。 */
    private var liveBandTick = Int.MIN_VALUE
    private var recordedBounds = BandBounds(0, 0, 0)
    private var liveLogged = false
    private var liveFallbackLogged = false

    /** 条带在内容坐标系里的几何（用于判定是否需要重录：栏位移/旋转/inset 变化会变）。 */
    private class BandBounds(val top: Int, val width: Int, val height: Int) {
        override fun equals(other: Any?): Boolean =
            other is BandBounds && other.top == top && other.width == width && other.height == height

        override fun hashCode(): Int = (top * 31 + width) * 31 + height
    }

    private fun contentRenderNode(): android.graphics.RenderNode? {
        if (Build.VERSION.SDK_INT < 29) return null
        // 优先取字段（View.mRenderNode），不可用时再试隐藏方法 getRenderNode()；两者
        // 都失败（ROM 隐藏 API 限制）→ 返回 null，由调用方回退 view.draw()。
        val node = runCatching {
            renderNodeField?.get(view) as? android.graphics.RenderNode
        }.getOrNull() ?: runCatching {
            android.view.View::class.java
                .getDeclaredMethod("getRenderNode")
                .apply { isAccessible = true }
                .invoke(view) as? android.graphics.RenderNode
        }.getOrNull() ?: return null
        return node.takeIf { runCatching { it.hasDisplayList() }.getOrDefault(false) }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val tickValue = tick()
        val coordinates = coordinates ?: return
        // 内容采样停用（声明式通道）：条带不录制、不回放、不直绘，玻璃节点只留
        // 表面色/特效（效果层内容为空）
        if (samplingDisabled()) return
        val node = bandNode
        // layerBlock 逆缩放读数：玻璃 layer 的正向缩放（layerBlock，如液泡按压 1.39×+
        // 速度拉伸）会缩放整块效果输出——采样若不施加其逆，泡内内容随玻璃缩放/偏移，
        // 与底栏内容明显错位（双影/位移差/采样越界透明区）。逆变换**同轴**施加（正向轴
        // = graphicsLayer 默认 TransformOrigin.Center = 节点 layout 尺寸中点，轴点取
        // coordinates.size/2，见 [applyInverseLayerScale]；忽略平移/旋转——本模块
        // layerBlock 仅含缩放，ExtraActionButton 的 tanh 平移为已知不做补偿的库同款
        // 边界）。**运行 layerBlock 前必须先设置 size/density/fontScale**（库
        // InverseLayerScope 同款）——layerBlock 读 size/dp.toPx（栏体按压缩放公式），
        // 裸实例 = NaN → scale(NaN) → 画布矩阵 NaN → 整条不绘制（2.9.3 回归实证）。
        // **读回尺寸 = coordinates.size（节点 layout 尺寸）而非 DrawScope.size**：
        // 正向 layerBlock 在 graphicsLayer 里读到的 size = 节点尺寸（不含效果层
        // padding），正/读两侧必须同源——2.9.10 前误用 DrawScope.size（节点+2×padding
        // ∝ 模糊半径）→ 主栏 k 公式读回 k 随模糊半径漂移。scale=1（rest）时为恒等，
        // 既有渲染逐位不变（§0.4）。
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
        // 冻结帧：进入时一次性录制，冻结期间纯回放（零外部 draw）；resume 唤醒强制
        // 重录制一次（Surface 重建丢弃已录制内容，回放前必须刷新）
        if (node != null && freeze()) {
            if (!frozen) {
                frozen = true
                log?.invoke("backdrop freeze on (single-shot band record)")
                val ok = runCatching { recordBand(node, coordinates) }
                if (ok.isSuccess) {
                    recordedTick = tickValue
                } else {
                    log?.invoke("freeze band record failed: ${ok.exceptionOrNull()}")
                }
            } else if (forceRecord()) {
                log?.invoke("backdrop revive (re-record after resume)")
                runCatching { recordBand(node, coordinates) }
                    .onFailure { log?.invoke("freeze band re-record failed: $it") }
            }
            if (recordedTick != Int.MIN_VALUE) {
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    if (nc.isHardwareAccelerated) {
                        runCatching {
                            replay(nc, coordinates, node, layerScaleX, layerScaleY)
                        }.onFailure {
                            log?.invoke("frozen band replay failed: $it")
                        }
                    }
                }
            }
            return
        }
        if (frozen) {
            frozen = false
            log?.invoke("backdrop freeze off (resume live sampling)")
        }
        // 实时显示列表采样（liveNodeSampling，默认关）：条带只在几何变化/唤醒时重录，
        // 其余 tick 纯回放——内容更新由内容视图自身当帧的显示列表自动跟随（零滞后）。
        // 取不到 RenderNode 时回退下面的每 tick 重录路径（两种路径的录制标记相互独立）。
        if (node != null && !dedupeDisabled && dedupe() && liveNodeSampling() &&
            liveBandPath(node, coordinates, tickValue, layerScaleX, layerScaleY)
        ) {
            return
        }
        if (node != null && !dedupeDisabled && dedupe()) {
            if (recordedTick != tickValue || forceRecord()) {
                recordedTick = tickValue
                val ok = runCatching { recordBand(node, coordinates) }
                if (ok.isFailure) {
                    dedupeDisabled = true
                    log?.invoke("band record failed, fallback to direct draw: ${ok.exceptionOrNull()}")
                }
            }
            if (!dedupeDisabled) {
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    if (nc.isHardwareAccelerated) {
                        try {
                            replay(nc, coordinates, node, layerScaleX, layerScaleY)
                        } catch (t: Throwable) {
                            dedupeDisabled = true
                            log?.invoke("band replay failed, fallback to direct draw: $t")
                            drawDirect(nc, coordinates, layerScaleX, layerScaleY)
                        }
                    } else {
                        drawDirect(nc, coordinates, layerScaleX, layerScaleY)
                    }
                }
                return
            }
        }
        drawIntoCanvas { canvas ->
            drawDirect(canvas.nativeCanvas, coordinates, layerScaleX, layerScaleY)
        }
    }

    /** 全宽条带录制：内容坐标系，节点本地 (0,0) = 内容点 (0, bandTop−margin)。
     *  [liveContent] 非 null 时不再 `view.draw()` 重编码内容树，改为引用内容视图自身
     *  已录制的 RenderNode（回放时由 RenderThread 栅格化并裁到条带）。
     *
     *  单节点直录（2.7.2 结构；历史上的 srcNode 双节点 + 边界镜像合成已整体移除，
     *  见类 KDoc"采样源边界镜像"历史注记）。每 tick 仍只有一次内容树遍历（`view.draw`
     *  或 live 引用）。 */
    private fun DrawScope.recordBand(
        node: android.graphics.RenderNode,
        coordinates: LayoutCoordinates,
        liveContent: android.graphics.RenderNode? = null,
    ): BandBounds {
        if (!enableLogged) {
            enableLogged = true
            log?.invoke("band dedupe enabled (full-width strip)")
        }
        val started = SystemClock.elapsedRealtimeNanos()
        val bounds = bandBounds(coordinates)
        node.setPosition(0, bounds.top, bounds.width, bounds.top + bounds.height)
        val canvas = node.beginRecording(bounds.width, bounds.height)
        try {
            canvas.clipRect(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat())
            canvas.translate(0f, -bounds.top.toFloat())
            drawBandContent(canvas, liveContent)
        } finally {
            node.endRecording()
        }
        recordCostSumUs += (SystemClock.elapsedRealtimeNanos() - started) / 1_000
        recordCostCount++
        if (recordCostCount >= 120) {
            log?.invoke("band record: ${recordCostSumUs / recordCostCount}us avg x$recordCostCount")
            recordCostCount = 0
            recordCostSumUs = 0L
        }
        return bounds
    }

    /** 条带内容绘制（内容坐标系：画布已平移 (0, −bandTop)）。玻璃发起的宿主树直绘：
     *  期间被 hook 类的 onDraw 是绘制副作用，抑制信号广播（自激防护，见
     *  GlassSession.signalSuppressed）。 */
    private fun drawBandContent(
        canvas: android.graphics.Canvas,
        liveContent: android.graphics.RenderNode?,
    ) {
        if (liveContent != null) {
            canvas.drawRenderNode(liveContent)
        } else {
            GlassSession.withSignalSuppression { view.draw(canvas) }
        }
    }

    /** 条带几何（内容坐标系）：高度 = 当前消费者条带 + 上下边距（每侧 = [bandPerSideMargin]
     *  + [bandExtraExtent]——8dp 兜底主层与副本/液泡条带的 4dp 垂直错位；外扩生效时边距
     *  随之外扩保证采样环落在条带录制范围内；联合外扩供 dedupe 共享条带按最大消费者覆盖，
     *  内容坐标系锚定使多录无害）；原点 = glassPos − viewPos。 */
    private fun DrawScope.bandBounds(coordinates: LayoutCoordinates): BandBounds {
        val perSideMargin = bandPerSideMargin() + bandExtraExtent()
        val bandHeight = size.height.toInt() + (perSideMargin * 2f).toInt().coerceAtLeast(1)
        val marginPx = bandHeight - size.height.toInt()
        val viewPos = viewPosition()
        val glassPos = coordinates.positionInWindow()
        val bandTop = (glassPos.y - viewPos.y).toInt() - marginPx / 2
        val width = view.width.coerceAtLeast(1)
        return BandBounds(bandTop, width, bandHeight)
    }

    /**
     * 实时显示列表采样路径：首次（或几何变化/唤醒强制）录制一次引用内容节点，之后每 tick
     * 纯回放。返回 true = 本 tick 已处理（调用方直接返回）；false = 本帧不可用，调用方
     * 走既有每 tick 重录路径。
     */
    private fun DrawScope.liveBandPath(
        node: android.graphics.RenderNode,
        coordinates: LayoutCoordinates,
        tickValue: Int,
        layerScaleX: Float,
        layerScaleY: Float,
    ): Boolean {
        if (liveBandTick != tickValue) {
            liveBandTick = tickValue
            val bounds = bandBounds(coordinates)
            val live = contentRenderNode()
            if (live == null) {
                liveBandRecorded = false
                if (!liveFallbackLogged) {
                    liveFallbackLogged = true
                    log?.invoke("band sampling: content render node unavailable, fallback to view.draw")
                }
                return false
            }
            if (!liveBandRecorded || live !== recordedLiveNode || forceRecord() ||
                bounds != recordedBounds
            ) {
                val ok = runCatching { recordBand(node, coordinates, live) }
                if (ok.isSuccess) {
                    liveBandRecorded = true
                    recordedLiveNode = live
                    recordedBounds = bounds
                    if (!liveLogged) {
                        liveLogged = true
                        log?.invoke("band sampling: live content render node (no per-frame content redraw)")
                    }
                } else {
                    liveBandRecorded = false
                    if (!liveFallbackLogged) {
                        liveFallbackLogged = true
                        log?.invoke("band sampling: live record failed, fallback to view.draw: ${ok.exceptionOrNull()}")
                    }
                    return false
                }
            }
        } else if (!liveBandRecorded) {
            return false
        }
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (nc.isHardwareAccelerated) {
                try {
                    replay(nc, coordinates, node, layerScaleX, layerScaleY)
                } catch (t: Throwable) {
                    dedupeDisabled = true
                    log?.invoke("band replay failed, fallback to direct draw: $t")
                    drawDirect(nc, coordinates, layerScaleX, layerScaleY)
                }
            } else {
                drawDirect(nc, coordinates, layerScaleX, layerScaleY)
            }
        }
        return true
    }

    /** 回放：按当次消费者的坐标对齐到内容坐标系，条带矩形（四向 = 条带边距）内的
     *  内容与直绘逐像素一致；layerBlock 缩放经逆变换抵消（见 [drawBackdrop]）。 */
    private fun DrawScope.replay(
        nc: Canvas,
        coordinates: LayoutCoordinates,
        node: android.graphics.RenderNode,
        layerScaleX: Float,
        layerScaleY: Float,
    ) {
        val saveCount = nc.save()
        try {
            clipBandRect(nc)
            // **算子序（库 LayerBackdrop 同款）：逆缩放在前声明、对齐平移在后声明**——
            // 先声明 = 外层、后声明先作用（DrawTransform.scale 分解 T(P);S;T(−P)，androidx
            // 逐字核实）→ 点映射 = P + (c + t − P)/k（对齐平移在缩放**内**，P = 节点中心
            // = 正向 graphicsLayer 缩放轴）→ 残差 = (k−1)/k×(P − 正向轴) = 0。
            // **序不可反**：translate 在前声明（外层）→ 平移在缩放外 → 残差
            // (1−1/k)×(t + P)，t ≈ viewPos − glassPos ≈ −glassPos = 数十 dp 上移
            // （2.9.6~2.9.12 raw 腿持续"悬停上移"的根因，2.9.13 修正）。
            val viewPos = viewPosition()
            val glassPos = coordinates.positionInWindow()
            applyInverseLayerScale(nc, coordinates, layerScaleX, layerScaleY)
            nc.translate(viewPos.x - glassPos.x, viewPos.y - glassPos.y)
            nc.drawRenderNode(node)
        } finally {
            nc.restoreToCount(saveCount)
        }
    }

    /** 条带矩形裁剪（消费者本地坐标，四向 = 条带边距 = max(8dp, 采样外扩量)）。
     *  **不再按胶囊裁剪**：内容在效果层外扩环内完整填充（胶囊裁剪边界内侧的模糊淡出带
     *  整类消失），可见输出由库 layout 层（`placeWithLayer` clip=true + shape）遮罩回
     *  节点形状——外扩采样已实证不外泄（clipOutset 24dp 无色带报告）。 */
    private fun DrawScope.clipBandRect(nc: Canvas) {
        val margin = bandPerSideMargin()
        nc.clipRect(-margin, -margin, size.width + margin, size.height + margin)
    }

    /** 条带每侧边距 = max(8dp, 采样外扩量)：8dp 兜底主层与副本/液泡条带的 4dp 垂直错位；
     *  外扩生效时边距随之外扩，保证采样环落在条带录制范围内。 */
    private fun DrawScope.bandPerSideMargin(): Float =
        maxOf(16f.dp.toPx() / 2f, clipOutset())

    /** layerBlock 逆缩放（**节点中心为轴**，轴点 = `coordinates.size/2`）。
     *  正向缩放轴：`drawBackdrop` 经普通 `Modifier.graphicsLayer` 施加 layerBlock，不设
     *  transformOrigin → Compose 默认 `TransformOrigin.Center` = **layout 尺寸中点**
     *  （androidx 源码实锤：GraphicsLayerScope 默认 Center、渲染侧 pivot =
     *  pivotFraction×layerSize）。**轴点必须取 `coordinates.size`（节点 layout 尺寸）
     *  而非 DrawScope.size**：效果层 = 节点 ± padding（blur/lens 抬升），DrawScope.size
     *  = 节点 + 2×padding——DrawScope 原点经库 `translate(padding,padding)` 已对齐节点
     *  左上，故节点中心在 DrawScope 坐标 = `coordinates.size/2`；2.9.9 误用 DrawScope
     *  size/2（= 节点中心 + padding）→ 残差 = (k−1)×padding，**右下位移 ∝ 模糊半径、
     *  悬停随液泡 ring 增大**（2.9.9 真机"折射背景右下位移"，反向证实本模型）。同轴
     *  精确抵消后残差 = (k−1)×(逆轴 − 正向轴) = 0：顶左轴（2.9.6~2.9.8）残差
     *  −(k−1)×节点中心 = 液泡磨砂上左漂（"悬停上移"）、相对栏体 = 边界断层 + 采样窗
     *  口越界透明条带。2.9.5"正向=顶左"系当时实验帧/枢轴混淆所误推。
     *  scale=1（rest）时无操作。 */
    private fun DrawScope.applyInverseLayerScale(
        nc: Canvas,
        coordinates: LayoutCoordinates,
        layerScaleX: Float,
        layerScaleY: Float,
    ) {
        if (layerScaleX == 1f && layerScaleY == 1f) return
        val nodeWidth = coordinates.size.width.toFloat()
        val nodeHeight = coordinates.size.height.toFloat()
        val pivotX = if (nodeWidth > 0f) nodeWidth / 2f else size.width / 2f
        val pivotY = if (nodeHeight > 0f) nodeHeight / 2f else size.height / 2f
        nc.scale(1f / layerScaleX, 1f / layerScaleY, pivotX, pivotY)
    }

    /** 直绘路径（dedupe 关闭/回退）：与回放同款条带矩形裁剪 + 逆缩放（单次直绘；历史
     *  上的翻转二次直绘边界镜像已随录制期镜像一并移除，见类 KDoc 历史注记）。 */
    private fun DrawScope.drawDirect(
        nc: Canvas,
        coordinates: LayoutCoordinates,
        layerScaleX: Float,
        layerScaleY: Float,
    ) {
        val saveCount = nc.save()
        try {
            clipBandRect(nc)
            // 算子序同 [replay]：逆缩放在前声明（外层）、对齐平移在后声明（内）——
            // 平移在缩放内，残差为零；序不可反（见 replay 注释）。
            val viewPos = viewPosition()
            val glassPos = coordinates.positionInWindow()
            applyInverseLayerScale(nc, coordinates, layerScaleX, layerScaleY)
            nc.translate(viewPos.x - glassPos.x, viewPos.y - glassPos.y)
            // 玻璃发起的宿主树直绘：信号抑制（自激防护，同 recordBand）
            GlassSession.withSignalSuppression { view.draw(nc) }
        } finally {
            nc.restoreToCount(saveCount)
        }
    }
}

/** layerBlock 缩放读数载体：成员面与库 InverseLayerScope 一致（size/density/fontScale
 *  可写——layerBlock 读 size/dp.toPx 时必须先由调用方设置，裸默认 = Unspecified/NaN）。 */
internal class LayerScaleScope : GraphicsLayerScope {
    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f

    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 0f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
}
