package com.moji.liquidglass.hook

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassInstaller
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.GlassStyle
import com.moji.liquidglass.hook.adapters.AppAdapter
import com.moji.liquidglass.hook.adapters.AdapterRegistry
import java.util.WeakHashMap

/**
 * 多目标 hook 分发入口：全局生命周期 hook + 适配器注册表按包名分发。
 *
 * 本文件零应用常量——所有应用专属逻辑（探测/提取/内容延伸/专属 hooks）
 * 在各 AppAdapter 内；样式与动效由共享玻璃引擎全局单源（GlassStyle）保证，
 * 适配器不触碰任何渲染参数。
 */
object TargetHook {

    private const val MAX_ATTEMPTS = 40
    private const val RETRY_INTERVAL_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    private val sessions = WeakHashMap<Activity, GlassSession>()
    private val scheduled = HashMap<Activity, Int>() // 已调度重试轮次（防止 onResume 重复排程）
    private val giveUpLogged = HashSet<Int>() // identityHashCode，仅记录一次放弃日志

    fun install(
        facade: HookFacade,
        configProvider: () -> ModuleConfig,
        appClassLoader: ClassLoader,
        hostPackage: String,
    ) {
        val adapter = AdapterRegistry.byPackage(hostPackage) ?: run {
            facade.log(android.util.Log.WARN, "package not adapted: $hostPackage")
            return
        }
        val config = ttlConfig(configProvider)

        val onResume = Activity::class.java.getDeclaredMethod("onResume")
        facade.around(onResume, null) { call ->
            val activity = call.thisObj as? Activity ?: return@around null
            GlassInstaller.dispatchResume(activity)
            GlassInstaller.glassStyle = GlassStyle.fromConfig(config())
            if (adapter.matches(activity)) {
                if (adapter.tabActivityClassName != null &&
                    activity.javaClass.name == adapter.tabActivityClassName
                ) {
                    adapter.onTabActivityResumed(activity, facade, config)
                }
                if (!config().enabled) {
                    sessions.remove(activity)?.restore()
                    scheduled.remove(activity)
                    return@around null
                }
                scheduleProbe(activity, adapter, facade, config)
            }
            null
        }
        // 驱动 Compose owner 生命周期：Activity 退后台时暂停 recomposer，省电且避免后台跑帧
        val onPause = Activity::class.java.getDeclaredMethod("onPause")
        facade.around(onPause, null) { call ->
            (call.thisObj as? Activity)?.let { GlassInstaller.dispatchPause(it) }
            null
        }
        adapter.installAppHooks(facade, config, appClassLoader)
        facade.log(android.util.Log.INFO, "hook installed for ${adapter.targetPackage} (${adapter.displayName})")
    }

    /** 远程配置读取加 500ms TTL（内容延伸 hook 是列表布局的高频调用点）。 */
    private fun ttlConfig(configProvider: () -> ModuleConfig): () -> ModuleConfig {
        var cached = ModuleConfig.DEFAULT
        var cachedAt = 0L
        return {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - cachedAt > 500) {
                cached = configProvider()
                cachedAt = now
            }
            cached
        }
    }

    private fun scheduleProbe(
        activity: Activity,
        adapter: AppAdapter,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        if (Build.VERSION.SDK_INT < 33) {
            if (scheduled.isEmpty()) {
                facade.log(android.util.Log.WARN, "Android 13+ (API 33) required for liquid glass, skip (sdk=${Build.VERSION.SDK_INT})")
            }
            return
        }
        // 失联的旧会话先还原，否则下面的存在性检查会永远跳过重装
        sessions[activity]?.let { current ->
            if (!current.isStale()) return
            current.restore()
            sessions.remove(activity)
        }
        if (scheduled.containsKey(activity)) return
        scheduled[activity] = 0
        handler.post { probe(activity, adapter, 0, facade, config) }
    }

    /** 主线程探测；目标视图树异步加载，未就绪则按重试间隔再试。 */
    private fun probe(
        activity: Activity,
        adapter: AppAdapter,
        attempt: Int,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            scheduled.remove(activity)
            return
        }
        if (!config().enabled) {
            scheduled.remove(activity)
            return
        }
        sessions[activity]?.let { current ->
            if (!current.isStale()) {
                scheduled.remove(activity)
                return
            }
            // 应用重建了视图树：还原后按当前结构重装
            current.restore()
            sessions.remove(activity)
        }

        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        val bar = content?.let { adapter.findTabBar(it) }
        val tabViews = bar?.let { adapter.tabViewsOf(it) } ?: emptyList()
        val ready = bar != null && adapter.readyToInstall(tabViews, activity.classLoader)
        if (!ready) {
            // 里程碑日志：未就绪时按 10 次间隔输出探测状态，卡点（bar/tabs/ready）一眼可辨
            if (attempt == 0 || attempt % 10 == 0) {
                facade.log(
                    android.util.Log.INFO,
                    "probe(${adapter.displayName}) attempt=$attempt bar=${bar != null} tabViews=${tabViews.size} ready=$ready",
                )
            }
            if (attempt + 1 >= MAX_ATTEMPTS) {
                scheduled.remove(activity)
                if (giveUpLogged.add(System.identityHashCode(activity))) {
                    facade.log(
                        android.util.Log.WARN,
                        "bottom tab bar not found after $MAX_ATTEMPTS attempts, give up (${adapter.displayName}) " +
                            "bar=${bar != null} tabViews=${tabViews.size}",
                    )
                }
                return
            }
            scheduled[activity] = attempt + 1
            handler.postDelayed({ probe(activity, adapter, attempt + 1, facade, config) }, RETRY_INTERVAL_MS)
            return
        }

        try {
            val session = GlassInstaller.install(activity, bar!!, tabViews, adapter.createExtractor())
                ?: throw IllegalStateException("installer returned null")
            session.onStale = { scheduleProbe(activity, adapter, facade, config) }
            session.logger = { msg -> facade.log(android.util.Log.INFO, msg) }
            setupNavBarInsetProvider(session)
            adapter.onSessionCreated(session, activity, facade, config)
            logTargetVersion(activity, adapter, facade)
            GlassInstaller.glassStyle = GlassStyle.fromConfig(config())
            sessions[activity] = session
            scheduled.remove(activity)
            facade.log(
                android.util.Log.INFO,
                "liquid glass bar installed (${adapter.displayName}): ${session.tabViews.size} tabs, " +
                    "activity=${activity.javaClass.name}",
            )
        } catch (t: Throwable) {
            facade.log(android.util.Log.WARN, "install failed (attempt $attempt)", t)
            if (attempt + 1 >= MAX_ATTEMPTS) {
                scheduled.remove(activity)
            } else {
                scheduled[activity] = attempt + 1
                handler.postDelayed({ probe(activity, adapter, attempt + 1, facade, config) }, RETRY_INTERVAL_MS)
            }
        }
    }

    /**
     * 小白条适配：玻璃栏抬升到小白条上方所需的导航栏 inset 数据源，三级回退：
     * 1. 底栏容器 paddingBottom（若应用在跟随 inset 维护，这是最准的应用侧值）；
     * 2. 容器 rootWindowInsets 的 navigationBars（窗口级真实值，不受应用在
     *    上游消费/拦截 insets 分发影响）；
     * 3. -1 = 不可用（GlassRoot 回退 Compose insets）。
     */
    private fun setupNavBarInsetProvider(session: GlassSession) {
        val container = session.tabContainer
        GlassInstaller.navInsetProvider = {
            container.paddingBottom.takeIf { it > 0 }
                ?: runCatching {
                    container.rootWindowInsets
                        ?.getInsets(android.view.WindowInsets.Type.navigationBars())
                        ?.bottom
                }.getOrNull()?.takeIf { it > 0 }
                ?: -1
        }
    }

    /** 记录目标应用版本：版本更新后混淆名/结构锚点一眼可从日志识别失配。 */
    private fun logTargetVersion(activity: Activity, adapter: AppAdapter, facade: HookFacade) {
        runCatching {
            val pm = activity.packageManager
            val versionName = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(adapter.targetPackage, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(adapter.targetPackage, 0).versionName
            }
            facade.log(android.util.Log.INFO, "target app (${adapter.displayName}) version = $versionName")
        }
    }
}
