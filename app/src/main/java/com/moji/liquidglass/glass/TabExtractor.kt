package com.moji.liquidglass.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
)

/**
 * 描述符驱动的 tab 提取器：从适配器解析出的 tab 视图列表提取玻璃栏所需
 * 全部数据（标题/选中/图标/角标/配色）。不感知具体应用——一切按 [descriptor] 工作。
 */
class TabExtractor(private val descriptor: TabBarDescriptor) {

    private val idCache = HashMap<String, Int>()

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
        for (role in listOf(TabRole.ICON_LOTTIE, TabRole.REFRESH_LOTTIE)) {
            val id = roleId(tabView.context, role)
            if (id == 0) continue
            val lottie = tabView.findViewById<View>(id) ?: continue
            val ready = runCatching { getComposition.invoke(lottie) != null }.getOrDefault(true)
            if (!ready) return false
        }
        return true
    }

    /** 提取玻璃栏装配参数。主线程调用（需要已完成布局的视图）。 */
    fun extract(tabViews: List<View>): GlassBarSpec? {
        if (tabViews.isEmpty()) return null
        return when (descriptor.iconMode) {
            IconMode.WHOLE_TAB -> extractWholeTab(tabViews)
            IconMode.LOTTIE_RESOLVE -> extractLottieResolve(tabViews)
        }
    }

    /** WHOLE_TAB：整视图镜像，图标/标签/角标全部由原视图携带，玻璃层零适配。 */
    private fun extractWholeTab(tabViews: List<View>): GlassBarSpec {
        val initialIndex = tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        return GlassBarSpec(
            tabs = tabViews.map { view ->
                GlassTab(
                    title = "",
                    iconView = null,
                    icon = null,
                    tabView = view,
                    mirrorWholeTab = true,
                    badgeView = null,
                    badgeText = null,
                    badgeDot = false,
                )
            },
            initialIndex = initialIndex.coerceIn(0, tabViews.size - 1),
            contentColor = null,
            accentColor = null,
        )
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
            // 颜色取自文字选择器：default=内容色，selected=强调色
            if (contentColor == null) {
                findTextView(tabView)?.let { tv ->
                    runCatching {
                        val csl = tv.textColors
                        contentColor = Color(csl.defaultColor)
                        val selected =
                            csl.getColorForState(intArrayOf(android.R.attr.state_selected), csl.defaultColor)
                        if (selected != csl.defaultColor) accentColor = Color(selected)
                    }
                }
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
