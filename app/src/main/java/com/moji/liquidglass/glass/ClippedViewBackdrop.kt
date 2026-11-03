package com.moji.liquidglass.glass

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 胶囊裁剪的内容视图 Backdrop：玻璃只采样底栏胶囊区域，绘制时把应用内容视图
 * 裁剪到**胶囊形**（圆角矩形，radius=条带高/2）后直接画进玻璃画布。
 *
 * 为什么不用库的 layerBackdrop：LayerBackdropNode 是 DrawModifierNode，内容视图每次
 * 失效都会把整个首页重录制一遍——首页顶端下拉（二楼）时动画+列表同时失效，每帧多次
 * 全屏重录制直接打爆帧预算，表现为顶端滑动卡死、中部正常。裁剪后每帧只绘制一条
 * 胶囊条带，成本约为全屏的 1/10，实时折射保留。
 *
 * 裁剪必须是胶囊形而非矩形：矩形采样区域会被 vibrancy/blur 效果整体调色，
 * 在胶囊外的矩形边缘显形为"一圈色带"。
 *
 * 忽略 layerBlock 逆变换：按压时玻璃节点仅放大约 1.02 倍（16dp/宽度），采样内容不
 * 随之缩放，视觉差异可忽略。
 *
 * 采样去重（[dedupe]，默认关；由适配器经 GlassSession 按配置打开）：液泡/主层/折射
 * 副本三个玻璃节点各自直绘 = 每帧 3 次全树 view.draw，对 GL 渲染的内容页（相册照片
 * 网格，采样即触发 GL 绘制）开销巨大。开启后每 tick 只把**全宽条带**录制进一条
 * RenderNode（内容坐标系），各节点回放时按当次消费者的 viewPos/glassPos 对齐、用自己
 * 的胶囊裁剪——与直绘逐像素等价，全树遍历 3 次/帧 → 1 次/帧。
 *
 * 实现要点（上次失败版的三处修正）：① 录制不含任何消费者的采样窗口（液泡随拖动平移，
 * 窗口各不相同，只有全宽条带能一次覆盖）；② 条带原点 = glassPos − viewPos（内容坐标系，
 * 注意与画布平移方向 viewPos − glassPos 相反）；③ 条带高度加 8dp 边距——主层条带
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
    /** 诊断日志出口（dedupe/冻结翻转/重录制时输出）。 */
    private val log: ((String) -> Unit)? = null,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    /** 每 tick 单次录制的全宽条带（内容坐标系）。 */
    private val bandNode: android.graphics.RenderNode? =
        if (Build.VERSION.SDK_INT >= 29) android.graphics.RenderNode("glass-content-band") else null
    private var recordedTick = Int.MIN_VALUE

    /** 录制/回放异常后永久回退直绘（个别画布实现不支持回放嵌套显示列表）。 */
    @Volatile
    private var dedupeDisabled = false

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
        val node = bandNode
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
                        runCatching { replay(nc, coordinates, node) }
                            .onFailure {
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
            liveBandPath(node, coordinates, tickValue)
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
                            replay(nc, coordinates, node)
                        } catch (t: Throwable) {
                            dedupeDisabled = true
                            log?.invoke("band replay failed, fallback to direct draw: $t")
                            drawDirect(nc, coordinates)
                        }
                    } else {
                        drawDirect(nc, coordinates)
                    }
                }
                return
            }
        }
        drawIntoCanvas { canvas -> drawDirect(canvas.nativeCanvas, coordinates) }
    }

    /** 全宽条带录制：内容坐标系，节点本地 (0,0) = 内容点 (0, bandTop−margin)。
     *  [liveContent] 非 null 时不再 `view.draw()` 重编码内容树，改为引用内容视图自身
     *  已录制的 RenderNode（回放时由 RenderThread 栅格化并裁到条带）。 */
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
            if (liveContent != null) {
                canvas.drawRenderNode(liveContent)
            } else {
                view.draw(canvas)
            }
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

    /** 条带几何（内容坐标系）：高度 = 当前消费者条带 + 上下 8dp 边距（主层与副本/液泡
     *  条带垂直错位 4dp，边距保证互不越界）；原点 = glassPos − viewPos。 */
    private fun DrawScope.bandBounds(coordinates: LayoutCoordinates): BandBounds {
        val bandHeight = size.height.toInt() + with(density) { 16.dp.toPx().toInt() }.coerceAtLeast(1)
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
                    replay(nc, coordinates, node)
                } catch (t: Throwable) {
                    dedupeDisabled = true
                    log?.invoke("band replay failed, fallback to direct draw: $t")
                    drawDirect(nc, coordinates)
                }
            } else {
                drawDirect(nc, coordinates)
            }
        }
        return true
    }

    /** 回放：按当次消费者的坐标对齐到内容坐标系，胶囊内的条带区域与直绘逐像素一致。 */
    private fun DrawScope.replay(nc: Canvas, coordinates: LayoutCoordinates, node: android.graphics.RenderNode) {
        val saveCount = nc.save()
        try {
            // 胶囊形裁剪（消费者本地坐标，radius=高/2 与组件 Capsule 形状一致）
            val capsule = RectF(0f, 0f, size.width, size.height)
            val radius = size.height / 2f
            val capsulePath = Path().apply {
                addRoundRect(capsule, radius, radius, Path.Direction.CW)
            }
            nc.clipPath(capsulePath)
            // 平移对齐：消费者本地 (0,0) → 内容视图坐标系；节点按自身位置（内容坐标）落位
            val viewPos = viewPosition()
            val glassPos = coordinates.positionInWindow()
            nc.translate(viewPos.x - glassPos.x, viewPos.y - glassPos.y)
            nc.drawRenderNode(node)
        } finally {
            nc.restoreToCount(saveCount)
        }
    }

    /** 直绘路径（dedupe 关闭/回退）：与历史行为一致。 */
    private fun DrawScope.drawDirect(nc: Canvas, coordinates: LayoutCoordinates) {
        val saveCount = nc.save()
        try {
            val capsule = RectF(0f, 0f, size.width, size.height)
            val radius = size.height / 2f
            val capsulePath = Path().apply {
                addRoundRect(capsule, radius, radius, Path.Direction.CW)
            }
            nc.clipPath(capsulePath)
            val viewPos = viewPosition()
            val glassPos = coordinates.positionInWindow()
            nc.translate(viewPos.x - glassPos.x, viewPos.y - glassPos.y)
            view.draw(nc)
        } finally {
            nc.restoreToCount(saveCount)
        }
    }
}
