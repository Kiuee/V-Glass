package com.moji.liquidglass.glass.utils

import androidx.compose.runtime.withFrameNanos

/*
 * 移植自 Kyant0/AndroidLiquidGlass（kmp 分支）utils/Coroutines.kt 的 android actual。
 * 参考项目为 expect/actual 声明，Android 平台实现即等待一帧。
 */
suspend fun awaitFrame() {
    withFrameNanos { }
}
