package com.moji.liquidglass.glass

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 玻璃底栏的实时状态：hook 线程写、Compose 读（快照状态，跨线程安全）。
 *
 * 原软件首页顶端下拉（二楼手势）时，`c.h0(float)` 会逐帧把底栏容器随内容位移；
 * 原容器已被本模块 GONE，hook 层把位移值写到这里，由玻璃底栏的 graphicsLayer
 * 读取跟随，与原软件行为一致，松手复位时 h0 回调 0 自动归位。
 */
object GlassBarState {

    /** 二楼下拉位移（px，正值向下）。 */
    var translationY by mutableFloatStateOf(0f)
}
