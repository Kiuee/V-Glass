package com.moji.liquidglass.glass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 注入到目标应用的 Compose 生命周期宿主。
 *
 * 目标 Activity 不是 androidx ComponentActivity 体系（或其混淆后的
 * ViewTreeLifecycleOwner 标签 id 与模块内 androidx 不一致），ComposeView 解析
 * window recomposer 时会抛 "ViewTreeLifecycleOwner not found" 直接崩溃宿主。
 * 这里在模块内自建 Lifecycle/SavedStateRegistry/ViewModelStore 三合一 owner，
 * 在 ComposeView 挂载前设置到窗口根视图上，由 hook 层随 Activity 生命周期驱动。
 *
 * 注意：owner 与窗口（Activity）绑定而不是与装配会话绑定——Compose 的 window
 * recomposer 会缓存在根视图上，若 owner 随会话销毁，二次装配会复用已死亡
 * 的 recomposer 导致玻璃冻结，因此 restore 时只暂停、不销毁 owner。
 */
class ComposeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreDelegate = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreDelegate

    fun handleOnCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun handleOnStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun handleOnResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun handleOnPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun handleOnStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
}
