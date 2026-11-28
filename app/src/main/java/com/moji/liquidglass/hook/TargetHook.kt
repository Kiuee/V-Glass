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
import java.lang.reflect.Executable
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

    /** 中央默认内容活动信号实命中类数（installAppHooks 期记录）：>0 才允许会话开启
     *  静止降帧两档 + 深度静止档（无信号源会话开启 = 静止档跟不上内容变化，玻璃
     *  恒滞后；平台类恒可解析，实际恒 ≥3，防御性判空）。 */
    private var centralSignalHooked = 0

    /** 进程级注入计数/指纹（System 属性跨 classloader 共享——温复用旧进程里新旧两套
     *  模块 dex 各有各的静态区，唯有 System 属性能对账）。 */
    private const val PROP_INSTALL_COUNT = "moji.liquidglass.install.count"
    private const val PROP_INSTALL_FINGERPRINTS = "moji.liquidglass.install.fingerprints"

    /** 取证通道读取（卡顿归因样本行自带双构建指纹，无需另抓 logcat）。 */
    internal fun installFingerprints(): String? = System.getProperty(PROP_INSTALL_FINGERPRINTS)

    fun install(
        facade: HookFacade,
        configProvider: () -> ModuleConfig,
        appClassLoader: ClassLoader,
        hostPackage: String,
        entry: String,
    ) {
        val count = (System.getProperty(PROP_INSTALL_COUNT)?.toIntOrNull() ?: 0) + 1
        System.setProperty(PROP_INSTALL_COUNT, count.toString())
        val fingerprint = moduleFingerprint(entry, appClassLoader)
        val previous = System.getProperty(PROP_INSTALL_FINGERPRINTS)
        System.setProperty(
            PROP_INSTALL_FINGERPRINTS,
            if (previous.isNullOrEmpty()) fingerprint else "$previous | $fingerprint",
        )
        facade.log(android.util.Log.INFO, "module fingerprint #$count: $fingerprint")
        if (count > 1) {
            facade.log(
                android.util.Log.ERROR,
                "duplicate module injection detected ($count builds/entries in one process): " +
                    (System.getProperty(PROP_INSTALL_FINGERPRINTS) ?: "") +
                    " — every hooked method dispatches through all of them (double tax on hot paths). " +
                    "Host process is stale (module updated without app restart) or both entries/environments " +
                    "are enabled: reboot the phone and check LSPosed module entries.",
            )
        }
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
                // 总开关 + 按应用开关：任一关闭即还原已装配会话，跳过本应用全部模块行为
                if (!config().enabled || !config().appEnabledFor(hostPackage)) {
                    sessions.remove(activity)?.restore()
                    scheduled.remove(activity)
                    return@around null
                }
                if (adapter.tabActivityClassName != null &&
                    activity.javaClass.name == adapter.tabActivityClassName
                ) {
                    adapter.onTabActivityResumed(activity, facade, config)
                }
                // 回前台唤醒 backdrop：Surface 重建丢弃已录制的采样条带显示列表，
                // 去重/冻结回放在重录制前为空（表现为"玻璃轮廓在、内容空"）
                sessions[activity]?.invalidateBackdrop()
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
        // 总开关 + 按应用开关 = LSPosed 作用域语义：任一关闭 → 不安装任何应用 hook
        // （installAppHooks 装的是进程级全局方法 hook，与 Activity 生命周期无关，不能等
        // resume 才还原）。运行时切换由 ConfigCheckedFacade 在回调层惰性直通兜底。
        val hooksActive = config().enabled && config().appEnabledFor(hostPackage)
        val checkedFacade = ConfigCheckedFacade(facade, config, hostPackage)
        adapter.installAppHooks(
            checkedFacade,
            config,
            appClassLoader,
        )
        // 内容活动信号单管线（进程级一次性）：默认 5 类 + 适配器声明的应用专属类，
        // 经 ConfigCheckedFacade 保证开关关闭时回调直通。适配器勿自接信号 hook
        // （多管线叠加是架构红线，自接代码已全部迁移进本管线）
        centralSignalHooked = AppAdapter.hookContentActivitySignals(
            checkedFacade,
            appClassLoader,
            adapter.contentActivitySignalClasses,
        )
        facade.log(
            android.util.Log.INFO,
            "hook installed for ${adapter.targetPackage} (${adapter.displayName}), hooksActive=$hooksActive",
        )
    }

    /**
     * installAppHooks 专用 facade 装饰器：总开关或按应用开关关闭时，所有 hook 回调
     * 惰性直通（before→null 放行、int 变换→原值、swallow→不拦截）——hook 仍在但零
     * 作用，重新打开自动恢复。运行时切换（安装后关开关）的兜底，语义对齐 LSPosed
     * 作用域勾选。
     */
    private class ConfigCheckedFacade(
        private val inner: HookFacade,
        private val config: () -> ModuleConfig,
        private val hostPackage: String,
    ) : HookFacade by inner {
        private fun active(): Boolean =
            config().enabled && config().appEnabledFor(hostPackage)

        override fun around(
            m: Executable,
            before: ((HookCall) -> Any?)?,
            after: ((HookCall) -> Any?)?,
        ) {
            inner.around(
                m,
                before?.let { cb -> { call: HookCall -> if (active()) cb(call) else null } },
                after?.let { cb -> { call: HookCall -> if (active()) cb(call) else null } },
            )
        }

        override fun mapIntArg(m: Executable, index: Int, transform: (Int) -> Int) {
            inner.mapIntArg(m, index) { original -> if (active()) transform(original) else original }
        }

        override fun swallowArgIf(m: Executable, index: Int, gate: (Any?) -> Boolean) {
            inner.swallowArgIf(m, index) { arg -> active() && gate(arg) }
        }
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

    /**
     * 构建指纹（d6bea36 想加的"loaded 行带 module 版本"，本次补齐）：入口 + 本 dex 的
     * 物理来源（模块 apk 路径/大小/mtime，BaseDexClassLoader.pathList 反射——灰名单，
     * 取不到时仅留入口与 loader 身份）——不同构建 size/mtime 不同；进程里出现两套指纹
     * = 温复用旧进程 stale 注入 / 双入口双载（上方的重复注入 ERROR 据此触发）。
     */
    private fun moduleFingerprint(entry: String, loader: ClassLoader): String {
        val dexPath = runCatching {
            val pathList = loader.javaClass.getDeclaredField("pathList")
                .apply { isAccessible = true }.get(loader)
            val elements = pathList.javaClass.getDeclaredField("dexElements")
                .apply { isAccessible = true }.get(pathList) as? Array<*>
            val first = elements?.firstOrNull() ?: return@runCatching null
            for (name in arrayOf("path", "file", "dexFile")) {
                val field = first.javaClass.getDeclaredField(name)
                    .apply { isAccessible = true }
                when (val value = field.get(first)) {
                    is java.io.File -> return@runCatching value.path
                    is String -> return@runCatching value
                    is dalvik.system.DexFile -> return@runCatching value.name
                }
            }
            null
        }.getOrNull()
        val meta = dexPath?.let { path ->
            runCatching {
                val f = java.io.File(path)
                "apk=$path size=${f.length()} mtime=${f.lastModified()}"
            }.getOrNull()
        } ?: "dex=?"
        return "$entry $meta loader@${System.identityHashCode(loader)}"
    }


    private fun scheduleProbe(
        activity: Activity,
        adapter: AppAdapter,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 探测页面门控（如相册仅在主 Tab Activity 探测，避免其他页面的同类底栏误装）
        if (!adapter.shouldProbe(activity)) return
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
            val session = GlassInstaller.install(
                activity,
                bar!!,
                tabViews,
                adapter.createExtractor(),
            ) ?: throw IllegalStateException("installer returned null")
            session.onStale = { scheduleProbe(activity, adapter, facade, config) }
            session.logger = { msg -> facade.log(android.util.Log.INFO, msg) }
            setupNavBarInsetProvider(session)
            adapter.onSessionCreated(session, activity, facade, config)
            // 静止降帧统一接线（唯一设置点，全部会话）：两档 + 深度静止档（活动窗口 1s
            // → 30fps 静止档 3s → 1Hz 帧循环睡眠，LTPO 降面板最低档对齐原生功耗）。
            // 信号源 = 单管线广播（默认类 + 适配器声明类）。idleFrameSleep 配置远程
            // 可关（视觉异常回退逐位不变旧行为每帧恒满档）。
            if (config().idleFrameSleep && centralSignalHooked > 0) {
                session.idleDecayTickEnabled = true
                session.idleFrameSleepEnabled = true
                session.logger?.invoke("idle decay + deep idle (1Hz frame sleep) enabled")
            }
            // 采样去重统一设置（唯一设置点，默认开）：条带录制+回放（1×/tick）替代
            // 各节点全树直绘（3×/tick），轻量采样配置远程可关
            session.dedupeBackdropSampling = config().lightweightSampling
            // 显示列表引用（唯一设置点，默认开，2.9.92）：条带采样与镜像不再每 tick
            // view.draw 重编码宿主树，改为引用宿主已录制的 RenderNode（ViewDisplayList）；
            // 取不到/不等价自动回退。descriptor 逃生门可按应用单独关闭；远程
            // liveNodeSampling=false 整体回退 2.9.91 直绘路径。
            session.backdropLiveNodeSampling =
                config().liveNodeSampling && !adapter.descriptor.backdropDirectDraw
            session.viewNodeMirror =
                config().liveNodeSampling && !adapter.descriptor.viewDirectMirror
            // 高刷减半（默认关 = 满帧跟随面板刷新率；仅 120Hz 实测掉帧时远程打开）
            session.highRefreshHalfRate = config().highRefreshHalfRate
            AppAdapter.registerActivitySignalSession(session)
            logTargetVersion(activity, adapter, facade)
            GlassInstaller.glassStyle = GlassStyle.fromConfig(config())
            sessions[activity] = session
            scheduled.remove(activity)
            facade.log(
                android.util.Log.INFO,
                "liquid glass bar installed (${adapter.displayName}): ${session.tabViews.size} tabs, " +
                    "activity=${activity.javaClass.name}, appRoot=${session.appRoot.javaClass.name}",
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
