package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.glass.TabExtractor
import com.moji.liquidglass.hook.HookFacade
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 高德地图（com.autonavi.minimap，第三方应用）适配器。基准版本 17.01.0.2012（versionCode 170100）。
 *
 * **底栏体系归属（v1 误锚 C3TabBar 的修正，适配前必读）**：高德存在两套 tab 体系——
 * ①`tabspage` 包的 C3TabBar（"tabBarPage" 路由呈现，配置经 `TabsPageRouter` 网络拉取，
 * 服务端驱动，非主页在用）；②`uitemplate.tab` 包的 LiteTabBar 体系。真实主页面验证链
 * （反编译交叉实证）：launcher alias → `NewMapActivity`（页面宿主）→ `MapHomeTabPage`
 * （`@PageAction("amap.basemap.action.default_page")` = 默认主页，amaphome bundle）→
 * `BootHomeTabPage` extends `NewTabHostPage` → 页面根 `NewHostContainer`（FrameLayout）
 * → 底栏 [BAR_CLASS]。本适配器只锚②。
 *
 * - 底栏：`NewHostContainer` 以 FrameLayout.LayoutParams(match_parent, 82dp+inset,
 *   gravity=BOTTOM) 挂载 LiteTabBar——**悬浮覆盖层**：tab 内容页全屏铺满容器、底栏悬浮
 *   其上（胶囊皮肤背景 `img_tabbar_bg_amap`，tabContainer 侧边距 14dp、底距 inset+16dp、
 *   高 60dp；LiteTabBar 自身背景为 82dp 渐变 scrim）。**条带后方天然是全屏内容（地图），
 *   无需微博式内容延伸手术**（v1 曾照搬，已删——悬浮结构下负 margin 手术是错误方案）。
 *   LiteTabBar.init 程序化创建唯一子容器 tabContainer（DtLinearLayout），条目逐个挂入，
 *   无布局 XML。
 * - 每 tab 视图：[ITEM_CLASS]（DtRelativeLayout 子类，非混淆）。程序化装配多形态子视图：
 *   icon_img（DtFrameLayout，含 tab_lottie_v2 ViewStub + tab_img DtImageView 27dp）、
 *   tab_name_v2 DtTextView 10dp、tab_icon 选中指示条 20×4dp、tab_bg_layer 焦点背景
 *   115×51dp、tab_big_icon 46dp ViewStub、tab_badge/tab_bubble_tv 角标气泡 ViewStub 等。
 *   条目形态服务端驱动（normalIcon/bigIcon/lottie/title/carousel）——元素级镜像锚不全，
 *   走 **WHOLE_TAB_FULL 整条目镜像**（微博通道，真机成熟）：图标/标签/角标/指示条 100%
 *   保真，玻璃侧不绘标签、不染色。
 * - 选中态：应用不用 isSelected——选中标志在 `TabItemLayoutV2.isTabSelected` 私有字段，
 *   public `setTabSelected(boolean)` 只翻字段；渲染在 controller
 *   (`com.autonavi.bundle.uitemplate.tab.b`) `setSelectItem(z, z2)` → `setTabSelected(z2)`
 *   + `updateTabStyle` 重渲染（LiteTabBar.setSelectItem(re6, z) 遍历全部条目）。安装
 *   `setTabSelected → setSelected` 桥接 hook，引擎逐帧 isSelected 读数/拖动预览冻结/
 *   装配初值零改动工作。
 * - 拖动悬停预览：条目反射通道（void(boolean)）只翻字段**不渲染**，不可用——descriptor
 *   声明 `itemCheckedApplier` 直控通道：`LiteTabBar.setSelectItem(re6, false)` 全量纯视觉
 *   重设（controller b.setSelectItem 的 z 参数未使用、无动画无切页，逆向证实纯视觉）。
 *   checked=false 一律 no-op：每次 checked=true 的 setSelectItem 会全量重设所有条目
 *   （目标选中、其余取消），引擎预览/拖回/还原/松手转发全路径终态正确。re6 定位零模型
 *   字段反射：view→parent 链找 LiteTabBar → 反射 `mTabs`（真名，List 型兜底）→
 *   tabContainer.indexOfChild(view) 取下标（与 mTabs 序对应，应用 setSelectItem 自身
 *   即按此假设实现）。
 * - 点击转发：performClick → TabItemLayoutV2.onClick → **200ms 双击判定延迟**（handler
 *   postDelayed）→ 单击 `LiteTabBar$c.onTabClick` → isTabClickable 检查 →
 *   dispatchTabClickBefore → dispatchTabClick → `NewTabHostPage.onTabClick`（同 tab =
 *   回顶语义 k()，异 tab = 切页流程 setTabClickable(false) + 过渡 + setSelectItem）。
 *   performClick 转发天然走全链路，200ms 为原生语义。
 * - 配色：文字色由 design token 体系驱动（DtTextView.proxy()），非 ColorStateList
 *   选择器——引擎 extractTextColors 只能取到当前态单色。hook `LiteTabBar.setSelectItem(re6, z)`
 *   after 读各条目 tab_name_v2 当前色（选中项 = 强调色、未选中项 = 内容色）经
 *   TabExtractor.colorOverride 注入（视图读色，零字段反射，token 实现无关；每次切页
 *   重捕获自愈）。液泡折射不染色（tintRefractionContent=false）：条目自带选中态配色。
 * - 显隐跟随：bar 视图自身的 `setVisibility(8/0)`（NewTabHostPage hideTab/onTransitionEnd）
 *   与平移 → isShown + 平移过半即藏。进 AI chat（消息 tab 分流 `amapuri://c3/chat/chatPage`，
 *   PresentPageManager non-modal present 盖挂宿主、不动宿主可见性）等 present 覆盖页玻璃
 *   滞留为**已知限制**：v3（页面栈 onCover/onAppear hook）与 v4（PresentPageManager
 *   show/dismiss 信号）两版方案真机均未达预期，按用户决策放弃修复、不再迭代（§6.7 已知限制）。
 * - 条目装饰中和（真机反馈②④）：抑制态条目（alpha=0 不可见、clickable=false 不参与
 *   触摸，唯一消费者是镜像绘制与 performClick 转发）的原生装饰在整条目镜像中被
 *   view.draw 如实复现，逐项中和：
 *   ①边缘内容偏移：应用对首/末条目（首页/我的）子视图做 ±6.5dp X 平移
 *   （`TabItemLayoutV2.applyAmapEdgeContentOffset`，CONTENT_EDGE_OFFSET_AMAP，向胶囊
 *   内收、排除 tab_bg_layer）→ 玻璃槽位居中绘制时内容偏离液泡中心 6.5dp、内容行两端
 *   内收呈"栏宽大于 tab 所需"观感——子视图 translationX 归零中和，点击转发不受平移
 *   影响。
 *   ②选中焦点背景（"液泡阴影"）：选中条目的半透明黑胶囊（tab_bg_layer 内 DtImageView，
 *   GradientDrawable 25.5dp 圆角，TabFocusBackgroundViewHolder.f 在选中瞬间显示，早于
 *   液泡弹性动画结束，且持续存在于选中条目上）与玻璃液泡叠加呈"双选中指示"，液泡
 *   显得不真实 → holder 只 toggle 子视图 visibility（f()/e()），从不触碰 tab_bg_layer
 *   容器自身——**容器 alpha=0 中和零对抗**（HWUI 跳过 alpha=0 子树，镜像不含原生胶囊，
 *   液泡成为唯一选中指示）。
 *   应用侧写入逻辑不修改；条目重附着场景由视图树重建 → isStale 重装路径自然覆盖。
 * - 帧驱动：保持默认每帧 tick（地图 TextureView 的 GL 帧更新不触发 View.invalidate，
 *   事件驱动会冻结玻璃）；采样去重按全局配置（GL 内容采样昂贵，3 次/帧 → 1 次/帧）。
 * - 已知几何差异：原生胶囊底距屏底 inset+16dp（悬浮设计），玻璃胶囊贴 inset——16dp
 *   悬浮间隙不动全局样式单源，观感可用设置页 barBottomGapDp 调整（§6.7 已知限制）。
 */
object AmapAdapter : AppAdapter {

    override val targetPackage: String = "com.autonavi.minimap"
    override val displayName: String = "高德地图"
    override val tabActivityClassName: String? = "com.autonavi.map.activity.NewMapActivity"
    override val baselineVersions: List<String> = listOf("17.01.0.2012")

    private const val BAR_CLASS = "com.autonavi.bundle.uitemplate.tab.LiteTabBar"
    private const val ITEM_CLASS = "com.autonavi.bundle.uitemplate.tab.view.TabItemLayoutV2"
    private const val BG_LAYER_ID_NAME = "tab_bg_layer"
    private const val LABELS_ID_NAME = "tab_name_v2"

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = true,
        // 条目反射通道（setTabSelected(Z)）只翻字段不渲染，预览走 applier 直控：
        // LiteTabBar.setSelectItem(re6, false) 全量纯视觉重设（见类 KDoc）
        itemCheckedApplier = { view, checked -> applyChecked(view, checked) },
        // 条目自带选中态配色（token 驱动图标/文字/焦点背景），折射染色会盖掉
        tintRefractionContent = false,
    )

    // —— hook 捕获的运行时状态（installAppHooks 写入，applier/createExtractor 读取）——

    /** LiteTabBar.setSelectItem(re6, Z) 的 Method 缓存（applier 用；双参 boolean 重载唯一）。 */
    @Volatile
    private var setSelectItemMethod: Method? = null

    /** LiteTabBar.mTabs 字段缓存（applier 用；真名失配时按 List 型 + 尺寸匹配兜底）。 */
    @Volatile
    private var tabsField: Field? = null

    /** tab_name_v2 的资源 id（配色捕获用；0 = 未解析/失配）。 */
    @Volatile
    private var labelsId: Int = 0

    /** rr0 捕获的内容色/强调色（ARGB int）。 */
    @Volatile
    private var capturedContentColor: Int? = null
    @Volatile
    private var capturedAccentColor: Int? = null

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
        // 首选：LiteTabBar 类名（非混淆）
        val byClass = ArrayList<View>(1)
        fun walkClass(v: View?) {
            if (v == null || byClass.isNotEmpty()) return
            if (v.javaClass.name == BAR_CLASS) {
                byClass.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walkClass(v.getChildAt(i))
        }
        walkClass(content)
        if (byClass.isNotEmpty()) return byClass.first()
        // 兜底：任一 TabItemLayoutV2 → parent(tabContainer) → parent(LiteTabBar)。
        // 条目类名非混淆，可直接匹配；保证类名改名时仍可经条目结构回锚。
        val byItem = ArrayList<View>(1)
        fun walkItem(v: View?) {
            if (v == null || byItem.isNotEmpty()) return
            if (isTabItem(v)) {
                ((v.parent as? View)?.parent as? View)?.let { byItem.add(it) }
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walkItem(v.getChildAt(i))
        }
        walkItem(content)
        return byItem.firstOrNull()
    }

    override fun tabViewsOf(bar: View): List<View> {
        if (bar !is ViewGroup) return emptyList()
        // LiteTabBar.init 只挂一个子容器 tabContainer（DtLinearLayout），直接子项即全部
        // 条目；以"首个子项是 TabItemLayoutV2 的子容器"识别，不依赖字段反射
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i) as? ViewGroup ?: continue
            if (child.childCount >= 2 && isTabItem(child.getChildAt(0))) {
                return (0 until child.childCount).mapNotNull { child.getChildAt(it) }
            }
        }
        // 兜底：收集子树全部 TabItemLayoutV2（树序 = tab 序）；类名失效返回空 → 探测重试
        val items = ArrayList<View>()
        fun walk(v: View?) {
            if (v == null) return
            if (isTabItem(v)) {
                items.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(bar)
        return items
    }

    private fun isTabItem(v: View?): Boolean =
        v != null && (v.javaClass.name == ITEM_CLASS || v.javaClass.simpleName == "TabItemLayoutV2")

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // 选中态桥接：setTabSelected(Z) 是条目选中标志的唯一翻转口（controller b.setSelectItem
        // 与 LiteTabBar.addTabView 初始都经它）。after 同步到标准 isSelected → 引擎逐帧选中
        // 读数/拖动预览冻结/装配初值零改动工作。
        // **initialize 必须为 false**（包加载期 Application 未创建，第三方视图类 <clinit>
        // 常有重副作用，见微博 §5.7 教训 3）。
        runCatching {
            val itemClass = Class.forName(ITEM_CLASS, false, loader)
            val setTabSelected =
                itemClass.getDeclaredMethod("setTabSelected", Boolean::class.javaPrimitiveType)
            facade.around(setTabSelected, null) { call ->
                val view = call.thisObj as? View ?: return@around null
                val selected = call.args.firstOrNull() as? Boolean ?: return@around null
                view.isSelected = selected
                null
            }
            facade.log(android.util.Log.INFO, "hooked Amap TabItemLayoutV2.setTabSelected selection bridge")
        }.onFailure {
            // 桥接失效的表现：玻璃液泡停在装配时的 tab 不跟随切页（镜像/点击转发仍工作）
            facade.log(android.util.Log.WARN, "hook Amap TabItemLayoutV2.setTabSelected failed (selection sync degraded)", it)
        }
        // 配色捕获：setSelectItem(re6, z) 是选中状态全量重设口（点击切页/应用驱动/预览
        // applier 全经它）——after 读各条目 tab_name_v2 当前色：选中项 = 强调色、未选中项 =
        // 内容色。token 驱动的文字色无选择器可取，视图读色是唯一实现无关的运行时取色口；
        // 每次切页重捕获（深色模式/主题变化自愈）。双参 boolean 重载在类内唯一，按签名
        // 解析不硬编码 re6 模型类名（混淆短名易变）。
        runCatching {
            val barClass = Class.forName(BAR_CLASS, false, loader)
            val setSelectItem = barClass.declaredMethods.singleOrNull {
                it.name == "setSelectItem" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
            } ?: throw NoSuchMethodException("setSelectItem(re6, boolean) not found")
            setSelectItem.isAccessible = true
            setSelectItemMethod = setSelectItem
            facade.around(setSelectItem, null) { call ->
                (call.thisObj as? ViewGroup)?.let { captureBarColors(it) }
                null
            }
            facade.log(android.util.Log.INFO, "hooked Amap LiteTabBar.setSelectItem color capture")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook Amap LiteTabBar.setSelectItem failed (accent color degraded)", it)
        }
    }

    /** setSelectItem after：读各条目 tab_name_v2 当前文字色，选中项 = 强调色、未选中项 = 内容色。 */
    private fun captureBarColors(bar: ViewGroup) {
        runCatching {
            val context = bar.context
            var id = labelsId
            if (id == 0) {
                id = context.resources.getIdentifier(LABELS_ID_NAME, "id", context.packageName)
                labelsId = id
            }
            if (id == 0) return
            var accent: Int? = null
            var content: Int? = null
            fun walk(v: View?) {
                if (v == null) return
                if (isTabItem(v)) {
                    val color = (v.findViewById(id) as? TextView)?.textColors?.defaultColor
                        ?.takeIf { it != 0 } ?: return
                    if (v.isSelected) accent = color else content = content ?: color
                    return
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(bar)
            capturedAccentColor = accent ?: capturedAccentColor
            capturedContentColor = content ?: capturedContentColor
        }
    }

    /**
     * 拖动悬停预览的选中外观翻转（descriptor.itemCheckedApplier 直控通道）：
     * `LiteTabBar.setSelectItem(re6, false)` 全量重设（目标选中、其余取消），controller
     * b.setSelectItem 纯视觉（z 参数未使用、无切页，逆向证实）。**checked=false 一律
     * no-op**：引擎预览路径中每次 checked=true 调用（悬停新 tab/拖回原 tab/还原/松手
     * 转发）都触发全量重设，终态恒正确——单条目"取消选中"无法用 setSelectItem 表达，
     * 也无需表达。模型 re6 零字段反射：view→parent 链找 LiteTabBar → 反射 mTabs（真名，
     * List 型 + 尺寸匹配兜底）→ tabContainer.indexOfChild(view) 取下标（与 mTabs 序
     * 对应，应用 setSelectItem 自身即按此假设实现）。通道缺失 = 预览静默失效（不崩溃）。
     */
    private fun applyChecked(view: View, checked: Boolean) {
        if (!checked) return
        var parent = view.parent
        var bar: View? = null
        while (parent is View) {
            if (parent.javaClass.name == BAR_CLASS) {
                bar = parent
                break
            }
            parent = parent.parent
        }
        val barView = bar as? ViewGroup ?: return
        val container = view.parent as? ViewGroup ?: return
        val index = container.indexOfChild(view)
        if (index < 0) return
        var method = setSelectItemMethod
        if (method == null) {
            method = barView.javaClass.declaredMethods.singleOrNull {
                it.name == "setSelectItem" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true } ?: return
            setSelectItemMethod = method
        }
        var field = tabsField
        if (field?.declaringClass != barView.javaClass) {
            field = runCatching { barView.javaClass.getDeclaredField("mTabs") }
                .getOrNull()
                ?.takeIf { List::class.java.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }
                ?: barView.javaClass.declaredFields.firstOrNull { f ->
                    List::class.java.isAssignableFrom(f.type) && runCatching {
                        (f.get(barView) as? List<*>)?.size == container.childCount
                    }.getOrDefault(false)
                }?.apply { isAccessible = true }
            tabsField = field
        }
        val tab = field?.get(barView) as? List<*> ?: return
        val model = tab.getOrNull(index) ?: return
        // 异常不吞：交由引擎 setTabChecked 的 runCatching + session.logger 统一记录
        method.invoke(barView, model, false)
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 显隐跟随：bar 视图自身的 setVisibility(8/0)（NewTabHostPage hideTab/
        // onTransitionEnd）与 modal present 的宿主 INVISIBLE 走 isShown 判定；平移过程
        // 过半即藏，避免玻璃滞留在已滑走的内容上。进 AI chat 等 non-modal present 覆盖页
        // 玻璃滞留为已知限制（两版信号方案真机未达预期，按用户决策放弃修复，§6.7）。
        session.mirrorVisibleGate = {
            val bar = session.tabContainer
            bar.isShown && bar.translationY < bar.height * 0.5f
        }
        // 采样去重：地图为 GL（TextureView）内容，采样即触发 GL 绘制，3 次/帧 → 1 次/帧
        session.dedupeBackdropSampling = config().lightweightSampling
        neutralizeItemDecorations(session, facade)
    }

    /**
     * 条目装饰中和（抑制态条目的原生装饰在整条目镜像中被 view.draw 如实复现，逐项消除）：
     * ①边缘内容偏移：应用对首/末条目子视图做 ±6.5dp X 平移
     * （`TabItemLayoutV2.applyAmapEdgeContentOffset`，onAttachedToWindow 调用，向胶囊内
     * 收、排除 tab_bg_layer）——玻璃槽位居中绘制时首/末条目内容偏离液泡中心 6.5dp、
     * 内容行两端内收呈"栏宽大于 tab 所需"观感（真机反馈②，已确认修复）——子视图
     * translationX 归零中和（tab_bg_layer 平移本为 0，全量归零无副作用）。
     * ②选中焦点背景（"液泡阴影"）：选中条目的半透明黑胶囊（tab_bg_layer 内 DtImageView，
     * GradientDrawable 25.5dp 圆角，TabFocusBackgroundViewHolder.f 在选中瞬间显示，早于
     * 液泡弹性动画结束，且持续存在于选中条目上）与玻璃液泡叠加呈"双选中指示"（真机
     * 反馈④）——holder 只 toggle 子视图 visibility（f()/e()），从不触碰 tab_bg_layer
     * 容器自身 → **容器 alpha=0 中和零对抗**（HWUI 跳过 alpha=0 子树，镜像不含原生胶囊，
     * 液泡成为唯一选中指示）。
     * 抑制态条目（alpha=0 不可见、clickable=false 不参与触摸）的唯一消费者是镜像绘制
     * 与 performClick 转发（不受变换/透明影响）；应用侧写入逻辑不修改；条目重附着场景
     * 由视图树重建 → isStale 重装路径自然覆盖。
     */
    private fun neutralizeItemDecorations(session: GlassSession, facade: HookFacade) {
        runCatching {
            var edgeNeutralized = 0
            var focusBgNeutralized = 0
            val context = session.tabContainer.context
            val bgLayerId =
                context.resources.getIdentifier(BG_LAYER_ID_NAME, "id", context.packageName)
            for (item in session.tabViews) {
                if (item !is ViewGroup) continue
                for (i in 0 until item.childCount) {
                    val child = item.getChildAt(i) ?: continue
                    if (child.translationX != 0f) {
                        child.translationX = 0f
                        edgeNeutralized++
                    }
                }
                if (bgLayerId != 0) {
                    item.findViewById<View>(bgLayerId)?.let { bgLayer ->
                        if (bgLayer.alpha != 0f) {
                            bgLayer.alpha = 0f
                            focusBgNeutralized++
                        }
                    }
                }
            }
            facade.log(
                android.util.Log.INFO,
                "amap: neutralized item decorations (edge offset $edgeNeutralized, focus bg $focusBgNeutralized)",
            )
        }.onFailure { facade.log(android.util.Log.WARN, "amap neutralize item decorations failed", it) }
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }
}
