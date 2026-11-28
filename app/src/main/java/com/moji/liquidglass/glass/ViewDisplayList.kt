package com.moji.liquidglass.glass

import android.graphics.Canvas
import android.os.Build
import android.view.View

/**
 * 宿主视图显示列表（display list）共享工具：把"重新执行宿主 view.draw"降级为
 * "引用宿主已录制的 RenderNode"。
 *
 * 背景（对标参考实现 SuperLiquid 的 Backdrop.drawRecorded）：玻璃镜像与背景采样
 * 每 tick 调一次 `view.draw(canvas)` 会在主线程把宿主视图树/条目重新编码一遍——
 * 相册 GL 内容页表现为整屏照片网格重画、小黑盒上滑表现为新绑定条目逐帧重付首建
 * 成本（№02.2/№15.7 已登记根因）。宿主自身的 traversal 本帧已经把这些视图录制进
 * 各自的 RenderNode，玻璃只需 `drawRenderNode(node)` 引用它：主线程零重编码，
 * RenderThread 按条带/镜像盒裁剪栅格化，内容仍是当帧状态（无降频滞后）。
 *
 * **关键位置语义**（2.9.92 回归根因）：`View.draw(Canvas)` 在 (0,0) 画自身内容，
 * 不施加自己的 mLeft/mTop；而 `Canvas.drawRenderNode` 会施加节点 LTRB、alpha、
 * 变换与阴影。宿主的 RenderNode LTRB = `View.setFrame` 写入的父容器内位置（AOSP
 * View.java: setFrame → mRenderNode.setLeftTopRightBottom(mLeft, mTop, ...)），
 * 因此引用子视图节点前必须平移 `(-node.left, -node.top)`，否则第 i 个 tab 条目会
 * 被平移 i×槽宽、被槽位裁剪成空白。平移由 [drawNode] 统一承担。
 *
 * **逐位等价前置条件**（任一不满足即返回不可用，调用方回退 `view.draw`）：
 * 节点存在且有 display list；视图 `isShown` 且 `layerType == NONE`；视图自身无
 * RenderEffect（反射 `View.getRenderEffect()`）；节点 alpha=1、无 translationX/Y/Z、
 * 无 elevation、scale=1 且变换矩阵为单位阵、无 clipToOutline、无 clipBounds；
 * 无进行中的传统 `view.animation`。这些属性直绘都不会施加，节点回放会施加——不满足
 * 时必须回退，保证 2.9.91 行为。已知边界：transition 的 `setAnimationMatrix` 不在
 * 守卫内（读取 `getAnimationMatrix()` 会每帧 new Matrix，分配代价不可接受），属极少数
 * 瞬时过渡；发现某应用过渡期偏移时按该应用声明 `viewDirectMirror` 即可。
 *
 * 反射与回放全部 `runCatching` 静默降级，禁止把异常抛进宿主绘制。仅 API 29+
 * 可用（minSdk 33 恒满足，保留守卫供低版本路径复用）。
 */
internal object ViewDisplayList {

    /** 视图自身 RenderNode（隐藏 API `View.mRenderNode`，与 ClippedViewBackdrop 同源）。 */
    private val renderNodeField: java.lang.reflect.Field? by lazy {
        runCatching {
            View::class.java.getDeclaredField("mRenderNode").apply { isAccessible = true }
        }.getOrNull()
    }

    /** 隐藏方法 `View.getRenderNode()` 兜底（个别 ROM 只暴露方法）。 */
    private val renderNodeMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            View::class.java.getDeclaredMethod("getRenderNode").apply { isAccessible = true }
        }.getOrNull()
    }

    /** `View.getRenderEffect()` 非公开 getter（SDK 只公开 setter）：反射读取，取不到
     *  按"无特效"处理（不会因此改变既有观感）。 */
    private val renderEffectMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            View::class.java.getDeclaredMethod("getRenderEffect").apply { isAccessible = true }
        }.getOrNull()
    }

    /** 显示列表引用绘制结果：区分"暂不可用"（下一帧可重试）与"回放失败"（永久回退）。 */
    enum class Result {
        /** 已用显示列表绘制。 */
        DRAWN,

        /** 当前不可用（未绘制/alpha/变换/特效/层类型等），调用方本帧回退。 */
        UNAVAILABLE,

        /** 节点存在但回放抛异常，调用方应永久回退该视图（避免每帧异常）。 */
        FAILED,
    }

    /**
     * 解析视图自身已录制的显示列表；不可用返回 null。
     * 调用方拿到节点后自行决定引用方式（条带录制/回放，统一经 [drawNode] 绘制）。
     */
    fun nodeOf(view: View): android.graphics.RenderNode? {
        if (Build.VERSION.SDK_INT < 29) return null
        if (!view.isShown) return null
        if (view.layerType != View.LAYER_TYPE_NONE) return null
        if (hasRenderEffect(view)) return null
        val node = resolveNode(view) ?: return null
        if (!node.hasDisplayList()) return null
        if (!isEquivalent(view, node)) return null
        return node
    }

    /**
     * 在硬件画布上以显示列表等价直绘（内部完成位置补偿 [drawNode]）。
     *
     * 返回 [Result.UNAVAILABLE] / [Result.FAILED] 时调用方必须回退
     * `GlassSession.withSignalSuppression { view.draw(canvas) }`（直绘会触发被 hook
     * 类的 onDraw，需信号抑制防自激）。
     */
    fun tryDraw(canvas: Canvas, view: View): Result {
        if (!canvas.isHardwareAccelerated) return Result.UNAVAILABLE
        val node = nodeOf(view) ?: return Result.UNAVAILABLE
        return if (drawNode(canvas, node)) Result.DRAWN else Result.FAILED
    }

    /**
     * 以"直绘等价"方式绘制节点：先平移 `(-left, -top)` 抵消节点 LTRB（见类 KDoc），
     * 再 `drawRenderNode`。返回 false = 回放异常（已恢复画布）。
     */
    fun drawNode(canvas: Canvas, node: android.graphics.RenderNode): Boolean {
        val save = canvas.save()
        return try {
            canvas.translate(-node.left.toFloat(), -node.top.toFloat())
            canvas.drawRenderNode(node)
            true
        } catch (_: Throwable) {
            false
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /** `getClipBounds(Rect)` 复用缓冲（主线程绘制相独占；避免每次 getClipBounds() new Rect）。 */
    private val clipBoundsScratch = android.graphics.Rect()

    /**
     * 一次性回退原因（仅日志用，含分配；禁止在逐帧热路径调用）。
     * 返回 "ok" 表示当前满足等价条件，其余为具体失败项逗号串。
     */
    fun reasonOf(view: View): String {
        if (Build.VERSION.SDK_INT < 29) return "sdk<29"
        if (!view.isShown) return "notShown"
        if (view.layerType != View.LAYER_TYPE_NONE) return "layerType=${view.layerType}"
        if (hasRenderEffect(view)) return "renderEffect"
        val node = resolveNode(view) ?: return "noNode"
        if (!node.hasDisplayList()) return "noDisplayList"
        val reasons = ArrayList<String>(4)
        if (node.alpha != 1f) reasons.add("alpha=${node.alpha}")
        if (node.translationX != 0f || node.translationY != 0f) reasons.add("translation")
        if (node.translationZ != 0f || node.elevation != 0f) reasons.add("elevation")
        if (node.scaleX != 1f || node.scaleY != 1f ||
            node.rotationX != 0f || node.rotationY != 0f || node.rotationZ != 0f
        ) {
            reasons.add("transform")
        }
        if (!node.hasIdentityMatrix()) reasons.add("matrix")
        if (view.animation != null) reasons.add("animation")
        if (node.clipToOutline) reasons.add("clipToOutline")
        if (view.getClipBounds(clipBoundsScratch)) reasons.add("clipBounds")
        return if (reasons.isEmpty()) "ok" else reasons.joinToString(",")
    }

    /** 反射取节点（字段优先、隐藏方法兜底）；失败返回 null。 */
    private fun resolveNode(view: View): android.graphics.RenderNode? {
        return runCatching {
            renderNodeField?.get(view) as? android.graphics.RenderNode
        }.getOrNull() ?: runCatching {
            renderNodeMethod?.invoke(view) as? android.graphics.RenderNode
        }.getOrNull()
    }

    /** 直绘与节点回放结果是否逐位等价（见类 KDoc 前置条件）。 */
    private fun isEquivalent(view: View, node: android.graphics.RenderNode): Boolean {
        if (node.alpha != 1f) return false
        if (node.translationX != 0f || node.translationY != 0f) return false
        if (node.translationZ != 0f || node.elevation != 0f) return false
        if (node.scaleX != 1f || node.scaleY != 1f) return false
        if (!node.hasIdentityMatrix()) return false
        // 不调用 View.getAnimationMatrix()：它内部 new Matrix()，120Hz×10 次/帧的分配
        // 代价不可接受。退而求其次用 view.animation（传统 Animation）挡掉最常见路径；
        // transition 的 setAnimationMatrix（非 view.animation）不覆盖，属已知边界。
        if (view.animation != null) return false
        if (node.clipToOutline) return false
        if (view.getClipBounds(clipBoundsScratch)) return false
        return true
    }

    private fun hasRenderEffect(view: View): Boolean {
        val getter = renderEffectMethod ?: return false
        return runCatching { getter.invoke(view) != null }.getOrDefault(false)
    }
}
