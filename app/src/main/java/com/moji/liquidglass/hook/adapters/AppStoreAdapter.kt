package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.glass.TabExtractor
import com.moji.liquidglass.glass.TabRole
import com.moji.liquidglass.hook.HookFacade
import java.lang.ref.WeakReference

/**
 * vivo 应用商店（com.bbk.appstore）适配器。
 *
 * - 底栏：home_page_root_view 的 #tab_container（FrameLayout，alignParentBottom）
 *   内 @android:id/tabs（TabWidget），tab 视图为 com.bbk.appstore.ui.tab.TabView
 *   （双 Lottie：lottie_view 主图标 / lottie_view_refresh 刷新，LOTTIE_RESOLVE 镜像）。
 * - 内容延伸（信息流沉浸）：全部 tab 页列表的底栏高度留白取自混淆方法 g9.h.j()
 *   （= 66dp + 导航栏 inset），精准方案是改写分发入口 ca.h.G0(int)（j() 保持真实值，
 *   下拉刷新状态机不受影响）；类名失配时逐级降级到结构化兜底（RecyclerView.setPadding）。
 * - 可见性修补：导航栏透明（小白条区域呈现），见 applyTransparentNavigationBar。
 */
object AppStoreAdapter : AppAdapter {

    override val targetPackage: String = "com.bbk.appstore"
    override val displayName: String = "vivo 应用商店"
    override val tabActivityClassName: String? = "com.bbk.appstore.ui.AppStoreTabActivity"

    private const val TAB_VIEW_CLASS = "com.bbk.appstore.ui.tab.TabView"

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = null, // 容器 = TabWidget 的父级（#tab_container），见 findTabBar
        tabWidgetIdName = "tabs", // android.R.id.tabs
        tabViewClassName = TAB_VIEW_CLASS,
        roles = mapOf(
            TabRole.TEXT_VIEW to "textview",
            TabRole.ICON_LOTTIE to "lottie_view",
            TabRole.REFRESH_LOTTIE to "lottie_view_refresh",
            TabRole.BADGE_NUM to "update_noti_num",
            TabRole.BADGE_DOT_GUIDE to "guide_iv",
            TabRole.BADGE_DOT_TRASH to "trash_noti_red_dot",
        ),
        iconMode = IconMode.LOTTIE_RESOLVE,
        fallbackProbe = true,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val extractor by lazy { TabExtractor(descriptor) }

    // 结构化兜底（不依赖混淆名）：标准类名 hook 全部失配时置位，
    // 装配成功后从视图树反查真实列表类再 hook（视图树异步加载，可能需要重试）
    @Volatile
    private var structuralViewTreeProbeNeeded = false

    @Volatile
    private var structuralHookDone = false

    /** 当前装配了玻璃栏的 Activity（h0 回调触发取证时定位目标）。 */
    @Volatile
    private var glassActivityRef: WeakReference<Activity>? = null

    override fun createExtractor(): TabExtractor = extractor

    /** 首选 @android:id/tabs（TabWidget）且首子项为 TabView 类，返回其父容器（#tab_container）；
     *  兜底：任意贴底、含 3–6 个"各带文字 TextView"子项的 ViewGroup（应用更新容错）。 */
    override fun findTabBar(content: ViewGroup): View? {
        val byId = content.findViewById<ViewGroup>(android.R.id.tabs)
        if (byId != null && byId.childCount >= 2 &&
            byId.getChildAt(0)?.javaClass?.name == TAB_VIEW_CLASS
        ) {
            return byId.parent as? ViewGroup ?: byId
        }
        if (!descriptor.fallbackProbe) return null
        val candidates = ArrayList<ViewGroup>()
        collectViewGroups(content, candidates, depth = 0)
        return candidates.firstOrNull { vg ->
            vg.childCount in 3..6 &&
                vg.isBottomAligned() &&
                (0 until vg.childCount).all { i ->
                    val child = vg.getChildAt(i)
                    child is ViewGroup && hasVisibleText(child)
                } &&
                byId == null // 有 TabWidget 但类名不匹配时优先交给首选路径的失败重试，避免误装
        }
    }

    override fun tabViewsOf(bar: View): List<View> {
        val tabWidget = (bar as? ViewGroup)?.findViewById<ViewGroup>(android.R.id.tabs) ?: bar
        return (0 until (tabWidget as ViewGroup).childCount).mapNotNull { tabWidget.getChildAt(it) }
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        when (config().contentExtend) {
            ModuleConfig.CONTENT_EXTEND_SOURCE -> {
                // 直接 hook j() 清零留白——注意：会破坏下拉刷新状态机（竖向滑动失灵的根因）
                if (!hookBottomBarPadding(facade, config, loader)) {
                    // 商店自更新后混淆名 g9.h 失配时留白清零彻底失效（信息流停在小白条上方），
                    // 自动接入不依赖混淆名的结构化兜底
                    if (!hookListPaddingStructural(facade, config, loader)) {
                        structuralViewTreeProbeNeeded = true
                    }
                }
            }

            ModuleConfig.CONTENT_EXTEND_APPLIER -> {
                // 精准方案：j() 保持真实值（下拉刷新的拉距/阈值计算不受影响），改写最终落地留白
                if (!hookBottomBarPaddingApplier(facade, loader)) {
                    if (!hookListPaddingStructural(facade, config, loader)) {
                        structuralViewTreeProbeNeeded = true
                    }
                }
            }

            else -> facade.log(android.util.Log.INFO, "contentExtend=off: list padding untouched")
        }
        hookBarTranslation(facade, loader)
        hookTouchFlow(facade, loader)
        hookGestureChain(facade, loader)
        hookNavigationBarPresentation(facade, config, loader)
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        glassActivityRef = WeakReference(activity)
        session.afterForward = {
            handler.post { if (!activity.isFinishing && !activity.isDestroyed) runDiagnostics(activity, facade) }
        }
        // 取证：装配后定时 dump（覆盖 tab 切换后的视图状态）；顺路重试结构化兜底
        for (delay in longArrayOf(3_000, 8_000, 15_000, 25_000, 40_000)) {
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (structuralViewTreeProbeNeeded && !structuralHookDone) {
                        applyStructuralFallbackFromViewTree(activity, facade, config)
                    }
                    runDiagnostics(activity, facade)
                }
            }, delay)
        }
    }

    /**
     * 二楼下拉时玻璃底栏跟随位移：原软件首页顶端下拉（二楼手势）期间，`c.h0(float)`
     * 逐帧把底栏容器随内容平移。容器可见但隐形（alpha=0），这里把位移值写入
     * GlassBarState 驱动玻璃栏跟随，并放行原方法（对应用状态机完整无害）。
     * 类名 c 为混淆名，仅对本 APK 版本有效，查找失败自动降级（玻璃栏固定不动）。
     */
    private fun hookBarTranslation(facade: HookFacade, loader: ClassLoader) {
        try {
            val h0 = Class.forName("com.bbk.appstore.ui.tab.c", true, loader)
                .getDeclaredMethod("h0", Float::class.javaPrimitiveType)
            var lastTranslation = 0f
            facade.around(h0, { call ->
                val translation = (call.args.firstOrNull() as? Number)?.toFloat() ?: 0f
                com.moji.liquidglass.glass.GlassBarState.translationY = translation
                val previous = lastTranslation
                lastTranslation = translation
                // 二楼手势结束（位移回归 0）：捕捉"返回后条带正常"状态的视图树，与常规状态对比
                if (previous != 0f && translation == 0f) {
                    glassActivityRef?.get()?.let { act ->
                        handler.post {
                            if (!act.isFinishing && !act.isDestroyed) runDiagnostics(act, facade)
                        }
                    }
                }
                null // 放行原方法：保持应用状态机完整
            }, null)
            facade.log(android.util.Log.INFO, "hooked c.h0(float): glass bar follows second-floor pull")
        } catch (t: Throwable) {
            facade.log(
                android.util.Log.WARN,
                "hook c.h0 failed, glass bar stays fixed during second-floor pull",
                t,
            )
        }
    }

    /**
     * 内容延伸的精准方案：j() 保持真实值（下拉刷新的拉距/阈值计算不受影响），
     * 只改写把留白值落地为 padding/margin 的分发入口 `ca.h.G0(int)`
     * （首页所有子页留白都从这一个方法分发到 a2/Q/v/t）。类名 ca.h 为混淆名，
     * 仅对本 APK 版本有效，查找失败自动降级。
     */
    private fun hookBottomBarPaddingApplier(facade: HookFacade, loader: ClassLoader): Boolean {
        return try {
            val g0 = Class.forName("ca.h", true, loader)
                .getDeclaredMethod("G0", Int::class.javaPrimitiveType)
            facade.mapIntArg(g0, 0) { 0 }
            facade.log(android.util.Log.INFO, "hooked ca.h.G0(int): bottom padding applier zeroed (j() intact)")
            true
        } catch (t: Throwable) {
            facade.log(
                android.util.Log.WARN,
                "hook ca.h.G0 failed (store updated?), falling back to structural list padding hook",
                t,
            )
            false
        }
    }

    /**
     * 列表底部留白清零（让内容延伸到玻璃栏与小白条下方）：
     * 应用商店所有 tab 页列表的底栏高度留白统一取自静态方法 g9.h.j()（= 66dp + 导航栏 inset），
     * 首页信息流/分类/排行/管理页/二级子页/Flutter BOTTOM_OVERLAY_HEIGHT 全部经过它。
     * 模块启用时返回 0，停用时走原方法（原底栏需要留白）。类名为混淆名，仅对本 APK 版本有效。
     */
    private fun hookBottomBarPadding(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader): Boolean {
        return try {
            val j = Class.forName("g9.h", true, loader).getDeclaredMethod("j")
            facade.around(j, { if (config().enabled) 0 else null }, null)
            facade.log(android.util.Log.INFO, "hooked g9.h.j(): list bottom padding zeroed while enabled")
            true
        } catch (t: Throwable) {
            facade.log(
                android.util.Log.WARN,
                "hook g9.h.j() failed (store updated?), falling back to structural list padding hook",
                t,
            )
            false
        }
    }

    /**
     * 内容延伸的结构化兜底：不依赖混淆类名，hook 应用自带 RecyclerView 基类的
     * setPadding(int,int,int,int)，把底栏留白的最终落地值清 0（语义同 applier 方案，
     * 不碰留白计算状态机）。jadx 结论：商店列表底部留白唯一来源就是底栏高度，
     * 进程内所有 RecyclerView 的底 padding 都是该留白，清 0 无副作用；停用时放行原值。
     * 标准类名都解析不到（改名/换库）时返回 false，交给视图树反查兜底。
     */
    private fun hookListPaddingStructural(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader): Boolean {
        for (name in listOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView",
        )) {
            val setPadding = runCatching {
                Class.forName(name, true, loader).getDeclaredMethod(
                    "setPadding",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                )
            }.getOrNull() ?: continue
            facade.mapIntArg(setPadding, 3) { original -> if (config().enabled) 0 else original }
            structuralHookDone = true
            facade.log(
                android.util.Log.INFO,
                "structural fallback: hooked $name.setPadding, list bottom padding zeroed while enabled",
            )
            return true
        }
        facade.log(android.util.Log.WARN, "structural fallback: RecyclerView not resolvable by name, will probe from view tree after install")
        return false
    }

    /**
     * 视图树反查兜底（标准类名全部失配时的最终手段）：从视图树取一个真实列表实例，
     * 沿父类链 hook 所有声明 setPadding(int,int,int,int) 的列表自有类（覆盖子类重写；
     * 到 ViewGroup 为止，绝不触碰 View 基类，否则进程内所有视图的底 padding 都会被清），
     * 并把现存列表已落地的底 padding 主动清 0（hook 只能拦截之后的调用）。
     * 视图树异步加载，找不到列表时不置完成标记，由装配后的定时任务重试。
     */
    private fun applyStructuralFallbackFromViewTree(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val queue = ArrayDeque<View>()
        queue.add(content)
        var firstList: View? = null
        while (queue.isNotEmpty() && firstList == null) {
            val v = queue.removeFirst()
            if (v.javaClass.name.contains("RecyclerView")) firstList = v
            if (v is ViewGroup) for (i in 0 until v.childCount) v.getChildAt(i)?.let { queue.add(it) }
        }
        val list = firstList ?: run {
            facade.log(android.util.Log.INFO, "structural fallback: no RecyclerView in view tree yet, will retry")
            return
        }
        var hooked = 0
        var c: Class<*>? = list.javaClass
        while (c != null && c != ViewGroup::class.java) {
            val setPadding = runCatching {
                c.getDeclaredMethod(
                    "setPadding",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                )
            }.getOrNull()
            if (setPadding != null) {
                facade.mapIntArg(setPadding, 3) { original -> if (config().enabled) 0 else original }
                hooked++
            }
            c = c.superclass
        }
        if (hooked == 0) {
            facade.log(android.util.Log.WARN, "structural fallback: no setPadding declared in ${list.javaClass.name} hierarchy")
            return
        }
        structuralHookDone = true
        var swept = 0
        fun sweep(v: View?) {
            if (v == null) return
            if (v.paddingBottom > 0 && v.javaClass.name.contains("RecyclerView") && config().enabled) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                swept++
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) sweep(v.getChildAt(i))
        }
        sweep(content)
        facade.log(
            android.util.Log.INFO,
            "structural fallback (view tree): hooked setPadding on $hooked list classes, swept $swept existing lists",
        )
    }

    /**
     * 小白条区域呈现（信息流在小白条区域透出的根本保障）：
     * 应用会在切页/换主题时把导航栏颜色设为页面底色（不透明）。窗口本身 E2E
     * （fit 排除了 navigationBars），但 vivo 在 edge-to-edge=0 判定下会把这层颜色
     * 画成窗口底部的实体色条，正好盖住小白条区域的信息流——实测表现为
     * "首启/切页后不显示、后台重进后恢复"（后台重进走 adapterEdgeChange → g0.G()
     * 会把颜色改回透明色 appstore_transparent01）。
     * 应用所有设色路径（u0.c/s/u/v、g0.G、切页换肤）最终都收口到框架的
     * PhoneWindow.setNavigationBarColor——吞掉非透明设色即可一次覆盖全部来源，
     * 且不依赖混淆类名、随商店更新免疫。透明色放行（模块 onResume 双保险
     * 传入的正是透明），停用时全部放行（重启商店还原）。
     */
    private fun hookNavigationBarPresentation(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        for (method in listOf("setNavigationBarColor", "setNavigationBarDividerColor")) {
            val m = runCatching {
                Class.forName("com.android.internal.policy.PhoneWindow", true, loader)
                    .getDeclaredMethod(method, Int::class.javaPrimitiveType)
            }.getOrNull()
            if (m == null) {
                facade.log(android.util.Log.WARN, "hook $method failed: PhoneWindow method not found")
                continue
            }
            facade.swallowArgIf(m, 0) { arg ->
                config().enabled && arg != android.graphics.Color.TRANSPARENT
            }
            facade.log(android.util.Log.INFO, "hooked PhoneWindow.$method: non-transparent colors swallowed while enabled")
        }
    }

    /**
     * 滑动失灵取证：记录主 Tab Activity 的触摸事件流（DOWN/UP 全记、MOVE 限频）。
     * "卡死"窗口内若事件仍在流动而页面不动 → 阻挡在视图树内部（透明遮挡层）；
     * 若事件停流且 FreezerManager 有 freeze 记录 → 进程被 vivo 冻结器冻结。
     */
    private fun hookTouchFlow(facade: HookFacade, loader: ClassLoader) {
        try {
            val dispatch = Class.forName("com.bbk.appstore.ui.AppStoreTabActivity", true, loader)
                .getDeclaredMethod("dispatchTouchEvent", android.view.MotionEvent::class.java)
            var lastMoveLogAt = 0L
            var movesSinceLog = 0
            facade.around(dispatch, { call ->
                val ev = call.args.firstOrNull() as? android.view.MotionEvent ?: return@around null
                val now = android.os.SystemClock.elapsedRealtime()
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // focus=false 说明有别的窗口盖在本窗口上接管了输入（系统通知栏/子窗口）；
                        // eventAge 异常大说明事件积压延迟送达（进程曾被冻结的特征）
                        val activity = call.thisObj as? android.app.Activity
                        val focus = activity?.window?.decorView?.hasWindowFocus()
                        val ageMs = android.os.SystemClock.uptimeMillis() - ev.eventTime
                        facade.log(
                            android.util.Log.INFO,
                            "touch DOWN (${ev.x.toInt()},${ev.y.toInt()}) focus=$focus eventAge=${ageMs}ms",
                        )
                        lastMoveLogAt = now
                        movesSinceLog = 0
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        facade.log(
                            android.util.Log.INFO,
                            "touch ${if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) "UP" else "CANCEL"} " +
                                "(${ev.x.toInt()},${ev.y.toInt()}) moves=$movesSinceLog",
                        )

                    android.view.MotionEvent.ACTION_MOVE -> {
                        movesSinceLog++
                        if (now - lastMoveLogAt > 200) {
                            facade.log(
                                android.util.Log.INFO,
                                "touch MOVE x$movesSinceLog (${ev.x.toInt()},${ev.y.toInt()})",
                            )
                            lastMoveLogAt = now
                            movesSinceLog = 0
                        }
                    }
                }
                null // 只记录，不拦截
            }, null)
            facade.log(android.util.Log.INFO, "hooked dispatchTouchEvent: touch flow logging on")
        } catch (t: Throwable) {
            facade.log(android.util.Log.WARN, "hook dispatchTouchEvent failed", t)
        }
    }

    /**
     * 手势链追踪：记录左右滑子页视图链上各组件（TouchViewPager/PullRefreshRecyclerView 等）
     * 对每个手势的处理声明（onInterceptTouchEvent/onTouchEvent 的返回值）。
     * 滑动失灵时谁声明了消费手势（返回 true），谁就是拦截者。
     * MOVE 限频防刷屏；DOWN/UP/CANCEL/拦截声明全记。
     */
    private fun hookGestureChain(facade: HookFacade, loader: ClassLoader) {
        val targets = listOf(
            "com.bbk.appstore.widget.tabview.TouchViewPager",
            "com.bbk.appstore.widget.recyclerview.PullRefreshRecyclerView",
            "com.bbk.appstore.widget.CanDisableViewPager",
            "com.bbk.appstore.widget.DetectPageSelectViewPager",
            "com.bbk.appstore.ui.rank.RankingContainerView",
            "com.vivo.springkit.nestedScroll.NestedScrollLayout",
        )
        val lastMoveLog = HashMap<String, Long>()
        var hooked = 0
        for (className in targets) {
            val clazz = try {
                Class.forName(className, true, loader)
            } catch (_: Throwable) {
                continue
            }
            for (methodName in listOf("onInterceptTouchEvent", "onTouchEvent")) {
                try {
                    val m = clazz.getDeclaredMethod(methodName, android.view.MotionEvent::class.java)
                    val key = "${className.substringAfterLast('.')}.$methodName"
                    facade.around(m, null, { call ->
                        val ev = call.args.firstOrNull() as? android.view.MotionEvent ?: return@around null
                        val actionName = when (ev.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> "DOWN"
                            android.view.MotionEvent.ACTION_UP -> "UP"
                            android.view.MotionEvent.ACTION_CANCEL -> "CANCEL"
                            android.view.MotionEvent.ACTION_MOVE -> "MOVE"
                            else -> "A${ev.actionMasked}"
                        }
                        val claimed = call.result == true
                        val now = android.os.SystemClock.elapsedRealtime()
                        val shouldLog = actionName != "MOVE" &&
                            (claimed || actionName != "DOWN" || now - (lastMoveLog[key] ?: 0) > 300)
                        if (actionName == "MOVE") {
                            if (claimed && now - (lastMoveLog[key] ?: 0) > 300) {
                                lastMoveLog[key] = now
                                facade.log(
                                    android.util.Log.INFO,
                                    "gesture $key($actionName)=${call.result} CLAIMED",
                                )
                            }
                        } else if (shouldLog) {
                            lastMoveLog[key] = now
                            facade.log(
                                android.util.Log.INFO,
                                "gesture $key($actionName)=${call.result} (${ev.x.toInt()},${ev.y.toInt()})",
                            )
                        }
                        null
                    })
                    hooked++
                } catch (_: Throwable) {
                }
            }
        }
        facade.log(android.util.Log.INFO, "gesture chain tracing on ($hooked methods)")
    }

    /**
     * 滑动失灵/小白条取证套件（一次输出三份）：
     * 1. padding dump — 带底部 padding 的 RecyclerView（g9.h.j() 清零后应全部为 0）
     * 2. bounds dump — 沿"底边最低的可见子视图"链向下：内容若到不了屏幕底，
     *    链上第一个 bottom < 屏幕高的视图就是元凶（padding/margin/高度/裁剪）
     * 3. overlay scan — 所有可见的全屏视图按 z 序输出：滑动失灵若由"透明不可滚动层"
     *    遮挡引起，覆盖层会直接现形
     */
    private fun runDiagnostics(activity: Activity, facade: HookFacade) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val screenH = content.height
        val screenW = content.width

        val padded = ArrayList<String>()
        val scrollStates = ArrayList<String>()
        // RecyclerView 的滚动状态经反射读取（目标应用的 RecyclerView 由应用类加载器加载）
        val computeRange = runCatching {
            View::class.java.getDeclaredMethod("computeVerticalScrollRange").apply { isAccessible = true }
        }.getOrNull()
        val computeExtent = runCatching {
            View::class.java.getDeclaredMethod("computeVerticalScrollExtent").apply { isAccessible = true }
        }.getOrNull()
        val computeOffset = runCatching {
            View::class.java.getDeclaredMethod("computeVerticalScrollOffset").apply { isAccessible = true }
        }.getOrNull()

        fun walkPadding(v: View?, depth: Int) {
            if (v == null || depth > 14) return
            if (v.javaClass.name.contains("RecyclerView") && scrollStates.size < 4) {
                fun via(method: java.lang.reflect.Method?): Int = runCatching {
                    method?.invoke(v) as? Int ?: -1
                }.getOrDefault(-1)
                scrollStates.add(
                    "${v.javaClass.simpleName} h=${v.height} trY=${v.translationY.toInt()} " +
                        "range=${via(computeRange)} extent=${via(computeExtent)} offset=${via(computeOffset)} " +
                        "canUp=${v.canScrollVertically(-1)} canDown=${v.canScrollVertically(1)} " +
                        "pb=${v.paddingBottom} children=${if (v is ViewGroup) v.childCount else -1}",
                )
            }
            if (v.paddingBottom > 0 && v.javaClass.name.contains("RecyclerView")) {
                padded.add("${v.javaClass.simpleName} pb=${v.paddingBottom}")
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walkPadding(v.getChildAt(i), depth + 1)
            }
        }
        walkPadding(content, 0)
        facade.log(
            android.util.Log.INFO,
            if (padded.isEmpty()) "padding dump: no RecyclerView with bottom padding"
            else "padding dump: ${padded.joinToString("; ")}",
        )
        facade.log(android.util.Log.INFO, "scroll state dump:\n${scrollStates.joinToString("\n")}")

        // 容器级留白取证：底部区域所有带 pb/bottomMargin 的视图（RecyclerView 之外的留白也会现形）
        val reserves = ArrayList<String>()
        fun walkReserves(v: View?, depth: Int) {
            if (v == null || depth > 14 || reserves.size >= 10) return
            val bottomOnScreen = runCatching {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                loc[1] + v.height
            }.getOrDefault(0)
            if (bottomOnScreen > screenH * 2 / 3) {
                val mb = (v.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                if (v.paddingBottom > 0 || mb > 0) {
                    reserves.add("${v.javaClass.simpleName} pb=${v.paddingBottom} mb=$mb bottom=$bottomOnScreen")
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walkReserves(v.getChildAt(i), depth + 1)
            }
        }
        walkReserves(content, 0)
        facade.log(
            android.util.Log.INFO,
            if (reserves.isEmpty()) "reserve dump: no bottom-region view with padding/margin"
            else "reserve dump:\n${reserves.joinToString("\n")}",
        )

        val chain = ArrayList<String>()
        fun walkBottom(v: View, depth: Int) {
            chain.add(
                "${"  ".repeat(depth)}${v.javaClass.simpleName} bottom=${v.bottom} h=${v.height} " +
                    "pb=${v.paddingBottom} trY=${v.translationY.toInt()} fsw=${v.fitsSystemWindows} vis=${v.visibility}",
            )
            if (v !is ViewGroup || depth > 12) return
            var best: View? = null
            for (i in 0 until v.childCount) {
                val child = v.getChildAt(i)
                if (child == null || child.visibility != View.VISIBLE) continue
                if (best == null || child.bottom > best!!.bottom) best = child
            }
            if (best != null) walkBottom(best, depth + 1)
        }
        walkBottom(content, 0)
        facade.log(android.util.Log.INFO, "bounds dump (screenH=$screenH):\n${chain.joinToString("\n")}")

        val overlays = ArrayList<String>()
        fun scanOverlays(v: View, depth: Int, index: Int) {
            if (v !is ViewGroup || depth > 12) return
            for (i in 0 until v.childCount) {
                val child = v.getChildAt(i) ?: continue
                val isLottie = child.javaClass.name.contains("Lottie")
                if (child.visibility == View.VISIBLE &&
                    (isLottie || (child.width >= screenW - 10 && child.height >= screenH / 2))
                ) {
                    overlays.add(
                        "d$depth.i$i ${child.javaClass.simpleName}(${child.width}x${child.height} " +
                            "bottom=${child.bottom} alpha=${child.alpha} trY=${child.translationY.toInt()} " +
                            "clickable=${child.isClickable} lottie=$isLottie)",
                    )
                }
                scanOverlays(child, depth + 1, i)
            }
        }
        scanOverlays(content, 0, 0)
        facade.log(
            android.util.Log.INFO,
            if (overlays.isEmpty()) "overlay scan: none"
            else "overlay scan (full-screen visible views):\n${overlays.joinToString("\n")}",
        )
    }

    private fun collectViewGroups(root: View, out: MutableList<ViewGroup>, depth: Int) {
        if (depth > 12) return
        if (root is ViewGroup) {
            out.add(root)
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.let { collectViewGroups(it, out, depth + 1) }
            }
        }
    }

    private fun View.isBottomAligned(): Boolean {
        return when (val lp = layoutParams) {
            is FrameLayout.LayoutParams -> lp.gravity and android.view.Gravity.BOTTOM != 0
            is android.widget.RelativeLayout.LayoutParams ->
                lp.rules[android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM] != 0

            else -> false
        }
    }

    private fun hasVisibleText(view: ViewGroup): Boolean {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is TextView && child.visibility == View.VISIBLE) return true
            if (child is ViewGroup && hasVisibleText(child)) return true
        }
        return false
    }
}
