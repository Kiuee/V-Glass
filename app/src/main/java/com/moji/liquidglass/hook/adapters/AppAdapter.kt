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

    /**
     * 深度逆向适配的基准版本（完成锚点级适配的版本，升版逆向后更新此列表
     * 并同步 MAINTENANCE.md 对应章节的「基准版本」行）。
     */
    val baselineVersions: List<String> get() = emptyList()

    /**
     * 最终已适配版本列表（管理器应用页据此显示已适配/未适配 chip 与展开列表）：
     * = [baselineVersions] + [AdaptedVersionRegistry] 集中追加的免适配版本，按序去重。
     * 精确匹配：已安装版本不在列表内 = 未适配，仅提示兼容性风险，不禁用功能。
     */
    val adaptedVersions: List<String>
        get() = (baselineVersions + AdaptedVersionRegistry.extraAdaptedVersions[targetPackage].orEmpty())
            .distinct()

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

    /**
     * 应用专属内容活动信号类（可选声明，默认空）：静止降帧两档 + 深度静止档的唯一
     * 信号管线（TargetHook 进程级一次性 hook [AppAdapter.DEFAULT_CONTENT_SIGNAL_CLASSES]
     * + 本列表）之外的补充信号源。**适配器只声明类名，hook 安装/分发/降帧档位全部
     * 由引擎单管线负责**（勿再自接 hookContentDrawSignals / 自设降帧旗标——多管线
     * 叠加是架构红线）。走 AppAdapter.hookContentDrawSignals 的同一 walk-up 落点
     * 语义（沿类层次找 onDraw 声明点），应用不存在某类时自动跳过。
     */
    val contentActivitySignalClasses: List<String> get() = emptyList()

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
        // —— 内容活动信号单管线（静止降帧两档 + 深度静止档的唯一活动源）——
        // TargetHook 进程级一次性 hook（默认类 + 适配器 [AppAdapter.contentActivitySignalClasses]
        // 声明）→ 单一分发口广播 → 各会话 armActivityWindow + onContentActivity 消费者。
        // 适配器勿自接信号 hook / 自设降帧旗标（多管线叠加是架构红线）。
        /**
         * 跨应用默认内容活动信号类：RecyclerView（列表滚动/翻页/条目重绑定）+
         * LottieAnimationView（banner/加载/图标动画持续重绘）+ TextureView（视频逐帧
         * 输出——feed 视频实时跟随的关键信号源，播放期间信号逐帧续期永不降档）+
         * WebView（H5 页面自绘帧）+ CompoundButton（选中/勾选状态重绘）。平台类混淆
         * 免疫，应用不存在某类（无 androidx/无 lottie）时自动跳过按实命中计。
         * walk-up 落点事实：LottieAnimationView 不声明 onDraw（继承链无 onDraw）→
         * 落 **ImageView.onDraw**，hook 覆盖全进程 ImageView。
         * 玻璃 ComposeView 挂 decor content、不在任何被 hook 类子树内——玻璃自身重绘
         * 不触发信号；镜像 Painter 对宿主视图的**单参直绘**会触发（自激回路），由
         * GlassSession.signalSuppressed 抑制（见 withSignalSuppression）。
         * ViewRootImpl.performTraversals 全局信号不可用的根因 = 玻璃层自身 tick 触发
         * traversal 会自持满帧。
         */
        val DEFAULT_CONTENT_SIGNAL_CLASSES = listOf(
            "androidx.recyclerview.widget.RecyclerView",
            "com.airbnb.lottie.LottieAnimationView",
            "android.view.TextureView",
            "android.webkit.WebView",
            "android.widget.CompoundButton",
        )

        private val signalSessions = ArrayList<java.lang.ref.WeakReference<GlassSession>>()
        @Volatile private var signalsHooked = false

        /** 注册会话到信号广播表（TargetHook 装配成功后调用；弱引用 + 注册时惰性清理，
         *  restore 后未及 GC 的会话收到广播无害——帧循环已断仅字段写入）。 */
        fun registerActivitySignalSession(session: GlassSession) {
            synchronized(signalSessions) {
                signalSessions.removeAll { it.get() == null }
                signalSessions.add(java.lang.ref.WeakReference(session))
            }
        }

        /** 内容活动信号广播（hook 回调，主线程，唯一分发口）：升所有注册会话的满帧率
         *  窗口 + 通知会话级消费者（onContentActivity，采样冻结检测等）。玻璃发起的
         *  宿主直绘期间整体抑制（自激防护）。 */
        private fun dispatchContentActivitySignal() {
            if (GlassSession.signalSuppressed) return
            synchronized(signalSessions) {
                for (ref in signalSessions) ref.get()?.let {
                    it.armActivityWindow()
                    it.onContentActivity?.invoke()
                }
            }
        }

        /**
         * 内容活动信号接线（进程级一次性，TargetHook installAppHooks 期调用；facade 用
         * ConfigCheckedFacade 包装保证开关关闭时回调直通）：默认类 + [extraClasses]
         * （适配器 [AppAdapter.contentActivitySignalClasses] 声明）一次 hook。返回实命中
         * 的信号类数——0 = 无可用信号源，调用方不得开启静止降帧（玻璃跟不上内容变化）。
         * 平台类（TextureView/WebView/CompoundButton）恒可解析，实际恒 ≥3。
         */
        fun hookContentActivitySignals(facade: HookFacade, loader: ClassLoader, extraClasses: List<String>): Int {
            val classNames = DEFAULT_CONTENT_SIGNAL_CLASSES + extraClasses
            if (signalsHooked) return classNames.size
            signalsHooked = true
            val hooked = hookContentDrawSignals(facade, loader, classNames) { _ ->
                dispatchContentActivitySignal()
            }
            facade.log(
                android.util.Log.INFO,
                "content activity signals hooked: $hooked/${classNames.size}" +
                    (if (extraClasses.isNotEmpty()) " (default ${DEFAULT_CONTENT_SIGNAL_CLASSES.size} + app ${extraClasses.size})" else ""),
            )
            return hooked
        }

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
         * hook 内容活动信号源（静止降帧唯一管线，TargetHook 经
         * [hookContentActivitySignals] 调用）：这些类的 `onDraw` = 应用内容真正重绘的
         * 时刻（滚动/动画期间每帧触发、静止时不触发）。
         *
         * 沿类层次向上找 `onDraw` 的声明点（子类未覆写时落在父类实现并对该父类全部
         * 实例生效，如内容容器继承 RecyclerView 时 hook 到 RecyclerView.onDraw）；
         * **绝不落在 `android.view.View` 的空实现**（那会拦截全进程所有视图，过热）。
         * 同一声明点只 hook 一次。返回成功覆盖的信号类数（0 = 无可用信号源，调用方
         * 不得开启静止降帧，否则玻璃跟不上内容变化）。
         *
         * **自激防护**：回调在 [GlassSession.signalSuppressed]（玻璃直绘宿主视图期间）
         * 置位时跳过——镜像 Painter 每 tick 单参 `view.draw` 直绘被 hook 类产生的
         * onDraw 是绘制副作用，不是内容活动。
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
                        facade.around(method, null) {
                            if (!GlassSession.signalSuppressed) onSignal(className)
                            null
                        }
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
