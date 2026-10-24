package com.moji.liquidglass.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.graphics.Color

/** tab 语义角色 → 资源 id 名（描述符驱动提取，适配器按应用声明映射）。 */
enum class TabRole { TEXT_VIEW, ICON_LOTTIE, REFRESH_LOTTIE, BADGE_NUM, BADGE_DOT_GUIDE, BADGE_DOT_TRASH }

/** 图标镜像模式。 */
enum class IconMode {
    /** 每帧解析 ICON_LOTTIE/REFRESH_LOTTIE 中当前可见的 Lottie 视图（商店：首页 ⇄ 刷新）。 */
    LOTTIE_RESOLVE,

    /** 镜像整个 tab 视图（图标/标签/角标一体，普通应用通用）。 */
    WHOLE_TAB,

    /** 整条目镜像：tab 内容全自绘、无图标/标签子视图的应用（如微博 TabViewGroupV2
     *  在 Canvas 上画图标+角标+文字），把整个条目视图按原生布局镜像为 tab 内容。
     *  条目自身携带选中态外观（drawable state_checked + 文字色切换），玻璃侧无需再
     *  绘制标签，也不做折射染色（tintRefractionContent=false）——染色会盖掉原生配色。 */
    WHOLE_TAB_FULL,
}

/** 底栏结构描述符：适配器声明目标应用的底栏形态，提取引擎按此工作。 */
data class TabBarDescriptor(
    /** 底栏容器类名（非混淆类优先，可空）。 */
    val containerClassName: String? = null,
    /** TabWidget 资源 id 名（可空）。 */
    val tabWidgetIdName: String? = null,
    /** 单个 tab 视图类名（可空）。 */
    val tabViewClassName: String? = null,
    /** 语义角色 → 资源 id 名（仅 LOTTIE_RESOLVE 模式需要）。 */
    val roles: Map<TabRole, String> = emptyMap(),
    val iconMode: IconMode = IconMode.LOTTIE_RESOLVE,
    /** 类名/id 失配时的位置启发式兜底探测。 */
    val fallbackProbe: Boolean = true,
    /** WHOLE_TAB 元素级镜像：元素名 → 资源 id 名。只镜像这些子元素（排除 M3 选中胶囊
     *  指示器与条目背景，观感对齐商店）；全部解析失败时回退整视图镜像。空 = 整视图镜像。 */
    val elementIds: Map<String, String> = emptyMap(),
    /** WHOLE_TAB 条目"选中外观翻转"方法名（默认 "setChecked"，相册 VNavMenuItem 路径）。
     *  拖动悬停预览靠它翻转条目选中态；部分库被方法级混淆（如 vivo 官网 originui
     *  13.3.0.1 为 "y"，无 setChecked），按取证在适配器声明。应用更新后改名 = 预览
     *  静默失效（不崩溃，回退为无预览）。 */
    val itemCheckedSetterMethodName: String? = null,
    /** WHOLE_TAB 条目"选中外观翻转"的直控通道（非 null 时优先于 [itemCheckedSetterMethodName]
     *  反射解析）。条目选中外观由"数据模型 + setData 重渲染"驱动、视图上不存在
     *  void(boolean) 翻转方法的应用（如高德地图 C3TabItemView：选中态在 or0.b 字段，
     *  翻转 = 改模型 + 反射调 setData）在适配器提供此闭包，参数 (条目视图, 目标选中态)。
     *  要求与反射路径同语义：纯视觉翻转、无切页等副作用（副作用应用见
     *  GlassSession.isTabPreviewActive 吞咽机制）。 */
    val itemCheckedApplier: ((view: View, checked: Boolean) -> Unit)? = null,
    /** 液泡折射副本是否整层强调色染色（默认 true：选中/悬停 tab 在液泡内呈强调色）。
     *  图标素材含"白=底色"编码的应用（如 vivo 官网）必须关闭：染色会把不透明白间隙
     *  一起染成强调色，图标在液泡内呈实心，且动画结束切到真透明间隙的静态图时产生
     *  "染色实心 → 镂空"闪现。关闭后液泡折射未染色副本 = 原样图标。 */
    val tintRefractionContent: Boolean = true,
    /** 液泡覆盖槽位抑制（默认 false）。true 时被液泡覆盖的可见槽位整槽不绘制，泡内
     *  折射副本成为唯一图标来源。液泡内图标来自隐形折射副本层的采样，按压/弹性运动时
     *  副本缩放（1.2x）与液泡膨胀（1.39x）和可见槽位镜像（恒 1x）并存；副本被整层染色
     *  的应用（默认 tintRefractionContent=true）副本呈纯色剪影、"读作高亮"不显重影，
     *  关闭染色 + 多色动画图标的应用（原子笔记）会显形为两份错位重影。仅声明本开关的
     *  应用消费（GlassRoot 按声明执行），默认关闭 = 既有应用渲染逐位不变（§0.4 红线）。 */
    val suppressBubbleCoveredSlots: Boolean = false,
    /** 点击转发接管（默认 null = 对条目视图 [View.performClick]）。条目自身无点击监听器
     *  /监听器链被应用侧机制架空的应用（如小红书：生效监听在祖先容器，参考 LiquidTab
     *  XhsNavigationState.performTap 的穿透式点击），适配器提供自己的派发逻辑，参数
     *  (槽位下标, 条目视图列表, 底栏容器)。实现须与原生点击同语义（切页/回顶）；拖动
     *  预览的选中翻转仍走 itemChecked* 通道，不受本字段影响。 */
    val tabClickHandler: ((slotIndex: Int, tabViews: List<View>, tabContainer: View) -> Unit)? = null,
    /** 玻璃栏标签文本覆盖（原生标签文本 → 显示文本，默认空 = 原样）。标签过长等场景的
     *  适配级精简，仅作用于玻璃栏自绘标签（提取与 TextWatcher 跟随双处生效）；原生底栏
     *  隐形不受影响。 */
    val labelOverrides: Map<String, String> = emptyMap(),
    /** 标签悬停/选中强调色回退（默认 false = 强调色缺失时回退内容色，无可见切换）。
     *  原生选中态**不靠颜色表达**的应用（如文件管理：标签色为纯色非选择器，选中态 =
     *  标签缩放）取色得到 强调色==内容色/缺失 → 悬停切换在视觉上不存在。true 时标签
     *  强调色回退到液泡折射染色的强调色（引擎默认蓝，GlassRoot.bubbleAccent），与气泡
     *  观感一致，符合 §1.2 悬停语义（悬停 tab = 强调色、原选中 = 内容色）。 */
    val labelAccentFallbackToBubble: Boolean = false,
    /** 单 tab 舒适宽度上限覆盖（dp，默认 null = 引擎全局 [GlassRoot] MAX_TAB_WIDTH_DP=70）。
     *  tab 极少（2 tab）且原生条目远宽于 70dp 槽位的应用（如手机管家），全局公式收窄
     *  的栏内图标/标签显拥挤，按应用声明更大值；null 时其余应用逐位不变（§0.4 红线）。 */
    val maxTabWidthDp: Int? = null,
    /** WHOLE_TAB 元素级镜像的图标盒尺寸覆盖（dp，默认 null = 24dp）。图标镜像把原生
     *  图标视图 fit 缩放进盒内——原生图标视图非 24dp 的应用（如手机管家 ROM16 动态符号
     *  32dp）被等比缩小，按应用声明原生尺寸 1:1 镜像；盒大于 28dp 预留时居中溢出
     *  （内容层无裁剪，icon+标签总高仍在栏高内）；null 时其余应用逐位不变（§0.4 红线）。 */
    val iconBoxDp: Int? = null,
)

/**
 * 描述符驱动的 tab 提取器：从适配器解析出的 tab 视图列表提取玻璃栏所需
 * 全部数据（标题/选中/图标/角标/配色）。不感知具体应用——一切按 [descriptor] 工作。
 */
class TabExtractor(val descriptor: TabBarDescriptor) {

    private val idCache = HashMap<String, Int>()

    /** 配色注入缝（适配器可选）：自绘 tab 无标签 TextView 可取色时，由适配器经 hook
     *  捕获应用的文字内容色/强调色后从这里供给，返回 null = 保持提取结果。 */
    var colorOverride: (() -> Pair<Color?, Color?>?)? = null

    private fun idOf(context: Context, name: String): Int =
        idCache.getOrPut(name) { context.resources.getIdentifier(name, "id", context.packageName) }

    private fun roleId(context: Context, role: TabRole): Int =
        descriptor.roles[role]?.let { idOf(context, it) } ?: 0

    /** 装配就绪判定：tab 数合法；LOTTIE_RESOLVE 模式额外要求合成已加载。 */
    fun readyToInstall(tabViews: List<View>, loader: ClassLoader): Boolean {
        if (tabViews.size < 2) return false
        if (descriptor.iconMode != IconMode.LOTTIE_RESOLVE) return true
        return tabViews.all { iconsReady(it, loader) }
    }

    private fun iconsReady(tabView: View, loader: ClassLoader): Boolean {
        val lottieClass = try {
            Class.forName("com.airbnb.lottie.LottieAnimationView", true, loader)
        } catch (_: Throwable) {
            return true
        }
        val getComposition = try {
            lottieClass.getMethod("getComposition")
        } catch (_: Throwable) {
            return true
        }
        // 只要求"会被镜像的那个 Lottie"（可见优先，与 findLottieView 同一解析规则）合成已加载。
        // 隐藏的备选 Lottie（如首页刷新态 lottie_view_refresh）应用可能懒加载合成、长期为 null，
        // 一并检查会让就绪判定永远不通过 → 探测空转、底栏永远不换（保持重构前的判定口径）。
        val lottie = findLottieView(tabView) ?: return true
        return runCatching { getComposition.invoke(lottie) != null }.getOrDefault(true)
    }

    /** 提取玻璃栏装配参数。主线程调用（需要已完成布局的视图）。 */
    fun extract(tabViews: List<View>): GlassBarSpec? {
        if (tabViews.isEmpty()) return null
        return when (descriptor.iconMode) {
            IconMode.WHOLE_TAB, IconMode.WHOLE_TAB_FULL -> extractWholeTab(tabViews)
            IconMode.LOTTIE_RESOLVE -> extractLottieResolve(tabViews)
        }
    }

    /** WHOLE_TAB（相册）：图标元素镜像（原生配色 + ViewOverlay 角标）+ 提取标签文本与配色
     *  （玻璃层用统一样式绘制，观感/大小/颜色与商店同构）。
     *  WHOLE_TAB_FULL（微博）：整个条目视图即镜像内容（iconView = tab 视图本身），
     *  玻璃层不绘制标签（标签在镜像里、配色由应用自绘状态驱动）。 */
    private fun extractWholeTab(tabViews: List<View>): GlassBarSpec {
        val full = descriptor.iconMode == IconMode.WHOLE_TAB_FULL
        val iconId = descriptor.elementIds["icon"]
        val labelsId = descriptor.elementIds["labels"]
        var contentColor: Color? = null
        var accentColor: Color? = null
        val tabs = tabViews.map { view ->
            val iconElement = if (full) view else iconId?.let { view.findViewByIdByName(it) }
            val labels = if (full) null else labelsId?.let { view.findViewByIdByName(it) }
            val title = labels?.let(::visibleLabelText)
                ?.let { descriptor.labelOverrides[it] ?: it } ?: ""
            if (contentColor == null) {
                val (content, accent) = extractTextColors(firstTextView(labels))
                contentColor = content
                accentColor = accent
            }
            GlassTab(
                title = title,
                iconView = iconElement,
                icon = null,
                tabView = view,
                mirrorWholeTab = true,
                badgeView = null,
                badgeText = null,
                badgeDot = false,
            )
        }
        if (contentColor == null || accentColor == null) {
            colorOverride?.invoke()?.let { (content, accent) ->
                if (contentColor == null) contentColor = content
                if (accentColor == null) accentColor = accent
            }
        }
        val initialIndex = tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        return GlassBarSpec(
            tabs,
            initialIndex.coerceIn(0, tabViews.size - 1),
            contentColor = contentColor,
            accentColor = accentColor,
        )
    }

    /** 标签组内第一个文本视图（VMenuTextView 小/大标签，配色选择器一致）。
     *  labels 元素自身是 TextView 时直接返回（高德地图 c3_tab_bar_item_name 等
     *  "标签即单 TextView 无分组"的条目布局）。 */
    private fun firstTextView(labels: View?): TextView? {
        if (labels is TextView) return labels
        val group = labels as? ViewGroup ?: return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView) return child
        }
        return null
    }

    /** 文字颜色选择器取色：default=内容色；checked/selected 态=强调色（两态都缺省时为 null，
     *  由 GlassRoot/LiquidBottomTabs 回退参考配色）。相册条目 checked 态、商店 selected 态。 */
    private fun extractTextColors(tv: TextView?): Pair<Color?, Color?> {
        if (tv == null) return null to null
        return runCatching {
            val csl = tv.textColors
            val default = csl.defaultColor
            val accent = listOf(android.R.attr.state_checked, android.R.attr.state_selected)
                .firstNotNullOfOrNull { state ->
                    csl.getColorForState(intArrayOf(state), default).takeIf { it != default }
                }
            Color(default) to accent?.let(::Color)
        }.getOrDefault(null to null)
    }

    /** 装配后延迟补取配色（应用可能晚于装配才应用主题色，如清除后台后的暖启动加载态）：
     *  先按 WHOLE_TAB 的 labels 元素找标签 TextView，回退 LOTTIE_RESOLVE 角色 TextView，
     *  返回第一组取到的 (内容色, 强调色)；适配器注入了 colorOverride 时优先采用。 */
    fun extractLabelColors(tabViews: List<View>): Pair<Color?, Color?> {
        colorOverride?.invoke()?.let { colors -> return colors }
        val labelsId = descriptor.elementIds["labels"]
        for (view in tabViews) {
            val labels = labelsId?.let { view.findViewByIdByName(it) }
            val colors = extractTextColors(firstTextView(labels))
            if (colors.first != null || colors.second != null) return colors
        }
        for (view in tabViews) {
            val colors = extractTextColors(findTextView(view))
            if (colors.first != null || colors.second != null) return colors
        }
        return null to null
    }

    /** 标签组内可见的文本视图文本（小/大标签按选中态显隐）；labels 元素自身是
     *  TextView 时直接取其文本。 */
    private fun visibleLabelText(labelsElement: View): String? {
        if (labelsElement is TextView) {
            return labelsElement.text?.toString()?.takeIf { it.isNotBlank() }
        }
        if (labelsElement !is ViewGroup) return null
        for (i in 0 until labelsElement.childCount) {
            val child = labelsElement.getChildAt(i)
            if (child is TextView && child.visibility == View.VISIBLE && !child.text.isNullOrBlank()) {
                return child.text.toString()
            }
        }
        return null
    }

    private fun View.findViewByIdByName(name: String): View? {
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) null else findViewById(id)
    }

    /** LOTTIE_RESOLVE（商店）：Lottie 图标镜像 + Compose 标签/角标 + 颜色选择器取色。 */
    private fun extractLottieResolve(tabViews: List<View>): GlassBarSpec? {
        val context = tabViews.firstOrNull()?.context ?: return null
        val tabs = ArrayList<GlassTab>(tabViews.size)
        var contentColor: Color? = null
        var accentColor: Color? = null
        var initialIndex = 0
        tabViews.forEachIndexed { index, tabView ->
            val label = findTextView(tabView)?.text?.toString()?.takeIf { it.isNotBlank() }
            if (label == null && findLottieView(tabView) == null) return@forEachIndexed
            if (tabView.isSelected) initialIndex = index
            val badgeNumId = roleId(context, TabRole.BADGE_NUM)
            tabs.add(
                GlassTab(
                    title = label ?: "",
                    iconView = findLottieView(tabView),
                    icon = drawIcon(tabView, context),
                    badgeView = tabView.findBadgeView(context),
                    badgeText = findRoleText(tabView, badgeNumId)
                        ?.takeIf { badgeNumId != 0 && tabView.findViewById<View>(badgeNumId)?.visibility == View.VISIBLE },
                    badgeDot = listOf(TabRole.BADGE_DOT_GUIDE, TabRole.BADGE_DOT_TRASH).any { role ->
                        val id = roleId(context, role)
                        id != 0 && tabView.findViewById<View>(id)?.visibility == View.VISIBLE
                    },
                )
            )
            // 颜色取自文字选择器：default=内容色，checked/selected=强调色
            if (contentColor == null) {
                val (content, accent) = extractTextColors(findTextView(tabView))
                contentColor = content
                accentColor = accent
            }
        }
        if (tabs.isEmpty()) return null
        return GlassBarSpec(tabs, initialIndex.coerceIn(0, tabs.size - 1), contentColor, accentColor)
    }

    /** 把当前可见的 Lottie 图标视图按当前帧绘制成 3x 位图（视图镜像不可用时兜底）。 */
    private fun drawIcon(tabView: View, context: Context): Bitmap? {
        val lottie = findLottieView(tabView) ?: return null
        val w = lottie.width.takeIf { it > 0 } ?: return null
        val h = lottie.height.takeIf { it > 0 } ?: return null
        // 不重置进度：主路径是逐帧镜像该视图，重置会冻结其真实姿态
        return runCatching {
            val scale = 3
            val bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(scale.toFloat(), scale.toFloat())
            lottie.draw(canvas)
            bitmap
        }.getOrNull()
    }

    /** 当前可见优先：ICON_LOTTIE → REFRESH_LOTTIE → 任一（含不可见兜底）。 */
    fun findLottieView(tab: View): View? {
        val context = tab.context
        val preferred = listOf(TabRole.ICON_LOTTIE, TabRole.REFRESH_LOTTIE)
        for (role in preferred) {
            val id = roleId(context, role)
            if (id == 0) continue
            tab.findViewById<View>(id)?.takeIf { it.visibility == View.VISIBLE }?.let { return it }
        }
        return preferred.firstNotNullOfOrNull { role ->
            val id = roleId(context, role)
            if (id == 0) null else tab.findViewById<View>(id)
        }
    }

    /** 双 Lottie 全量解析（主 + 刷新，无论可见性）：复位类操作用——商店首页图标是
     *  双 Lottie（lottie_view 主 / lottie_view_refresh 刷新），可见性由应用刷新态
     *  状态机翻转，单视图跟踪会漏掉不可见者。 */
    fun findLottieViews(tab: View): List<View> {
        val context = tab.context
        return listOf(TabRole.ICON_LOTTIE, TabRole.REFRESH_LOTTIE).mapNotNull { role ->
            val id = roleId(context, role)
            if (id == 0) null else tab.findViewById<View>(id)
        }
    }

    fun findTextView(tab: View): TextView? {
        val id = roleId(tab.context, TabRole.TEXT_VIEW)
        return if (id == 0) null else tab.findViewById(id)
    }

    /** 角标视图：数字角标 > 引导点 > 红点，取第一个可见的。 */
    private fun View.findBadgeView(context: Context): View? =
        listOf(TabRole.BADGE_NUM, TabRole.BADGE_DOT_GUIDE, TabRole.BADGE_DOT_TRASH).firstNotNullOfOrNull { role ->
            val id = roleId(context, role)
            if (id == 0) null else findViewById<View>(id)?.takeIf { it.visibility == View.VISIBLE }
        }

    private fun findRoleText(tabView: View, id: Int): String? {
        if (id == 0) return null
        return tabView.findViewById<TextView>(id)?.text?.toString()?.takeIf { it.isNotBlank() }
    }
}
