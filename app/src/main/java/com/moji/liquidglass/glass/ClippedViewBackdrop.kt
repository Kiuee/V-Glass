package com.moji.liquidglass.glass

import android.graphics.RectF
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
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
 */
class ClippedViewBackdrop(
    private val view: View,
    /** 每帧递增的 tick：在 draw 阶段读取以触发玻璃节点逐帧重绘（实时采样滚动内容）。 */
    private val tick: () -> Int,
    /** 内容视图在屏幕中的原点（getLocationOnScreen）。 */
    private val viewPosition: () -> Offset,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        tick()
        val coordinates = coordinates ?: return
        val viewPos = viewPosition()
        val glassPos = coordinates.positionInWindow()

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val saveCount = nc.save()
            try {
                // 胶囊形裁剪（玻璃本地坐标，radius=高/2 与组件 Capsule 形状一致）
                val capsule = RectF(0f, 0f, size.width, size.height)
                val radius = size.height / 2f
                val capsulePath = android.graphics.Path().apply {
                    addRoundRect(capsule, radius, radius, android.graphics.Path.Direction.CW)
                }
                nc.clipPath(capsulePath)
                // 平移对齐：玻璃本地 (0,0) 平移到内容视图坐标系
                nc.translate(viewPos.x - glassPos.x, viewPos.y - glassPos.y)
                view.draw(nc)
            } finally {
                nc.restoreToCount(saveCount)
            }
        }
    }
}
