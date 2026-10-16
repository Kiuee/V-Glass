package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.glass.TabExtractor
import com.moji.liquidglass.hook.HookFacade

/**
 * 目标应用适配器：声明包名、底栏探测与提取方式、可选的原生行为修补。
 *
 * 硬约束（架构约定，评审时逐条对照）：
 * - 适配器只产出内容数据（GlassBarSpec / 视图引用）与可选修补策略，
 *   **不触碰任何样式/动效参数**——观感与动效由共享玻璃引擎全局单源保证；
 * - 应用专属常量（包名/类名/id 名/混淆名）只允许出现在适配器文件内，
 *   核心（TargetHook 入口 / AdapterRegistry / glass 引擎）零应用常量；
 * - 新增应用适配 = 实现本接口（通常只需描述符 + findTabBar/tabViewsOf）
 *   + 在 AdapterRegistry 注册 + 在 LSPosed 勾选作用域。
 */
interface AppAdapter {

    val targetPackage: String

    /** 应用展示名（设置页/日志用）。 */
    val displayName: String

    /** 主 Tab Activity 类名（导航栏呈现等仅在该页生效；null = 不判定）。 */
    val tabActivityClassName: String?

    /** 底栏结构描述符。 */
    val descriptor: TabBarDescriptor

    fun matches(activity: Activity): Boolean = activity.packageName == targetPackage

    /** 探测页面门控：默认全部 Activity 都探测；适配器可限定为主 Tab Activity 等。 */
    fun shouldProbe(activity: Activity): Boolean = true

    fun createExtractor(): TabExtractor = TabExtractor(descriptor)

    /** 装配就绪判定：tab 数合法 + 应用侧状态就绪（如 Lottie 合成加载完成）。 */
    fun readyToInstall(tabViews: List<View>, loader: ClassLoader): Boolean =
        tabViews.size >= 2 && TabExtractor(descriptor).readyToInstall(tabViews, loader)

    /** 在窗口内容树中定位底栏容器（承载 tab 视图的根）。找不到返回 null。 */
    fun findTabBar(content: ViewGroup): View?

    /** 从底栏容器解析每 tab 视图列表（顺序 = tab 顺序）。 */
    fun tabViewsOf(bar: View): List<View>

    /** 一次性安装应用专属 hooks（内容延伸等；无差异需求时空实现）。 */
    fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {}

    /** 主 Tab Activity onResume（导航栏呈现等每次恢复期重设的行为）。 */
    fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {}

    /** 装配成功后的注入（导航栏 inset 数据源、取证计划等）。 */
    fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
    }

    companion object {
        /**
         * 导航栏透明化（小白条区域呈现，参照 LiquidTab 的导航栏沉浸）：
         * 导航栏透明 + 关闭系统对比度蒙层，信息流在小白条区域原色透出。
         * 玻璃栏只存在于主 Tab Activity，每次 onResume 重设（应用可能在 onCreate 后改回）。
         */
        fun applyTransparentNavigationBar(activity: Activity, config: () -> ModuleConfig) {
            if (!config().enabled) return
            runCatching {
                val window = activity.window
                window.navigationBarColor = Color.TRANSPARENT
                window.navigationBarDividerColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.decorView.requestApplyInsets()
            }
        }

        /**
         * hook 内容重绘信号源（事件驱动 tick，见 `GlassSession.tickEventDriven`）：
         * 这些类的 `onDraw` = 应用内容真正重绘的时刻（滚动/动画期间每帧触发、静止时
         * 不触发），先于玻璃绘制、同一 traversal 内生效——静止零重绘且满帧率无拍频。
         *
         * 沿类层次向上找 `onDraw` 的声明点（子类未覆写时落在父类实现并对该父类全部
         * 实例生效，如内容容器继承 RecyclerView 时 hook 到 RecyclerView.onDraw）；
         * **绝不落在 `android.view.View` 的空实现**（那会拦截全进程所有视图，过热）。
         * 同一声明点只 hook 一次。返回成功覆盖的信号类数（0 = 无可用信号源，调用方
         * 应禁用事件驱动 tick 回退每帧渲染，否则玻璃停更）。
         */
        fun hookContentDrawSignals(
            facade: HookFacade,
            loader: ClassLoader,
            classNames: List<String>,
            onSignal: (className: String) -> Unit,
        ): Int {
            var hooked = 0
            val seenDeclarations = HashSet<Class<*>>()
            for (className in classNames) {
                val clazz = runCatching { Class.forName(className, true, loader) }.getOrNull() ?: continue
                var target: java.lang.reflect.Method? = null
                var c: Class<*>? = clazz
                while (c != null && c != View::class.java) {
                    val m = runCatching {
                        c.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java)
                    }.getOrNull()
                    if (m != null) {
                        target = m
                        break
                    }
                    c = c.superclass
                }
                val method = target ?: continue
                if (seenDeclarations.add(method.declaringClass)) {
                    runCatching {
                        facade.around(method, null) { onSignal(className); null }
                    }.onFailure {
                        facade.log(android.util.Log.WARN, "hook content signal failed: $className", it)
                    }
                }
                hooked++
            }
            return hooked
        }
    }
}
