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
 * - 内容延伸（信息流沉浸）：全部 tab 页列表的底栏高度留白取自混淆 getter
 *   `ya.h.j()`（10.4.81.0）/ `ya.f.h()`（10.6.21.0；ya.h 整体改名 ya.f、方法 j→h，
 *   语义逐字对应：沉浸判定 ? i() + w1.b : i()，i() = 66dp 底栏高度（R.dimen.main_tab_height，
 *   兜底 h0.b.j()）；10.3.60.0 为 g9.h.j()）。
 *   精准方案：10.4.81.0 改写单点分发入口 zb.h.K0(int)；10.6.21.0 起单点被拆成各页面
 *   applier，且落地分两类（多数 View.setPadding、首页楼层页 footer View）——APPLIER =
 *   首页控制器分发 ac.i.M0(int)（一次覆盖 padding/footer/二楼 margin）+ 楼层页 footer
 *   出口 xb.k.S1(int) + 平台 View.setPadding 收口**并存**（分类/排行/管理页直接落
 *   padding）。全部只改落地、getter 保持真实值（下拉刷新状态机不受影响）。
 * - AIOS 新底栏（10.6.21.0 新增分支）：`bb.f.e()` 为真时应用隐藏 TabWidget/TabView 链路、
 *   改用 originui `VBottomNavigationView` 悬浮卡片（id `v_bottom_navigation`，70dp/距底 30dp）。
 *   触发 = ROM≥6.0（FtBuild.getRomVersion）+ isAIOSUiEnabled + aiosEffectConfig 开 + 非灰色
 *   模式 + 非节日皮肤；旧系统不触发（仍走 TabWidget 链路）。该分支本轮未适配，触发时
 *   findTabBar 找不到 TabView 会静默不装配（无崩溃），见 №01.3/№01.7。
 * - 可见性修补：导航栏透明（小白条区域呈现），见 applyTransparentNavigationBar。
 */
object AppStoreAdapter : AppAdapter {

    override val targetPackage: String = "com.bbk.appstore"
    override val displayName: String = "vivo 应用商店"
    override val tabActivityClassName: String? = "com.bbk.appstore.ui.AppStoreTabActivity"
    override val baselineVersions: List<String> = listOf("10.4.81.0", "10.6.21.0")

    private const val TAB_VIEW_CLASS = "com.bbk.appstore.ui.tab.TabView"

    /**
     * 留白 getter 候选链（类名 × 方法名，按序解析、逐个 runCatching）：
     * 旧名 `ya.h.j`（10.4.81.0）在前、新名 `ya.f.h`（10.6.21.0）在后。
     *
     * 为什么旧名优先：10.6.21.0 已实证 `ya.h` 整体改名（ya 包内仅 a~f，无 h），旧名缺失 =
     * 自然回退到新名，顺序对新版没有影响；而窗口内免适配版 10.5.x 未必有 `ya.f`、若有也可能是
     * 语义无关的同名类——旧名优先可保证旧基线不会错绑（§0.6.3 窗口门槛）。
     */
    private val paddingGetterCandidates = listOf(
        "ya.h" to "j",
        "ya.f" to "h",
    )

    /**
     * 留白落地 applier 候选（类名 × 方法名 × 是否需要平台收口并存）。
     *
     * - `zb.h.K0(int)`（10.4.81.0）：单点分发，一处覆盖全部页面 → 不需要平台收口并存
     *   （旧版行为逐位不变、§0.4 红线）。
     * - `ac.i.M0(int)`（10.6.21.0）：首页推荐控制器自己的分发（`RecommendFragment
     *   .notifyNavigationbarChange` 反查所得）——一次覆盖首页全部落地：分页器的
     *   `View.setPadding`、楼层页 footer、二楼滑层 bottomMargin；与旧 K0 同构
     *   （委托 `A.a2(i)` + 遍历 `B` 的 `t(i)`）。其余 Fragment（分类/排行/管理页）
     *   各自直接落地 padding，仍需要平台收口并存。
     * - `xb.k.S1(int)`（10.6.21.0）：首页楼层页的 **footer 机制**——`S1(留白)` 先把列表
     *   bottom padding 归 0、再按留白高度 add 一个透明 footer View（`WrapRecyclerView.Q`）；
     *   作为 M0 失配时的 footer 兜底（M0 命中时 S1 收到的已是 0，重复无害），同样需要
     *   平台收口并存。
     */
    private class PaddingApplierCandidate(
        val className: String,
        val methodName: String,
        val needsStructuralSink: Boolean,
    )

    private val paddingApplierCandidates = listOf(
        PaddingApplierCandidate("zb.h", "K0", needsStructuralSink = false),
        PaddingApplierCandidate("ac.i", "M0", needsStructuralSink = true),
        PaddingApplierCandidate("xb.k", "S1", needsStructuralSink = true),
    )

    /**
     * 二楼平移跟随候选链（单 float 参数，读取位移后放行原方法）：
     * 首选 `AppStoreTabActivity.translateTabController(float)`——非混淆方法名、两版都在，
     * 且是二楼下拉回调的唯一入口（10.4.81.0 经 zb.h.g0 → 本方法 → c.h0；10.6.21.0 经
     * ac.i.i0 → 本方法 → tab/e.U → 当前底栏实现 A(F)）。
     * 回退（保底）：旧宿主 `ui.tab.c.h0(float)`，再退到新版两套宿主的 `A(float)`。
     */
    private val translationCandidates = listOf(
        "com.bbk.appstore.ui.AppStoreTabActivity" to "translateTabController",
        "com.bbk.appstore.ui.tab.c" to "h0",
        "com.bbk.appstore.ui.tab.b" to "A",
        "com.bbk.appstore.ui.tab.i" to "A",
    )

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

    /**
     * 应用专属内容活动信号类（引擎单管线声明，hook/分发/降帧由引擎统一负责）：
     * 自研内容容器 onDraw（滚动/翻页每帧触发、静止不触发）。Lottie 图标动画不在此列——
     * 中央默认列表的 LottieAnimationView walk-up 落 ImageView.onDraw，已覆盖全部
     * ImageView 实例（含 Lottie 视图）。
     */
    override val contentActivitySignalClasses: List<String> = listOf(
        "com.bbk.appstore.widget.tabview.TouchViewPager",
        "com.bbk.appstore.widget.recyclerview.PullRefreshRecyclerView",
        "com.bbk.appstore.widget.CanDisableViewPager",
        "com.bbk.appstore.widget.DetectPageSelectViewPager",
        "com.bbk.appstore.ui.rank.RankingContainerView",
        "com.vivo.springkit.nestedScroll.NestedScrollLayout",
    )

    // 平台收口直接 hook View.setPadding（见 hookListPaddingStructural v2 注释）：既是锚点
    // 全失配时的兜底，也是 10.6.21.0 起 APPLIER 的并存收口（与首页分发 / footer 出口并存，
    // 覆盖直接落 padding 的页面）。安装不再有条件分支，无需装配后的视图树反查重试。

    /** 当前装配了玻璃栏的 Activity（二楼平移回调触发取证时定位目标）。 */
    @Volatile
    private var glassActivityRef: WeakReference<Activity>? = null

    /** 二楼平移 hook 最近一次位移值与时间戳（容器 translationY 兜底跟随的冲突仲裁）。 */
    @Volatile
    private var lastHookTranslation = 0f

    @Volatile
    private var lastHookTranslationAt = 0L

    // —— 装配自检（排障用）：实际命中的锚点名，会话创建时汇总成一行日志 ——
    @Volatile
    private var hitGetterName: String? = null

    @Volatile
    private var hitApplierNames: String = "none"

    @Volatile
    private var hitTranslationName: String? = null

    override fun createExtractor(): TabExtractor = extractor

    /** 首选 @android:id/tabs（TabWidget）且首子项为 TabView 类，返回其父容器（#tab_container）；
     *  兜底：任意贴底、含 3–6 个"各带文字 TextView"子项的 ViewGroup（应用更新容错）。
     *  注意：10.6.21.0 的 AIOS 新底栏分支会把 TabWidget 整个隐藏、改用 VBottomNavigationView，
     *  此时两条路径都会落空 → 本函数返回 null = 不装配（静默，无崩溃；该分支未适配）。 */
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
                // 直接 hook 留白 getter 清零——注意：会破坏下拉刷新状态机（竖向滑动失灵的根因）
                if (!hookBottomBarPadding(facade, config, loader)) {
                    // 商店自更新后混淆 getter 失配时留白清零彻底失效（信息流停在小白条上方），
                    // 自动接入不依赖混淆名的平台级收口
                    hookListPaddingStructural(facade, config, loader)
                }
            }

            ModuleConfig.CONTENT_EXTEND_APPLIER -> {
                // 精准方案：getter 保持真实值（下拉刷新的拉距/阈值计算不受影响），只改最终落地留白
                installContentExtendApplier(facade, config, loader)
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
        // 取证：装配自检一行（升级排查先看它——命中名/MISS 一目了然，见 №01.5）
        facade.log(
            android.util.Log.INFO,
            "appstore session self-check: contentExtend=${config().contentExtend}, " +
                "getter=${hitGetterName ?: "MISS"}, applier=[$hitApplierNames], " +
                "translation=${hitTranslationName ?: "MISS"}, " +
                "bar=${session.tabContainer.javaClass.simpleName}@0x" +
                Integer.toHexString(session.tabContainer.id),
        )
        session.afterForward = {
            handler.post { if (!activity.isFinishing && !activity.isDestroyed) runDiagnostics(activity, facade) }
        }
        // 二楼平移的**兜底跟随**（不依赖任何混淆方法是否命中）：应用"二楼"逐帧平移的
        // 对象就是底栏容器 #tab_container 自身（10.6.21.0：tab/e.U → 宿主 ui/tab/b.A(F)
        // → tab_container.setTranslationY；旧版 c.h0(F) 同样作用于 tab_container），所以
        // 逐 tick 直接读原生容器的 translationY 作为权威值，只取下滑分量（≥0，与
        // TaobaoAdapter 同款语义）。hook 通道保留：hook 刚写过值（250ms 内）且容器尚未
        // 跟上时以 hook 值为准（避免"hook 先写、渲染后读"的一帧回退），否则以容器实测为准。
        session.mirrorVisibleGate = {
            val now = android.os.SystemClock.elapsedRealtime()
            val hookFresh = now - lastHookTranslationAt < 250L
            val containerTy = session.tabContainer.translationY.coerceAtLeast(0f)
            val ty = if (containerTy > 0f || !hookFresh) {
                containerTy
            } else {
                lastHookTranslation.coerceAtLeast(0f)
            }
            if (com.moji.liquidglass.glass.GlassBarState.translationY != ty) {
                com.moji.liquidglass.glass.GlassBarState.translationY = ty
            }
            true
        }
        // 静止降帧由 TargetHook 会话创建期统一接线（引擎单管线，本适配器零设置）；
        // 旧事件驱动 tick 通道已退役并入该管线（活动窗口满帧率 + 静止 30fps/1Hz 深度静止）
        // 取证：装配后定时 dump（覆盖 tab 切换后的视图状态）
        for (delay in longArrayOf(3_000, 8_000, 15_000, 25_000, 40_000)) {
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    runDiagnostics(activity, facade)
                }
            }, delay)
        }
    }

    /**
     * 二楼下拉时玻璃底栏跟随位移：原软件首页顶端下拉（二楼手势）期间，应用逐帧把底栏
     * 容器随内容平移。这里把位移值写入 GlassBarState 驱动玻璃栏跟随，并放行原方法
     * （对应用状态机完整无害）。
     *
     * 主锚点取非混淆的 `AppStoreTabActivity.translateTabController(float)`（两版都在、
     * 是唯一入口），混淆宿主方法作回退；另在 [onSessionCreated] 里每 tick 读底栏容器
     * 自身 `translationY` 兜底（hook 全部失配时照常跟随）。全部失效才固定不动、无崩溃。
     */
    private fun hookBarTranslation(facade: HookFacade, loader: ClassLoader) {
        for ((className, methodName) in translationCandidates) {
            val method = runCatching {
                Class.forName(className, true, loader)
                    .getDeclaredMethod(methodName, Float::class.javaPrimitiveType)
            }.getOrNull() ?: continue
            runCatching {
                facade.around(method, { call ->
                    // 只取下滑分量（≥0，与容器兜底跟随、TaobaoAdapter 同语义）
                    val translation = (call.args.firstOrNull() as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f
                    com.moji.liquidglass.glass.GlassBarState.translationY = translation
                    val previous = lastHookTranslation
                    lastHookTranslation = translation
                    lastHookTranslationAt = android.os.SystemClock.elapsedRealtime()
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
            }.onSuccess {
                hitTranslationName = "$className.$methodName"
                facade.log(
                    android.util.Log.INFO,
                    "hooked ${className.substringAfterLast('.')}.$methodName(float): " +
                        "glass bar follows second-floor pull",
                )
                return
            }.onFailure { t ->
                facade.log(
                    android.util.Log.WARN,
                    "hook ${className.substringAfterLast('.')}.$methodName failed, trying next candidate",
                    t,
                )
            }
        }
        facade.log(
            android.util.Log.WARN,
            "hook bar translation failed (all candidates missed), glass bar stays fixed during second-floor pull",
        )
    }

    /**
     * 内容延伸的精准方案（APPLIER）安装入口：留白 getter 保持真实值（下拉刷新的拉距/阈值
     * 计算不受影响），只改写"把留白值落地"的出口。
     *
     * 候选见 [paddingApplierCandidates]，**逐个全部尝试**（不复用"首个命中即止"）：10.6.21.0
     * 起应用把单点分发拆成各页面 applier、且落地机制分两类（padding / footer），只挂一个会
     * 漏页面。任一候选声明 [PaddingApplierCandidate.needsStructuralSink] 或全部候选都失配时，
     * 再叠加平台 `View.setPadding` 收口；10.4.81.0 单点 K0 命中且无并存需求 → 不装收口，
     * 旧版行为逐位不变（§0.4）。
     */
    private fun installContentExtendApplier(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        var hooked = 0
        var needSink = false
        for (candidate in paddingApplierCandidates) {
            val applier = runCatching {
                Class.forName(candidate.className, true, loader)
                    .getDeclaredMethod(candidate.methodName, Int::class.javaPrimitiveType)
            }.getOrNull() ?: continue
            runCatching {
                facade.mapIntArg(applier, 0) { 0 }
            }.onSuccess {
                hooked++
                if (candidate.needsStructuralSink) needSink = true
                hitApplierNames = if (hitApplierNames == "none") {
                    "${candidate.className}.${candidate.methodName}"
                } else {
                    "$hitApplierNames,${candidate.className}.${candidate.methodName}"
                }
                facade.log(
                    android.util.Log.INFO,
                    "hooked ${candidate.className}.${candidate.methodName}(int): " +
                        "bottom padding applier zeroed (getter intact)",
                )
            }.onFailure { t ->
                facade.log(
                    android.util.Log.WARN,
                    "hook ${candidate.className}.${candidate.methodName} failed, trying next candidate",
                    t,
                )
            }
        }
        if (hooked == 0) {
            needSink = true
            facade.log(
                android.util.Log.WARN,
                "hook bottom padding applier failed (all candidates missed, store updated?), " +
                    "falling back to structural list padding hook",
            )
        }
        if (needSink) hookListPaddingStructural(facade, config, loader)
    }

    /**
     * 列表底部留白清零（让内容延伸到玻璃栏与小白条下方）：
     * 应用商店所有 tab 页列表的底栏高度留白统一取自静态 getter（10.4.81.0 = `ya.h.j()`，
     * 10.6.21.0 = `ya.f.h()`——ya.h 整体改名为 ya.f、方法 j→h，两版语义逐字对应；
     * 10.3.60.0 为 `g9.h.j()`），首页信息流/分类/排行/管理页/二级子页/Flutter 桥全部经过它。
     * 模块启用时返回 0，停用时走原方法（原底栏需要留白）。候选链见
     * [paddingGetterCandidates]，全部失配自动降级到平台收口兜底。
     */
    private fun hookBottomBarPadding(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader): Boolean {
        for ((className, methodName) in paddingGetterCandidates) {
            val getter = runCatching {
                Class.forName(className, true, loader).getDeclaredMethod(methodName)
            }.getOrNull() ?: continue
            runCatching {
                facade.around(getter, { if (config().enabled) 0 else null }, null)
            }.onSuccess {
                hitGetterName = "$className.$methodName"
                facade.log(
                    android.util.Log.INFO,
                    "hooked $className.$methodName(): list bottom padding zeroed while enabled",
                )
                return true
            }.onFailure { t ->
                facade.log(
                    android.util.Log.WARN,
                    "hook $className.$methodName failed, trying next candidate",
                    t,
                )
            }
        }
        facade.log(
            android.util.Log.WARN,
            "hook bottom bar padding getter failed (all candidates missed, store updated?), " +
                "falling back to structural list padding hook",
        )
        return false
    }

    /**
     * 内容延伸的平台收口（不依赖混淆类名）：平台级 `View.setPadding` 收口——
     * 底 padding ≥ 底栏高度（留白 = 底栏高度 + 导航栏 inset，必然 ≥ 底栏高度）实时归 0，
     * 停用时放行原值。语义对齐 vivo 官网 ⑤ 收口（手册 §4.2）。
     *
     * 双角色：① getter/applier 锚点全失配时的兜底；② **10.6.21.0 起 APPLIER 精准方案的
     * 并存放口之一**——该版把单点分发拆进各页面 applier，大多数页面仍以 `View.setPadding`
     * 落地（`xb.k.a2`/`eb.t.G2`/`rank.d.b1`/ManageFragment 等），本收口只改"落地 padding"、
     * 留白 getter 保持真实值，与旧精准方案语义一致（下拉刷新状态机不受影响）；不落
     * padding 的首页楼层页 footer 出口由 `xb.k.S1` 候选单独 hook（见 [paddingApplierCandidates]）。
     *
     * v2 重写（10.4.81.0 升版时实证）：旧实现 hook RecyclerView.setPadding + 视图树反查
     * 列表自有类——但 `setPadding` 声明在 View 上，RecyclerView 与商店全部列表类均未重写
     * （jadx 全树 0 处 override），`getDeclaredMethod` 必然 NoSuchMethod：整条降级链
     * 自始是死代码，混淆锚点一失配内容延伸就整体失效（本次升版暴露的根因）。
     * View 为平台必在类，本收口实际总会安装成功。
     */
    private fun hookListPaddingStructural(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader): Boolean {
        val setPadding = runCatching {
            View::class.java.getDeclaredMethod(
                "setPadding",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
        }.getOrNull()
        if (setPadding == null) {
            facade.log(android.util.Log.WARN, "structural sink: View.setPadding not resolvable (unexpected)")
            return false
        }
        var hits = 0
        facade.mapIntArg(setPadding, 3) { original ->
            if (config().enabled) {
                val threshold = reserveThresholdPx(facade, loader)
                if (threshold > 0 && original >= threshold) {
                    if (++hits == 1) {
                        facade.log(
                            android.util.Log.INFO,
                            "structural sink: first reserve padding zeroed (pb=$original, threshold=$threshold)",
                        )
                    }
                    0
                } else original
            } else original
        }
        facade.log(
            android.util.Log.INFO,
            "structural sink: hooked View.setPadding, bottom >= tab bar height -> 0 while enabled",
        )
        return true
    }

    /** 收口阈值 = 底栏高度（`dimen/main_tab_height`，实测 66dp）：留白级 padding 判定线。
     *  回调内惰性解析，成功缓存；失败返回 0 = 放行原值（WARN 一次，不阻塞收口安装）。
     *
     *  解析链（10.6.21.0 升版实证）：
     *  ① 资源**名** `main_tab_height`（arsc 条目名未混淆，跨版本稳定；R 类整体搬进
     *     `com.bbk.appstore.core.j` 且字段名被重混淆后，按名解析仍然有效）；
     *  ② 新混淆 R `core.j.g3`（10.6.21.0；实值 = 0x7f070aa1 = main_tab_height，经
     *     `ya.f.i()` 的兜底取用交叉印证）；
     *  ③ 旧混淆 R `core.j.U2`（10.4.81.0；`ya.h.k()` 的兜底取用）；
     *  ④ `R$dimen.main_tab_height`（更老版本 R 类未混淆时的兜底）。
     *
     *  注：10.4.81.0 已实证 R 类就是 `core.j`（无 `R$dimen`），旧代码 ④ 在窗口内一直是
     *  死路径（阈值恒 0 = 放行）——只因旧版 applier 单点命中、收口从未被真实执行而未暴露；
     *  10.6.21.0 起收口成为 APPLIER 的并存主路径之一，阈值必须真实可用，故以 ① 为主。 */
    @Volatile
    private var reserveThresholdPxCache = 0

    @Volatile
    private var reserveThresholdWarned = false

    private fun reserveThresholdPx(facade: HookFacade, loader: ClassLoader): Int {
        reserveThresholdPxCache.takeIf { it > 0 }?.let { return it }
        val res = currentApplication()?.resources
        var source = "none"
        var id = runCatching {
            res?.getIdentifier("main_tab_height", "dimen", targetPackage) ?: 0
        }.getOrDefault(0)
        if (id != 0) source = "resource name"
        if (id == 0) {
            id = obfuscatedDimenId(loader, "com.bbk.appstore.core.j", "g3")
            if (id != 0) source = "core.j.g3"
        }
        if (id == 0) {
            id = obfuscatedDimenId(loader, "com.bbk.appstore.core.j", "U2")
            if (id != 0) source = "core.j.U2"
        }
        if (id == 0) {
            id = runCatching {
                Class.forName("com.bbk.appstore.R\$dimen", true, loader)
                    .getField("main_tab_height").getInt(null)
            }.getOrDefault(0)
            if (id != 0) source = "R\$dimen.main_tab_height"
        }
        val px = if (id == 0) 0 else runCatching {
            res?.getDimensionPixelOffset(id) ?: 0
        }.getOrDefault(0)
        if (px > 0) {
            reserveThresholdPxCache = px
            facade.log(
                android.util.Log.INFO,
                "structural sink: threshold=$px px from $source",
            )
        } else if (!reserveThresholdWarned) {
            reserveThresholdWarned = true
            facade.log(
                android.util.Log.WARN,
                "structural sink: main_tab_height resolve failed (all sources missed), padding passthrough",
            )
        }
        return px
    }

    /** 读混淆 R 类里的资源 id 字段（字段名逐版漂移，读不到静默返回 0）。 */
    private fun obfuscatedDimenId(loader: ClassLoader, className: String, fieldName: String): Int =
        runCatching {
            Class.forName(className, true, loader).getField(fieldName).getInt(null)
        }.getOrDefault(0)

    private fun currentApplication(): android.app.Application? =
        runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)
                as? android.app.Application
        }.getOrNull()

    /**
     * 小白条区域呈现（信息流在小白条区域透出的根本保障）：
     * 应用会在切页/换主题时把导航栏颜色设为页面底色（不透明）。窗口本身 E2E
     * （fit 排除了 navigationBars），但 vivo 在 edge-to-edge=0 判定下会把这层颜色
     * 画成窗口底部的实体色条，正好盖住小白条区域的信息流——实测表现为
     * "首启/切页后不显示、后台重进后恢复"（后台重进走 adapterEdgeChange → g0.G()
     * 会把颜色改回透明色 appstore_transparent01）。
     * 应用所有设色路径（10.6.21.0 = `w1.c/s/u/v`、`e1.L0`、`AppStoreTabActivity$a.b`、切页
     * 换肤；旧版 = `u0.c/s/u/v`、`g0.G`）最终都收口到框架的 PhoneWindow.setNavigationBarColor——
     * 吞掉非透明设色即可一次覆盖全部来源，且不依赖混淆类名、随商店更新免疫。透明色放行
     * （模块 onResume 双保险传入的正是透明），停用时全部放行（重启商店还原）。
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
     * 1. padding dump — 带底部 padding 的 RecyclerView（ya.h.j() 清零后应全部为 0）
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
                if (best == null || child.bottom > best.bottom) best = child
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
