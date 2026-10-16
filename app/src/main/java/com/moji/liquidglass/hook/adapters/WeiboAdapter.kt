package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.glass.TabExtractor
import com.moji.liquidglass.hook.HookFacade

/**
 * 微博（com.sina.weibo，第三方应用）适配器。基准版本 16.10.0（versionCode 8202）。
 *
 * - 底栏：MainTabActivity 布局（maintabs_left.xml）内 id `main_radio` 的 LinearLayout，
 *   由 `com.sina.weibo.bottombar.BottomTabManager`（混淆名 g）经 TabController（混淆名 w）
 *   填充；资源名未混淆，getIdentifier 可用（aapt 实证，区别于 vivo 官网的 arsc 混淆）。
 * - 每 tab 视图：`com.sina.weibo.bottombar.view.TabViewGroupV2`（FrameLayout 子类，
 *   非混淆类名；TabController 先包一层 FrameLayout 再挂到 main_radio）。**全自绘**：
 *   图标/角标/文字都在 onDraw 的 Canvas 上画，普通 tab 无任何子视图——现有
 *   WHOLE_TAB 的"图标子视图镜像 + 玻璃层自绘标签"模式装不上，走 WHOLE_TAB_FULL
 *   整条目镜像（引擎为此新增的通用模式）。
 * - 选中态：应用不用 isSelected，用内部 checked 标志 + `state_checked` drawable 状态
 *   （`setChecked(boolean, boolean)` 是唯一翻转口；performClick/应用切页/拖动预览都经它）。
 *   安装 `setChecked → setSelected` 桥接 hook，让引擎逐帧 isSelected 读数、拖动预览
 *   冻结、初次装配选中下标全部零改动工作。
 * - 配色：文字色同样由 Canvas 直绘（无 TextView），hook `setTextColor`/`setTextSelectedColor`
 *   （ITabView 接口方法，TabController 应用主题色时调用）捕获内容色/强调色，经
 *   TabExtractor.colorOverride 缝注入。液泡折射不染色（条目自带选中态配色，染色会盖掉）。
 * - 显隐跟随：进微博正文等沉浸场景应用对 main_radio 做 setTranslationY + setVisibility，
 *   玻璃侧经 mirrorVisibleGate 按容器 isShown + translationY 跟随。
 * - 内容延伸：未做（未发现列表底部留白问题；第三方应用更新频繁，先保持最小 hook 面）。
 */
object WeiboAdapter : AppAdapter {

    override val targetPackage: String = "com.sina.weibo"
    override val displayName: String = "微博"
    override val tabActivityClassName: String? = "com.sina.weibo.MainTabActivity"

    private const val BAR_ID_NAME = "main_radio"
    private const val ITEM_CLASS = "com.sina.weibo.bottombar.view.TabViewGroupV2"
    private const val ACTOR_CLASS = "com.sina.weibo.startup.actor.maintab.MainMaintabActor"

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = null,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = true,
        // TabViewGroupV2 公开方法 setChecked(boolean)：唯一选中翻转口，非混淆，显式声明留档
        itemCheckedSetterMethodName = "setChecked",
        // 条目自带选中态配色（图标 state_checked 选择器 + 文字色切换），不整层染色
        tintRefractionContent = false,
    )

    // —— hook 捕获的运行时状态（installAppHooks 写入，createExtractor/门控读取）——

    /** TabViewGroupV2.setTextColor/setTextSelectedColor 捕获的内容色/强调色（ARGB int）。 */
    @Volatile
    private var capturedContentColor: Int? = null
    @Volatile
    private var capturedAccentColor: Int? = null

    /** 当前活跃会话（onSessionCreated 写入）：切页副作用吞咽 hook 读它的预览标志。 */
    @Volatile
    private var activeSession: GlassSession? = null

    /** 装配所在 Activity（onSessionCreated 写入）：removeBottomShadow 用。 */
    @Volatile
    private var hostActivity: Activity? = null

    private fun capturedColors(): Pair<Color?, Color?>? {
        val content = capturedContentColor?.let(::Color)
        val accent = capturedAccentColor?.let(::Color)
        if (content == null && accent == null) return null
        return content to accent
    }

    override fun createExtractor(): TabExtractor = TabExtractor(descriptor).apply {
        colorOverride = { capturedColors() }
    }

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun findTabBar(content: ViewGroup): View? {
        // 首选资源 id：main_radio（LinearLayout，Horizontal，贴底，BottomTabManager 填充）
        val barId = content.resources.getIdentifier(BAR_ID_NAME, "id", content.context.packageName)
        if (barId != 0) {
            val byId = ArrayList<View>(1)
            fun walkId(v: View?) {
                if (v == null || byId.isNotEmpty()) return
                if (v.id == barId && v is ViewGroup && v.childCount >= 2) {
                    byId.add(v)
                    return
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walkId(v.getChildAt(i))
            }
            walkId(content)
            if (byId.isNotEmpty()) return byId.first()
        }
        // 兜底：任一 TabViewGroupV2 所在条目的父容器（TabController: wrapper FrameLayout
        // 的 parent 就是 main_radio）。条目类名非混淆，可直接匹配。
        val byItem = ArrayList<View>(1)
        fun walkItem(v: View?) {
            if (v == null || byItem.isNotEmpty()) return
            if (isTabItem(v)) {
                (v.parent as? View)?.let { byItem.add(it) }
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walkItem(v.getChildAt(i))
        }
        walkItem(content)
        return byItem.firstOrNull()
    }

    override fun tabViewsOf(bar: View): List<View> {
        val items = ArrayList<View>()
        if (bar !is ViewGroup) return items
        for (i in 0 until bar.childCount) {
            val wrapper = bar.getChildAt(i) ?: continue
            // 条目 = wrapper 内的 TabViewGroupV2（每 tab 恰一个）；类名失效时退回 wrapper
            // 本身（整条目镜像像素相同，仅多一层透明容器），保证更新免疫
            items.add(findTabItem(wrapper) ?: wrapper)
        }
        return items
    }

    private fun isTabItem(v: View): Boolean =
        v.javaClass.name == ITEM_CLASS || v.javaClass.simpleName == "TabViewGroupV2"

    private fun findTabItem(root: View): View? {
        if (isTabItem(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findTabItem(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // 选中态桥接：setChecked(Z, Z) 是唯一翻转口（一参重载内部委托它；performClick、
        // BottomTabManager 切页、拖动预览 wholeTabSetChecked 全部经此）。
        // after 同步到标准 isSelected → 引擎逐帧选中读数/预览冻结/装配初值零改动工作。
        // **initialize 必须为 false**：包加载期 Application 未创建，TabViewGroupV2.<clinit>
        // 会连锁初始化 feed.business.j → GreyScaleUtils → WBDebug（用 Context → NPE），
        // 类进入错误态后应用启动任务连环 NoClassDefFoundError 闪退（v1.8 实翻车）。
        runCatching {
            val itemClass = Class.forName(ITEM_CLASS, false, loader)
            val setChecked = itemClass.getDeclaredMethod(
                "setChecked",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            facade.around(setChecked, null) { call ->
                val checked = call.args.firstOrNull() as? Boolean ?: return@around null
                (call.thisObj as? View)?.isSelected = checked
                null
            }
            // 文字配色捕获：TabController 应用主题/深色模式时调用（Canvas 直绘的文字色
            // 无 TextView 可取，这是唯一可靠的运行时取色口）
            runCatching {
                val setContent = itemClass.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType)
                facade.around(setContent, null) { call ->
                    (call.args.firstOrNull() as? Int)?.let { capturedContentColor = it }
                    null
                }
            }.onFailure { facade.log(android.util.Log.WARN, "hook TabViewGroupV2.setTextColor failed", it) }
            runCatching {
                val setSelected = itemClass.getDeclaredMethod("setTextSelectedColor", Int::class.javaPrimitiveType)
                facade.around(setSelected, null) { call ->
                    (call.args.firstOrNull() as? Int)?.let { capturedAccentColor = it }
                    null
                }
            }.onFailure { facade.log(android.util.Log.WARN, "hook TabViewGroupV2.setTextSelectedColor failed", it) }
            facade.log(android.util.Log.INFO, "hooked Weibo TabViewGroupV2 selection bridge + color capture")
        }.onFailure {
            // 桥接失效的表现：玻璃液泡停在装配时的 tab 不跟随切页（镜像/点击转发仍工作）
            facade.log(android.util.Log.WARN, "hook Weibo TabViewGroupV2 failed (selection sync degraded)", it)
        }
        // 内容延伸：信息流底部储备归零。MainTabActivity 主布局（maintabs.xml）的
        // @android:id/tabcontent 静态 paddingBottom=44dp（dimen/toolbar_height），
        // MainMaintabActor.e(int) 是它的唯一运行时写入口（启动 ak()→a(iH,…)、沉浸切换
        // a(int,boolean) 全部经 e() 落 padding）——不归零则信息流不进底栏区，玻璃采样
        // 条带透出 activity 窗口背景（开屏图），模糊/折射无从体现。
        // e() 归零经 mapIntArg 强制参数为 0，随模块开关直通恢复应用原生行为。
        runCatching {
            val actorClass = Class.forName(ACTOR_CLASS, false, loader)
            val setReserve = actorClass.getDeclaredMethod("e", Int::class.javaPrimitiveType)
            facade.mapIntArg(setReserve, 0) { 0 }
            facade.log(android.util.Log.INFO, "hooked MainMaintabActor.e(int) -> content reserve 0")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook MainMaintabActor.e(int) failed (feed may not extend behind glass)", it)
        }
        // 拖动悬停预览的切页副作用吞咽：微博切页 = 条目 setChecked(true) 的选中变化监听器
        // （BottomTabManager$b）直接 TabHost.setCurrentTabByTag——预览翻转选中外观会误触
        // 切页（未松手页面已变）。预览激活期间（session.isTabPreviewActive）吞掉该调用，
        // 松手后正式点击经 onTabClicked → performClick（标志已复位）正常切页。
        // TabHost 为平台类，微博进程内仅主页面这一个 TabHost 在跑，预览门控下无外溢。
        runCatching {
            val tabHostClass = Class.forName("android.widget.TabHost", false, loader)
            val switchTab = tabHostClass.getDeclaredMethod("setCurrentTabByTag", String::class.java)
            facade.around(switchTab, { _ ->
                if (activeSession?.isTabPreviewActive == true) false else null
            }, null)
            facade.log(android.util.Log.INFO, "hooked TabHost.setCurrentTabByTag preview gate")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook TabHost.setCurrentTabByTag failed (drag preview may switch pages)", it)
        }
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 显隐门控：沉浸场景（微博正文/全屏视频）应用对 main_radio 做平移下滑 +
        // visibility INVISIBLE。INVISIBLE 走 isShown 判定；平移过程按过半即藏，避免玻璃
        // 滞留在已滑走的内容上。
        session.mirrorVisibleGate = {
            val bar = session.tabContainer
            bar.isShown && bar.translationY < bar.height * 0.5f
        }
        // 静态储备清理：e(int) hook 只覆盖运行时写入；布局 xml 里 tabcontent 的
        // paddingBottom=44dp 在 e() 首次触发前（以及布局重 inflate 后）仍然存在，
        // 装配时一次性归零。
        hostActivity = activity
        zeroTabContentReserve(activity, facade)
        removeBottomShadow(activity, facade)
        activeSession = session
    }

    /**
     * 原底栏顶部 1px 阴影线（iv_bottom_shadow，0x7f0a1902 用户 dump 实证）：玻璃栏更高
     * 且为胶囊形，它会从玻璃上边缘露出一条"边界线"。GONE 会被应用的启动/沉浸路径翻回
     * VISIBLE，故**从父布局移除真身**；但应用对它有多处裸引用（MainMaintabActor.G() 的
     * `findViewById(gf).setBackgroundColor(...)` 不判空；BaseMaintabActor.f(int) 还会
     * **强转 `(ImageView) findViewById(gf)`**——onResume/insets 分发路径）——真身移除后
     * findViewById 返回 null 或类型不符 → 稳定闪退（v2.1 NPE / v2.2 ClassCastException
     * 各实翻一次）。因此移除后必须在原位置放回一个**同类型（ImageView）、0×0、GONE 的
     * 替身**（同 id）：应用对该 id 的全部写操作与强转都安全落在替身上，替身无图无背景
     * 且 0×0 + GONE → 永不可见。仅装配时调用一次（视图树重建→重装会自然再移除/替换）。
     */
    private fun removeBottomShadow(activity: Activity?, facade: HookFacade) {
        if (activity == null) return
        runCatching {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val id = decor.resources.getIdentifier("iv_bottom_shadow", "id", activity.packageName)
                .takeIf { it != 0 } ?: 0x7f0a1902
            var replaced = 0
            fun walk(v: View?) {
                if (v == null) return
                if (v.id == id && v.parent is ViewGroup) {
                    val parent = v.parent as ViewGroup
                    val index = parent.indexOfChild(v)
                    parent.removeView(v)
                    // 替身必须是 ImageView（应用侧有 (ImageView) findViewById 强转）；
                    // 0×0 + GONE + 无图无背景：吸收全部写操作且永不可见
                    val stub = android.widget.ImageView(activity).apply {
                        this.id = id
                        visibility = View.GONE
                    }
                    parent.addView(stub, index.coerceAtLeast(0), android.widget.FrameLayout.LayoutParams(0, 0))
                    replaced++
                    return
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(decor)
            facade.log(
                android.util.Log.INFO,
                if (replaced > 0) "weibo: replaced $replaced iv_bottom_shadow with 0x0 ImageView stub (id=0x${id.toString(16)})"
                else "weibo: iv_bottom_shadow not found (id=0x${id.toString(16)})",
            )
        }.onFailure { facade.log(android.util.Log.WARN, "weibo remove bottom shadow failed", it) }
    }

    /** 窗口内所有 @android:id/tabcontent 的底部储备归零（静态 xml 来源，见 installAppHooks 注释）。 */
    private fun zeroTabContentReserve(activity: Activity?, facade: HookFacade) {
        if (activity == null) return
        runCatching {
            val decor = activity.window?.decorView as? ViewGroup ?: return
            var fixed = 0
            fun walk(v: View?) {
                if (v == null) return
                if (v.id == android.R.id.tabcontent && v is ViewGroup && v.paddingBottom > 0) {
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                    fixed++
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(decor)
            if (fixed > 0) {
                facade.log(android.util.Log.INFO, "weibo content-extend: zeroed $fixed tabcontent reserve(s)")
            }
        }.onFailure { facade.log(android.util.Log.WARN, "weibo content-extend sweep failed", it) }
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }
}
