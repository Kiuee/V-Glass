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
    /** 诊断日志出口（dedupe 开启时输出录制耗时/回退事件）。 */
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

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val tickValue = tick()
        val coordinates = coordinates ?: return
        val node = bandNode
        if (node != null && !dedupeDisabled && dedupe()) {
            if (recordedTick != tickValue) {
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

    /** 全宽条带录制：内容坐标系，节点本地 (0,0) = 内容点 (0, bandTop−margin)。 */
    private fun DrawScope.recordBand(node: android.graphics.RenderNode, coordinates: LayoutCoordinates) {
        if (!enableLogged) {
            enableLogged = true
            log?.invoke("band dedupe enabled (full-width strip)")
        }
        val started = SystemClock.elapsedRealtimeNanos()
        // 条带高度 = 当前消费者条带 + 上下 8dp 边距（主层与副本/液泡条带垂直错位 4dp）
        val bandHeight = size.height.toInt() + with(density) { 16.dp.toPx().toInt() }.coerceAtLeast(1)
        val marginPx = bandHeight - size.height.toInt()
        val viewPos = viewPosition()
        val glassPos = coordinates.positionInWindow()
        val bandTop = (glassPos.y - viewPos.y).toInt() - marginPx / 2
        val width = view.width.coerceAtLeast(1)
        node.setPosition(0, bandTop, width, bandTop + bandHeight)
        val canvas = node.beginRecording(width, bandHeight)
        try {
            canvas.clipRect(0f, 0f, width.toFloat(), bandHeight.toFloat())
            canvas.translate(0f, -bandTop.toFloat())
            view.draw(canvas)
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
