package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.compose.ui.graphics.Color
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.ExtraActionButtonSpec
import com.moji.liquidglass.glass.GlassInstaller
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import kotlin.math.abs

/**
 * 小黑盒（com.max.xiaoheihe，版本 1.3.399 取证）适配器。
 *
 * - 主宿主链路：launcher = `SplashActivity`（@style/splash），真实主 Tab 页 =
 *   `MainActivity`（singleTask + portrait，应用级 @style/AppTheme =
 *   Theme.AppCompat.DayNight.NoActionBar + windowBackground=@null）。子页宿主
 *   MainSinglePageActivity（exported=false）不承载底栏。
 * - 底栏结构（activity_main 布局，资源名未混淆，getIdentifier 可用）：根
 *   RelativeLayout `vg_main_root`，底栏容器 = `rg_main`（RadioGroup，56dp =
 *   dimen/home_bottom_tab_bar_height，alignParentBottom，背景 color_bg_subtle_day_night）
 *   内 5 个 RadioButton `rb_1..rb_5`（**运行时为 AppCompatRadioButton**——应用主题
 *   Theme.AppCompat.DayNight，AppCompat 布局工厂自动替换，条目过滤必须 instanceof
 *   而非类名精确匹配，真机 probe bar=true tabViews=0 实证；style NavBarRbStyle：
 *   weight=1、wrap 高、marginTop 6dp、button=@null、textColor=@color/nav_bar_text_color 选择器
 *   {state_checked: color_text_primary_day_night, 默认 color_text_tertiary_day_night}）。
 *   实际 4 tab：rb_1 发现 / rb_2 游戏库 / rb_4 社区 / rb_5 我；rb_3（推荐）onCreate
 *   恒置 INVISIBLE（服务端 TabConfigObj 遗留位，占位不显示）——tabViewsOf 按
 *   visibility==VISIBLE 过滤，未来版本若显示 rb_3 自动纳入。
 * - 选中机制（自研轨，非平台 TabWidget）：`MainActivity implements
 *   CompoundButton.OnCheckedChangeListener`，onCheckedChanged = 唯一选中入口——
 *   字体切换（checked=粗体 FontProvider）+ 图标 Lottie 驱动（默认皮肤路径
 *   AccelWorldLottieKt.h(comp,0) 播 marker 段选中动画；皮肤路径 g(comp) 跳末帧）+
 *   切页口 `(index, checked)`（Fragment add/show/hide 到 `fl_container`，tag=下标；
 *   R8 短名随版重排：1.3.399=N4、1.3.400=P4，候选链见 SWITCH_METHOD_CANDIDATES；
 *   rb_1→0 发现 / rb_2→1 游戏库 / rb_4→3 社区 / rb_5→4 我，rb_3→2 无 Fragment）
 *   + 双击回顶计时。点击 = 标准 RadioGroup 语义（performClick → 互斥级联 →
 *   onCheckedChanged），rb_1/rb_2/rb_4 选中一次后 onClick 被换成"回顶广播"发送器
 *   ——玻璃转发走条目 performClick 天然工作（§1.3 免疫清单），tabClickHandler 无需接管。
 * - 图标：XML drawableTop selector（nav_bar_*）在 onCreate 被 setCompoundDrawables
 *   替换——默认路径挂 `com.airbnb.lottie.LottieDrawable`（R.raw.nav_bar_anim_*_v2，
 *   多色动画图标，marker "light"/"dark" 分主题段）；皮肤路径挂 c4(normal,selected)
 *   构造 drawable / rb_5 挂 StateListDrawable{state_checked: 选中, default: 静态}，
 *   rb_5 另有 Steam/头像动态替换。WHOLE_TAB_FULL 整条目镜像（图标/标签/选中外观
 *   全自含随条目）；条目自配色 → `tintRefractionContent=false`；**不声明
 *   suppressBubbleCoveredSlots**——图标为静态帧 lottie（仅选中瞬间播一次动画），
 *   按压时液泡副本与镜像几乎重合、磨砂玻璃糊掉底层镜像无重影可忧（芒果TV 同类
 *   先例）；且抑制判定窗口（≈±0.85~0.99 槽）大于液泡捕获范围，声明方真机实证
 *   "液泡进出槽位途中图标消失"。
 * - 选中读数桥接：RadioButton checked ≠ isSelected（CompoundButton 的 checked 是
 *   独立状态，RadioGroup 不写 setSelected）——hook 平台 `CompoundButton.setChecked`
 *   after 同步 isSelected（回调按实例过滤 RadioButton），引擎逐帧读数/装配初值/
 *   预览冻结零改动工作（微博 №04 同款桥接；identity 过滤先例 = 芒果TV View.setAlpha）。
 * - 拖动悬停预览（§0.5）：翻转通道 = itemCheckedApplier 直控——同态去重 →
 *   `isChecked = checked`（RadioGroup 互斥级联：旧 tab false / 新 tab true 全走
 *   应用自身 onCheckedChanged，字体/状态选择器/皮肤 selector 天然跟随）→ lottie
 *   落定（AccelWorldLottieKt.g 反射直驱 marker 末帧）。副作用吞咽双锚（预览激活
 *   previewFlipping 时）：①`MainActivity` 切页口(IZ)（1.3.399=N4/1.3.400=P4——不吞则未松手页面已变）；
 *   ②`AccelWorldLottieKt.h(Lcom/airbnb/lottie/j;I)`（动画口——不吞则每次悬停重播
 *   选中动画；吞掉后由落定配方 g 直驱，动画口/直置口并存必用直置口，§0.5 B.1/B.2）。
 *   锚点失效静默降级：预览短暂重播动画后自止（h 是 marker 段一次性播放）、无崩溃。
 * - 角标：底栏无内嵌角标——`vg_tips` 是与 rg_main 同几何的**兄弟覆盖层**
 *   （5 个等权 cell 1:1 对齐 radio 列；iv_tips_1/3/4/5 红点 + iv_tips_2/msg_tips_2
 *   游戏库页 MsgView 点/数字，flyco MsgView）。装配期把每个角标视图包成
 *   BadgeMirrorDrawable 挂进对应 RadioButton 的 ViewOverlay（相册"ViewOverlay
 *   角标随镜像带走"同语义），vg_tips 压 alpha=0（绘制手调不施加父 alpha，镜像完整；
 *   显隐按 badge.isShown 逐帧判定——沉浸隐藏（b(Boolean) 对 rg_main/vg_tips/
 *   vg_mid_tab 三者 setVisibility 8/0 同步开关）自然跟随）。
 * - 内容延伸（真机前静态确证项）：`fl_container` 在 activity_main 里
 *   `layout_above=rg_main`——内容原生止于栏顶，玻璃栏后无内容可采样
 *   （windowBackground=@null → 空白）。装配期移除 ABOVE 规则（match_parent 不变，
 *   填满 vg_main_root），应用对它零运行时写入（MainActivity 全文件核对无
 *   f132015b 引用），单次写入安全；restore 回规则。另移除栏顶 3dp 渐变阴影线
 *   （**结构锚**：无 id + 叶子 + 高度 = shadow_height dimen + background 非空，
 *   不做类名匹配——换肤/布局工厂可能替换 XML `<View>` 的运行时类，类名锚真机
 *   失配实证；应用无引用无法强转，直接移除——区别于微博需替身）。
 * - 悬浮发布键 `vg_mid_tab`（CardView 36x28 快速发布，底中心 marginBottom 14dp，
 *   onCreate 恒置 VISIBLE，点击 → PostTabActivity）：**玻璃化 = descriptor.
 *   extraActionButton 附加操作按钮**（引擎通用通道，参考项目 Glass Bottom Bar 教程
 *   "Exercise: Add a tinted glass icon button" 同构：栏与按钮平行等高、8dp 间距、
 *   独立圆形 CircleShape 玻璃 + tint Hue 混合与 0.75 alpha 表面覆盖；强调色 = 原加号
 *   背景基色边缘环均值，字形 = 原图标位图与基色的通道差提取、原色渲染），点击
 *   performClick 转发原生 onClick 链路（→ PostTabActivity，登录/广告门控全保留）。
 *   原生视图压 alpha=0（重申 + restore）。**不用 aux 胶囊通道**：AuxGlassPill 渲染
 *   在外层 Box 且先于玻璃栏 BoxWithConstraints → z 序在栏下方，条带内元素会被磨砂
 *   胶囊盖住（真机首装实证"发布键未识别至底栏区域"根因；aux 通道适用面 = 栏外
 *   悬浮元素，文件管理存储条先例）。
 * - 窗口延伸 + 玻璃栏抬升（№14.2 配方，真机"信息流无法全屏显示/小白条区域无法
 *   显示内容"双症状修复）：AppTheme 非 translucent nav、x.j0 只设状态栏位
 *   （setSystemUiVisibility(1280) = LAYOUT_STABLE|LAYOUT_FULLSCREEN，**赋值式覆写**，
 *   全应用 22 处调用）——窗口 frame 止于导航条顶，内容永远画不进小白条区域：信息流
 *   止于玻璃栏上沿、小白条条带透出空底（windowBackground=@null），玻璃栏底边与
 *   小白条之间夹一段死条带。onResume 直设 + `View.setSystemUiVisibility` 钩子 after
 *   **同栈补位**（x.j0 类覆写在输入事件里随时发生，补位先于任何 traversal，窗口永远
 *   看不到缺位状态；重入时 flags 已在位不再写入，天然收敛）；玻璃栏抬升 = 真实导航栏
 *   inset 会话级缓存（`GlassInstaller.navInsetProvider`，芒果TV/淘宝/支付宝同款）。
 *   **补位必须按宿主窗口收口**（真机"游戏库→游戏详情页底部被小白条遮挡"修复）：
 *   `View.setSystemUiVisibility` 是进程级 hook，不区分窗口——详情页宿主
 *   `ChannelsDetailActivity`（`x.j0` 同样只设 1280）按原生安全区布局（
 *   `fragment_game_detail` 的 `ll_bottom_bar` alignParentBottom、全页无导航条衬垫），
 *   一旦被补上 LAYOUT_HIDE_NAVIGATION，窗口延到导航条后底部内容直接顶到屏幕底被
 *   遮挡。修复 = onTabActivityResumed 登记 MainActivity decor 弱引用，补位回调仅对
 *   `view.rootView === decor` 生效；onResume 前/宿主窗口重建以 context 链 Activity
 *   类名兜底（见 isHostWindow），非宿主窗口一次性留痕
 *   `window-extend: skipped non-host window`。
 * - 导航栏透明：onResume `applyTransparentNavigationBar`（§1.1 通用件）——窗口延伸后
 *   透明导航栏才真正生效（小白条条带透出全出血内容）。无设色收口需求（未发现换肤
 *   设色路径收口点，真机反馈再补吞色 hook）。
 */
object HeyboxAdapter : AppAdapter {

    override val targetPackage: String = "com.max.xiaoheihe"
    override val displayName: String = "小黑盒"
    override val tabActivityClassName: String? = TAB_ACTIVITY
    override val baselineVersions: List<String> = listOf("1.3.399", "1.3.400")

    private const val TAB_ACTIVITY = "com.max.xiaoheihe.MainActivity"
    private const val HOST_CLASS = "com.max.xiaoheihe.MainActivity"
    private const val BAR_ID_NAME = "rg_main"
    private const val CONTENT_ID_NAME = "fl_container"
    private const val TIPS_ID_NAME = "vg_tips"
    private const val MID_TAB_ID_NAME = "vg_mid_tab"
    private const val CONTAINER_CLASS = "android.widget.RadioGroup"

    /** 切页副作用入口 `MainActivity.(int, boolean)`（R8 每升版重排短名，候选链新名优先
     *  旧名回退：1.3.400=P4、1.3.399=N4；签名 (IZ)V 精确匹配——1.3.400 的 N4 已是
     *  (MsgView,I) 别义方法、1.3.399 的 P4 是 (Drawable,String)V，均不会被误中）。 */
    private val SWITCH_METHOD_CANDIDATES = listOf("P4", "N4")
    /** lottie 落定直置口 `AccelWorldLottieKt.g(LottieDrawable)`（R8 改名，1.3.399 取证，
     *  1.3.400 复核同名）。 */
    private const val LOTTIE_SETTLE_CLASS = "com.max.xiaoheihe.accelworld.AccelWorldLottieKt"
    private const val LOTTIE_SETTLE_METHOD = "g"
    /** lottie 动画口 `AccelWorldLottieKt.h(LottieDrawable, int)`（R8 改名，1.3.399 取证，
     *  1.3.400 复核同名）。 */
    private const val LOTTIE_ANIM_METHOD = "h"
    /** 图标包装构造口 `MainActivity.(Drawable, int)Drawable`（R8 每升版重排短名，候选链：
     *  1.3.400=f4、1.3.399=d4）——返回 StateListDrawable{state_checked: lottie,
     *  default: 静态 PNG}。签名精确匹配：1.3.400 的 d4 已是 (String,String)Drawable
     *  别义方法、1.3.399 的 f4 是 ()V，均不会被误中。 */
    private val DRAWABLE_WRAP_CANDIDATES = listOf("f4", "d4")

    /** 候选链定位已声明方法（§0.6.2 SOP-D：新名优先、旧名回退，逐个精确签名匹配静默
     *  降级；返回命中方法，调用方以 `method.name` 留痕实际命中名）。 */
    private fun resolveDeclaredMethod(
        hostClass: Class<*>,
        candidates: List<String>,
        vararg paramTypes: Class<*>?,
    ): java.lang.reflect.Method? = candidates.firstNotNullOfOrNull { name ->
        runCatching { hostClass.getDeclaredMethod(name, *paramTypes) }.getOrNull()
    }

    /** 内容活动信号源说明（2026-11-04 起并入引擎单管线）：本应用此前自接的
     *  RecyclerView/LottieAnimationView/CompoundButton 三类均在引擎默认列表内
     *  （AppAdapter.DEFAULT_CONTENT_SIGNAL_CLASSES，hook/分发/降帧统一），适配器
     *  侧不再自接 hook——冻结检测经 session.onContentActivity 挂接（见
     *  onContentActivitySignal）。信号只升档不门控绘制，静止 30fps 低频档兜底一切
     *  未覆盖的变化（角标/换肤 ≤33ms 自愈）。 */

    // —— 采样冻结（滑动期零外部 draw，上滑卡顿修复） ——
    // 上滑时列表新绑定的条目恰好落在屏幕底部的玻璃采样条带内，逐帧外部 view.draw
    // 把"新条目首次构建"（图片着色器/卡片测量绘制）的成本每帧重付一遍 = 上滑远比
    // 下滑卡的重因（下滑时条带内视图只是平移、显示列表有效，采样近乎免费）。
    // 冻结门控 = 持续活动检测：信号连续触发 FREEZE_ENTER_MS 进入冻结（条带一次性
    // 录制定格快照、冻结期纯回放零外部 draw），信号停 FREEZE_GAP_MS 恢复实时——
    // 8dp 模糊下快照无感（芒果TV WebView 页/相册 GL 页同款通道）。离散变化（切页/
    // 角标/选中）活动窗短于阈值不冻结，玻璃实时跟随。
    private const val FREEZE_GAP_MS = 250L
    private const val FREEZE_ENTER_MS = 200L

    /** [0]=最近信号时刻，[1]=持续活动起点（0 = 未持续）。仅主线程读写（信号在
     *  onDraw、门控在 draw 相，均主线程）。 */
    private val freezeGateState = LongArray(2)

    /** 内容活动信号接收端（onSessionCreated 经 session.onContentActivity 挂接）：更新
     *  冻结持续检测。满帧率窗口（armActivityWindow）由引擎信号管线统一升档，此处
     *  不再重复调（单管线，防多通道叠加）。 */
    private fun onContentActivitySignal() {
        val now = android.os.SystemClock.elapsedRealtime()
        val st = freezeGateState
        if (st[0] == 0L || now - st[0] > FREEZE_GAP_MS) st[1] = now
        st[0] = now
    }

    /** 采样冻结门控（draw 相逐 tick 调用，闭包内自缓存零遍历）。 */
    private fun backdropFreezeGate(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val st = freezeGateState
        if (st[0] == 0L || now - st[0] > FREEZE_GAP_MS) {
            st[1] = 0L
            return false
        }
        return st[1] != 0L && now - st[1] >= FREEZE_ENTER_MS
    }

    /** 预览翻转进行中（applier 置位）：切页口/h 吞咽 hook 据此吞掉切页与动画口副作用。
     *  仅预览通道置位——正式点击（松手 performClick）时恒 false，应用切页照常。 */
    @Volatile private var previewFlipping = false

    /** 落定配方（installAppHooks 解析缓存）：AccelWorldLottieKt.g(LottieDrawable)。 */
    @Volatile private var settleMethod: java.lang.reflect.Method? = null

    /** 图标包装映射（包装构造口 after 捕获，1.3.399=d4/1.3.400=f4）：StateListDrawable → 内层 LottieDrawable。
     *  应用图标顶层 drawable 是 StateListDrawable{state_checked: lottie, default: 静态
     *  PNG}（包装构造口构造）——落定 g 必须作用在内层 lottie 上（外层类名不含 lottie 前缀，
     *  直接判定会静默跳过 = "必须先真实选中一次才有选中图标"根因）。弱键随换肤重建。 */
    private val stateListLottieMap =
        java.util.WeakHashMap<android.graphics.drawable.Drawable, android.graphics.drawable.Drawable>()

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = CONTAINER_CLASS,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = false,
        // 翻转通道 = 直控 applier（setChecked 有切页/动画副作用，反射通道不可用，见 flipChecked）
        itemCheckedApplier = { item, checked -> flipChecked(item, checked) },
        // 条目自配色（checked 文字色选择器 + 多色 Lottie），整层染色会盖掉原生配色
        tintRefractionContent = false,
        // 不声明 suppressBubbleCoveredSlots（保持默认 false）——抑制判定窗口
        // （0.5·bubbleScaleX + 0.15 槽 ≈ ±0.85~0.99 槽，含速度拉伸）大于液泡实际
        // 捕获范围（~0.5 槽 + lens 位移）：液泡在两槽之间/即将进出时源槽与目标槽
        // 同时被抑制、而液泡内折射不到该槽图标 = 图标消失窗口（真机实证）。
        // 本应用图标为静态帧 lottie（仅选中瞬间播一次动画），按压时液泡副本与
        // 镜像几乎重合、磨砂玻璃糊掉底层镜像，无重影可忧——芒果TV 同类先例
        // （tint 关 + 多色图标，无抑制）。抑制适用面 = "按住时图标自身还在动画"
        // 的应用（原子笔记），见主册 §1.2。
        // 悬浮发布键 vg_mid_tab（快速发布，原设计位于底栏中间）→ 玻璃栏右侧独立圆形
        // 着色玻璃图标按钮（参考项目教程同构：与栏平行等高、8dp 间距、CircleShape，
        // 强调色 = 原加号背景基色），点击 performClick 转发原生 onClick 链路
        // （→ PostTabActivity，登录/广告门控全保留）。原生视图压 alpha=0。
        extraActionButton = ExtraActionButtonSpec(
            icon = { activeSessionRef?.get()?.let { session -> publishIconDrawable(session) } },
            accent = { activeSessionRef?.get()?.let { session -> publishAccentColor(session) } },
            onClick = { midTabViewRef?.get()?.performClick() },
        ),
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == TAB_ACTIVITY

    override fun findTabBar(content: ViewGroup): View? {
        // 首选：rg_main 资源 id（arsc 资源名未混淆，getIdentifier 可查）
        val barId = content.resources.getIdentifier(BAR_ID_NAME, "id", content.context.packageName)
        if (barId != 0) {
            content.findViewById<View>(barId)?.let { return it }
        }
        // 兜底：content 内第一个"子项全为 RadioButton 且 3~5 个"的 RadioGroup
        // （平台类锚点免疫 id 漂移；页内 RadioGroup 表单容器无此形态）
        val fallbacks = ArrayList<View>(1)
        walkByClass(content) { v ->
            if (fallbacks.isEmpty() && v is RadioGroup && v.childCount in 3..5) {
                var allRadio = true
                for (i in 0 until v.childCount) {
                    if (v.getChildAt(i) !is RadioButton) allRadio = false
                }
                if (allRadio) fallbacks.add(v)
            }
        }
        return fallbacks.firstOrNull()
    }

    override fun tabViewsOf(bar: View): List<View> {
        if (bar !is ViewGroup) return emptyList()
        val items = ArrayList<View>(bar.childCount)
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i) ?: continue
            // instanceof 过滤（AppCompat 布局工厂把 XML <RadioButton> 替换为
            // AppCompatRadioButton 子类，类名精确匹配会全部落空——真机 probe 实证
            // bar=true tabViews=0）+ 可见过滤：rb_3（推荐）恒 INVISIBLE 占位，排除出
            // 槽位（否则玻璃栏出现空槽）；父容器沉浸隐藏（GONE）不改变子 visibility，
            // 不影响本过滤；服务端未来显示 rb_3 时自动纳入
            if (child is RadioButton && child.visibility == View.VISIBLE) {
                items.add(child)
            }
        }
        return items
    }

    private fun walkByClass(root: View, visit: (View) -> Unit) {
        visit(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.let { walkByClass(it, visit) }
            }
        }
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
        cachedNavInsetLift = -1
        // 窗口延伸（№14.2 配方）：每次 onResume 重设（应用可能在 onCreate 后改回）。
        // 同时登记宿主窗口（decor 弱引用）——补位 hook 只服务这一窗口，详情页等
        // 无玻璃栏页面不得被延进导航条（见 installAppHooks ④ / isHostWindow）。
        runCatching {
            val decor = activity.window?.decorView ?: return
            tabDecorRef = java.lang.ref.WeakReference(decor)
            if (decor.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                decor.systemUiVisibility = decor.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
                decor.requestApplyInsets()
            }
        }
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        // ① 选中读数桥接：RadioButton checked ≠ isSelected（CompoundButton 独立状态，
        //    RadioGroup 互斥级联只写 checked）——hook 平台 CompoundButton.setChecked after
        //    同步 isSelected，回调按实例过滤 RadioButton（app 全局 CheckBox/Switch 不受扰）。
        //    失效表现 = 玻璃液泡停在装配时 tab 不跟随切页（镜像/点击转发仍工作）。
        //    initialize=false：进程加载期不触发宿主 <clinit> 连锁初始化（微博 §5.7 纪律）。
        runCatching {
            val setChecked = CompoundButton::class.java.getDeclaredMethod(
                "setChecked", Boolean::class.javaPrimitiveType,
            )
            facade.around(setChecked, null) { call ->
                val view = call.thisObj as? RadioButton ?: return@around null
                val checked = call.args.firstOrNull() as? Boolean ?: return@around null
                view.isSelected = checked
                null
            }
            facade.log(android.util.Log.INFO, "hooked CompoundButton.setChecked -> RadioButton selection bridge")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook CompoundButton.setChecked failed (selection sync degraded)", it)
        }
        // ② 落定配方解析：AccelWorldLottieKt.g(LottieDrawable)——按参数类型定位（类名
        //    com.max.xiaoheihe.accelworld.AccelWorldLottieKt 非混淆稳定；方法名 g 为 R8
        //    改名锚点，升版经 dexsql 重取证，见 №15.3）。解析失败 = 预览图标短暂重播
        //    动画后自止（静默降级，无崩溃）。
        runCatching {
            val ktClass = Class.forName(LOTTIE_SETTLE_CLASS, false, loader)
            val method = ktClass.declaredMethods.firstOrNull {
                it.name == LOTTIE_SETTLE_METHOD && it.parameterCount == 1 && !it.parameterTypes[0].isPrimitive
            }
            if (method != null) {
                method.isAccessible = true
                settleMethod = method
                facade.log(android.util.Log.INFO, "resolved AccelWorldLottieKt.$LOTTIE_SETTLE_METHOD settle anchor")
            } else {
                facade.log(android.util.Log.WARN, "AccelWorldLottieKt.$LOTTIE_SETTLE_METHOD not found (settle degraded)")
            }
            // 图标包装捕获：包装构造口(Drawable, int) after——arg0 = LottieDrawable、result =
            // StateListDrawable{state_checked: lottie, default: 静态 PNG}，按实例存映射
            // 供落定解包（外层类名无 lottie 前缀，不捕获则落定静默跳过 = 悬停永远显示
            // 未选中图标）。皮肤路径后续换新 StateListDrawable（无 lottie 内层）
            // → 查映射失配 → 静态选中态由 checked 状态自动呈现，无需落定，天然正确。
            runCatching {
                val hostClass = Class.forName(HOST_CLASS, false, loader)
                val wrap = resolveDeclaredMethod(
                    hostClass,
                    DRAWABLE_WRAP_CANDIDATES,
                    android.graphics.drawable.Drawable::class.java,
                    Int::class.javaPrimitiveType,
                ) ?: error("icon wrapper anchor miss: candidates=$DRAWABLE_WRAP_CANDIDATES")
                facade.around(wrap, null) { call ->
                    val lottie = call.args.getOrNull(0) as? android.graphics.drawable.Drawable
                        ?: return@around null
                    val stateList = call.result as? android.graphics.drawable.StateListDrawable
                        ?: return@around null
                    synchronized(stateListLottieMap) { stateListLottieMap[stateList] = lottie }
                    null
                }
                facade.log(android.util.Log.INFO, "hooked MainActivity.${wrap.name} icon wrapper capture")
            }.onFailure {
                facade.log(android.util.Log.WARN, "hook MainActivity icon wrapper capture failed (settle degraded)", it)
            }
            // ③ 预览副作用吞咽（预览激活 previewFlipping 时）：
            //    ③a 切页入口 MainActivity.(IZ)——不吞则拖动未松手页面已切（唯一
            //        Fragment show/hide 通道，正式点击路径同样经它，gate 只罩预览）。
            runCatching {
                val hostClass = Class.forName(HOST_CLASS, false, loader)
                val switch = resolveDeclaredMethod(
                    hostClass,
                    SWITCH_METHOD_CANDIDATES,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ) ?: error("page switch anchor miss: candidates=$SWITCH_METHOD_CANDIDATES")
                facade.around(switch, { _ ->
                    if (previewFlipping) false else null
                }, null)
                facade.log(android.util.Log.INFO, "hooked MainActivity.${switch.name} preview gate (page switch)")
            }.onFailure {
                facade.log(android.util.Log.WARN, "hook page switch preview gate failed (drag preview may switch pages)", it)
            }
            //    ③b 动画口 AccelWorldLottieKt.h(LottieDrawable, int)——onCheckedChanged
            //        checked 分支经它重播选中动画（§0.5 A.2 无过程感违规源）；吞掉后由
            //        applier 落定配方 g 直驱末帧。参数类型取自 ② 解析结果（同一 drawable
            //        形参类），②失效时本步一并放弃（降级路径 = 短暂重播后自止）。
            val drawableType = method?.parameterTypes?.firstOrNull()
            if (drawableType != null) {
                runCatching {
                    val anim = ktClass.declaredMethods.firstOrNull {
                        it.name == LOTTIE_ANIM_METHOD && it.parameterCount == 2 && it.parameterTypes[0] == drawableType
                    }
                    if (anim != null) {
                        facade.around(anim, { _ ->
                            if (previewFlipping) false else null
                        }, null)
                        facade.log(android.util.Log.INFO, "hooked AccelWorldLottieKt.$LOTTIE_ANIM_METHOD preview gate (animation replay)")
                    } else {
                        facade.log(android.util.Log.WARN, "AccelWorldLottieKt.$LOTTIE_ANIM_METHOD not found (preview icon may briefly animate)")
                    }
                }.onFailure {
                    facade.log(android.util.Log.WARN, "hook AccelWorldLottieKt.$LOTTIE_ANIM_METHOD failed", it)
                }
            }
        }.onFailure {
            facade.log(android.util.Log.WARN, "AccelWorldLottieKt resolve failed (preview settle degraded)", it)
        }
        // ④ 窗口延伸同栈补位（№14.2 配方）：x.j0 = setSystemUiVisibility(1280) **赋值式
        //    覆写**且全应用 22 处调用（onResume/换肤/页面事件）——延伸 flags 被覆写时同帧
        //    traversal 用缺位值重排 = 小白条区内容闪断回缩。after 同栈补位先于任何
        //    traversal，窗口永远看不到缺位状态；重入时 flags 已在位不再写入，天然收敛。
        //    应用重写的是状态栏位（0x500），读改写保留其他位，与此补位兼容。
        //    **作用域 = 玻璃栏宿主窗口**：平台 hook 进程级生效，详情页宿主
        //    ChannelsDetailActivity / 编辑器等页面按原生安全区布局（无导航条衬垫），
        //    一并补 LAYOUT_HIDE_NAVIGATION 会让其底部内容顶到屏幕底被小白条遮挡
        //    （真机"游戏库→游戏详情页"实证，№15.7）。非宿主窗口直接放行 + 一次性留痕。
        runCatching {
            val setSui = View::class.java.getDeclaredMethod(
                "setSystemUiVisibility", Int::class.javaPrimitiveType,
            )
            facade.around(setSui, null, { call ->
                val view = call.thisObj as? View ?: return@around null
                if (!isHostWindow(view)) {
                    logNonHostWindowOnce(view, facade)
                    return@around null
                }
                runCatching {
                    if (view.systemUiVisibility and REQUIRED_LAYOUT_FLAGS != REQUIRED_LAYOUT_FLAGS) {
                        view.systemUiVisibility = view.systemUiVisibility or REQUIRED_LAYOUT_FLAGS
                    }
                }
                null
            })
            facade.log(android.util.Log.INFO, "hooked View.setSystemUiVisibility: layout bits re-ORed in-stack (host window scoped)")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook setSystemUiVisibility failed", it)
        }
        // ⑤ 内容活动信号：自接 hook 已退役并入引擎单管线（TargetHook 进程级安装默认
        //    5 类，本应用此前自接的 3 类均在其中）；冻结持续检测经 onSessionCreated 挂
        //    session.onContentActivity（见 onContentActivitySignal）。
    }

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        activeSessionRef = java.lang.ref.WeakReference(session)
        // 背景采样去重：由 TargetHook 会话创建期统一设置（引擎单管线，默认开；
        // lightweightSampling 远程可关）
        // 采样冻结（滑动期）：持续活动检测门控（见采样冻结注释）——上滑新绑定条目
        // 落在条带内导致逐帧外部 draw 重付首建成本的上滑卡顿修复
        session.backdropFreezeGate = { backdropFreezeGate() }
        extendContentContainer(activity, facade)
        removeTopShadowLine(activity, facade)
        suppressTipsLayer(session, facade)
        installBadgeMirrors(session, facade)
        suppressMidTab(session, facade)
        // 显隐门控 + 压制重申：应用沉浸路径 b(Boolean) 对 rg_main/vg_tips/vg_mid_tab 三者
        // setVisibility 8/0 同步开关——gate 按 bar.isShown 跟随；稳态对 alpha 压制失配重申
        session.mirrorVisibleGate = {
            reassertSuppression(session)
            session.tabContainer.isShown
        }
        // restore 通道（会话还原）：角标 overlay 摘除 + 压制还原 + 内容容器回规则
        session.onAuxGlassRestore = { restoreSessionEdits() }
        // 玻璃栏抬升（真实导航栏 inset，会话级缓存）：窗口延伸后玻璃贴窗口底会压在
        // 小白条上，按 inset 抬到导航条顶（芒果TV/淘宝/支付宝同款）。
        GlassInstaller.navInsetProvider = { computeNavInsetLift(session, activity) }
        // 静止降帧两档 + 高刷减半分频：由 TargetHook 会话创建期统一接线（引擎单管线，
        // 本适配器零旗标设置）。分频由引擎按实测帧间隔自动判定（偶数帧 tick 零拍频）
        // ——勿在此用 display.refreshRate 一次性采样自设限帧：LTPO 动态刷新率下读数
        // 随状态漂移（重进应用 120/90/60 各异），一次性判定跨会话不一致且非整数比率
        // 必拍频（真机实证，"重进应用卡顿复发"根因）。
        // 滑动冻结的持续检测源 = 引擎信号管线的会话级消费者（onContentActivity；
        // armActivityWindow 由管线统一做，此处只更新冻结状态）
        session.onContentActivity = { onContentActivitySignal() }
    }

    // —— 窗口延伸（№14.2 配方：flags + 同栈补位 + 玻璃栏抬升） ——

    /** 窗口延伸要求位：LAYOUT_STABLE|LAYOUT_HIDE_NAVIGATION——窗口 frame 延到导航条后
     *  且不退出 decor-fit（LiquidTab 明禁 setDecorFitsSystemWindows(false)）。应用自身
     *  只设状态栏位（x.j0 = 0x500），此二位补齐后内容画进小白条区域。 */
    private val REQUIRED_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    /** 玻璃栏宿主窗口（MainActivity decor，onTabActivityResumed 每次恢复期刷新；弱引用
     *  随 Activity 回收）。窗口延伸同栈补位只服务这一窗口——详情页/编辑器等无玻璃栏
     *  页面按原生安全区布局，一并延伸会让底部内容顶到屏幕底被导航条遮挡（真机实证）。 */
    @Volatile private var tabDecorRef: java.lang.ref.WeakReference<View>? = null

    /** 非宿主窗口跳过留痕（一窗口一次）：x.j0 全应用高频调用，逐次打日志会刷屏；
     *  弱键随窗口回收自动清空（读写 synchronized 兜底）。 */
    private val nonHostWindows = java.util.WeakHashMap<View, Boolean>()

    /** 是否玻璃栏宿主窗口。①宿主 decor 已登记且仍挂窗：严格按根视图 identity 判定
     *  （MainActivity 内的弹窗/对话框有独立 decor，不延伸）；②冷启动 onResume 前或
     *  宿主窗口重建（decor 未登记/旧窗口已解挂）：按 View.context 链 Activity 类名
     *  兜底——x.j0 在 MainActivity.onCreate 即调用，不能等到 onResume 才补位。
     *  兜底分支**不登记** decor：调用点未必是根视图（可能尚未挂树），误登记会污染
     *  后续 identity 判定；宿主 decor 由 onTabActivityResumed（onResume 之后）登记。 */
    private fun isHostWindow(view: View): Boolean {
        val host = tabDecorRef?.get()
        if (host != null && host.isAttachedToWindow) return view.rootView === host
        return contextActivityOrNull(view)?.javaClass?.name == TAB_ACTIVITY
    }

    /** View.context 链上溯 Activity（AppCompat/Theme 包装层透明）。上限防异常环。 */
    private fun contextActivityOrNull(view: View): Activity? {
        var ctx: android.content.Context? = view.context
        var guard = 0
        while (ctx is android.content.ContextWrapper && guard++ < 16) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** 非宿主窗口一次性留痕（带 Activity 类名，排障时可确认"详情页跳过延伸"生效）。 */
    private fun logNonHostWindowOnce(view: View, facade: HookFacade) {
        val decor = view.rootView
        synchronized(nonHostWindows) {
            if (nonHostWindows.containsKey(decor)) return
            nonHostWindows[decor] = true
        }
        val name = contextActivityOrNull(view)?.javaClass?.name ?: decor.context.javaClass.name
        facade.log(android.util.Log.INFO, "heybox window-extend: skipped non-host window ($name)")
    }

    /** 玻璃栏抬升缓存（-1 = 未解析，onResume 重置）：手势导航下该值是常量；切页 insets
     *  重派发期间瞬时读数跳变会让以抬升为源的几何抖动。 */
    @Volatile private var cachedNavInsetLift: Int = -1

    /** 玻璃栏抬升（真实导航栏 inset，会话级缓存，芒果TV/淘宝/支付宝同款）。 */
    private fun computeNavInsetLift(session: GlassSession, activity: Activity): Int {
        cachedNavInsetLift.takeIf { it >= 0 }?.let { return it }
        val resolved = runCatching {
            session.tabContainer.rootWindowInsets
                ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                ?.bottom?.coerceIn(0, 400)
        }.getOrNull() ?: -1
        if (resolved >= 0) cachedNavInsetLift = resolved
        return resolved
    }

    // —— 内容延伸（fl_container 填满 + 栏顶阴影线移除） ——

    /** contentReference：fl_container 原始 ABOVE 规则锚 id（restore 用）；0 = 无规则。 */
    @Volatile private var originalAboveAnchor: Int = 0
    @Volatile private var extendedContainerRef: java.lang.ref.WeakReference<View>? = null
    @Volatile private var activeSessionRef: java.lang.ref.WeakReference<GlassSession>? = null

    /** 内容延伸：fl_container（layout_above=rg_main，内容原生止于栏顶）移除 ABOVE 规则
     *  填满 vg_main_root——windowBackground=@null，不延伸则玻璃栏后无内容可采样。
     *  应用对它零运行时写入（MainActivity 全文件核对），单次写入 + restore 回规则。 */
    private fun extendContentContainer(activity: Activity, facade: HookFacade) {
        runCatching {
            val decor = activity.window?.decorView ?: return
            val container = findViewByIdByName(decor, CONTENT_ID_NAME) ?: return
            val lp = container.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
            originalAboveAnchor = lp.getRule(android.widget.RelativeLayout.ABOVE)
            lp.addRule(android.widget.RelativeLayout.ABOVE, 0)
            container.layoutParams = lp
            extendedContainerRef = java.lang.ref.WeakReference(container)
            facade.log(android.util.Log.INFO, "heybox content-extend: fl_container ABOVE rule removed")
        }.onFailure { facade.log(android.util.Log.WARN, "heybox content-extend failed", it) }
    }

    private fun restoreContentContainer() {
        val container = extendedContainerRef?.get() ?: return
        runCatching {
            val lp = container.layoutParams as? android.widget.RelativeLayout.LayoutParams ?: return
            lp.addRule(android.widget.RelativeLayout.ABOVE, originalAboveAnchor)
            container.layoutParams = lp
        }
        extendedContainerRef = null
    }

    /** 移除栏顶 3dp 渐变阴影线（activity_main 内匿名无 id 叶子小视图，应用无引用
     *  无法强转——区别于微博 iv_bottom_shadow 需同类型替身）。**先按资源 id 定位
     *  vg_main_root，再在其子树内做有界 DFS**：应用自身用 BlurView 库，content 的
     *  直接子项是 BlurTarget 包装层（真机 appRoot=BlurTarget 实证），vg_main_root 与
     *  阴影线在第三层——固定两层扫描漏扫（第二轮失配根因）。结构锚不做类名匹配、
     *  不读布局后字段：无 id + 叶子视图 + background 非空 + 高度命中——优先
     *  `layoutParams.height`（inflate 时 dimen 已解析为 px，装配先于首个 traversal
     *  也可判，真机实证 view.height 装配时刻恒 0），兜底 `height`；高度 =
     *  shadow_height dimen（运行时解析，容差 +2px；dimen 缺失回退 ≤32px）。vg_main_root
     *  子树内其余叶子（rg_main/vg_tips 条目、vg_mid_tab 内 ImageView）均有 id 或高度
     *  超限，不会误中。锚点全程未命中打一次性 WARN（№03.7 教训 11 口径）。 */
    private fun removeTopShadowLine(activity: Activity, facade: HookFacade) {
        runCatching {
            val decor = activity.window?.decorView ?: return
            val res = decor.resources
            val shadowDimen = runCatching {
                val id = res.getIdentifier("shadow_height", "dimen", decor.context.packageName)
                if (id != 0) res.getDimensionPixelSize(id) else -1
            }.getOrDefault(-1)
            val maxHeight = if (shadowDimen > 0) shadowDimen + 2 else 32
            val content = decor.findViewById<ViewGroup>(android.R.id.content) ?: return
            // 定位扫描根：vg_main_root（资源 id）；id 失效则从 content 起全树（有界）
            val rootId = res.getIdentifier("vg_main_root", "id", decor.context.packageName)
            val scanRoot: View = if (rootId != 0) content.findViewById(rootId) ?: content else content
            var removed = false
            fun dfs(v: View, depth: Int) {
                if (removed || depth > 8) return
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) v.getChildAt(i)?.let { dfs(it, depth + 1) }
                    return
                }
                if (v.id != View.NO_ID) return
                val lpHeight = v.layoutParams?.height ?: 0
                if (v.background != null &&
                    (lpHeight in 1..maxHeight || v.height in 1..maxHeight)
                ) {
                    (v.parent as? ViewGroup)?.removeView(v)
                    removed = true
                    facade.log(
                        android.util.Log.INFO,
                        "heybox: removed top shadow line (lp=$lpHeight h=${v.height}px, dimen=$shadowDimen)",
                    )
                }
            }
            dfs(scanRoot, 0)
            if (!removed) {
                facade.log(
                    android.util.Log.WARN,
                    "heybox: top shadow line not found (anchor miss, dimen=$shadowDimen, root=${scanRoot.javaClass.simpleName})",
                )
            }
        }.onFailure { facade.log(android.util.Log.WARN, "heybox remove shadow line failed", it) }
    }

    // —— 角标层压制 + ViewOverlay 镜像 ——

    private val suppressedViews = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, Float>())
    private val badgeMirrorDrawables = ArrayList<Pair<View, BadgeMirrorDrawable>>()

    /** 压制 vg_tips（兄弟角标覆盖层）alpha=0：绘制手调（overlay drawable 直接
     *  badge.draw）不施加父 alpha，镜像完整；isShown 不受 alpha 影响，显隐跟随正常。 */
    private fun suppressTipsLayer(session: GlassSession, facade: HookFacade) {
        runCatching {
            val tips = findViewByIdByName(session.tabContainer.rootView, TIPS_ID_NAME) ?: return
            suppressedViews[tips] = tips.alpha
            if (tips.alpha != 0f) tips.alpha = 0f
            facade.log(android.util.Log.INFO, "heybox: suppressed vg_tips overlay (badges -> slot ViewOverlay)")
        }.onFailure { facade.log(android.util.Log.WARN, "heybox suppress vg_tips failed", it) }
    }

    /** 角标 ViewOverlay 镜像：vg_tips 的 5 个等权 cell 与 rg_main 的 5 个 radio 列 1:1
     *  同几何对齐——收集每个 cell 内的叶子角标视图（iv_tips_1/3/4/5 红点 + iv_tips_2/
     *  msg_tips_2），包成 BadgeMirrorDrawable 挂进同列 RadioButton 的 ViewOverlay。
     *  相册"ViewOverlay 角标随镜像带走"同语义；rb_3 列 cell（vg_tips_3，GONE）同样
     *  挂载但 isShown 恒 false，draw 无操作。 */
    private fun installBadgeMirrors(session: GlassSession, facade: HookFacade) {
        runCatching {
            val tips = findViewByIdByName(session.tabContainer.rootView, TIPS_ID_NAME) as? ViewGroup ?: return
            val bar = session.tabContainer as? ViewGroup ?: return
            if (tips.childCount != bar.childCount) {
                facade.log(
                    android.util.Log.WARN,
                    "heybox badge mirror skipped: tips=${tips.childCount} vs bar=${bar.childCount}",
                )
                return
            }
            for (i in 0 until tips.childCount) {
                val cell = tips.getChildAt(i) ?: continue
                val radio = bar.getChildAt(i) ?: continue
                collectLeafViews(cell).forEach { badge ->
                    val drawable = BadgeMirrorDrawable(badge, radio)
                    radio.overlay.add(drawable)
                    badgeMirrorDrawables.add(radio to drawable)
                }
            }
            facade.log(android.util.Log.INFO, "heybox: installed ${badgeMirrorDrawables.size} badge mirror overlay(s)")
        }.onFailure { facade.log(android.util.Log.WARN, "heybox badge mirror install failed", it) }
    }

    private fun restoreBadgeMirrors() {
        for ((host, drawable) in badgeMirrorDrawables) {
            runCatching { host.overlay.remove(drawable) }
        }
        badgeMirrorDrawables.clear()
    }

    private fun collectLeafViews(root: View): List<View> {
        val leaves = ArrayList<View>(2)
        fun walk(v: View) {
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) v.getChildAt(i)?.let { walk(it) }
            } else {
                leaves.add(v)
            }
        }
        walk(root)
        return leaves
    }

    /** 单个角标的 ViewOverlay 镜像 drawable：draw 时按窗口坐标差平移到对应 radio 列的
     *  位置后直绘角标（View.draw 手调不施加 vg_tips 的 alpha=0）；badge.isShown 逐帧
     *  判定显隐——K4 数据开关（单个角标 visibility）与沉浸隐藏（vg_tips GONE）都自然
     *  跟随；坐标差每次 draw 现算（两数组复用，角标 ≤5 个且多数隐藏，开销可忽略）。 */
    private class BadgeMirrorDrawable(private val badge: View, private val host: View) : android.graphics.drawable.Drawable() {
        private val srcPos = IntArray(2)
        private val dstPos = IntArray(2)

        override fun draw(canvas: android.graphics.Canvas) {
            if (!badge.isShown || badge.width == 0 || badge.height == 0) return
            badge.getLocationInWindow(srcPos)
            host.getLocationInWindow(dstPos)
            canvas.save()
            canvas.translate((srcPos[0] - dstPos[0]).toFloat(), (srcPos[1] - dstPos[1]).toFloat())
            badge.draw(canvas)
            canvas.restore()
        }

        override fun getIntrinsicWidth(): Int = badge.width
        override fun getIntrinsicHeight(): Int = badge.height
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    // —— 悬浮发布键（vg_mid_tab）→ descriptor.extraActionButton 玻璃栏右侧圆形按钮 ——

    private var midTabViewRef: java.lang.ref.WeakReference<View>? = null

    /** 发布键图像产物（图标字形 + 强调色），按原 drawable 实例缓存——换肤/深浅色切换
     *  重建 drawable 后自动重算（§0.5 B.5 换肤重建映射纪律）。失败也缓存（null 值 +
     *  containsKey 区分），失败不打每帧重解码。 */
    private val publishArtCache = java.util.WeakHashMap<android.graphics.drawable.Drawable, PublishArt?>()

    private class PublishArt(
        val icon: android.graphics.drawable.Drawable,
        val accent: Color?,
    )

    /** 装配期压制 vg_mid_tab（快速发布键，玻璃栏条带内）：alpha=0 隐藏原生形态——
     *  玻璃栏内的呈现改由 descriptor.extraActionButton 圆形着色玻璃按钮承担（引擎组件），
     *  点击经按钮 onClick → performClick 转发原生 onClick 链路。 */
    private fun suppressMidTab(session: GlassSession, facade: HookFacade) {
        runCatching {
            val view = midTabViewOrNull(session) ?: return
            suppressedViews[view] = view.alpha
            if (view.alpha != 0f) view.alpha = 0f
            facade.log(android.util.Log.INFO, "heybox: suppressed vg_mid_tab (publish -> extra action button)")
        }.onFailure { facade.log(android.util.Log.WARN, "heybox suppress vg_mid_tab failed", it) }
    }

    /** 解析 vg_mid_tab（CardView 发布键，弱引用缓存）。 */
    private fun midTabViewOrNull(session: GlassSession): View? {
        val cached = midTabViewRef?.get()
        if (cached != null && cached.isAttachedToWindow) return cached
        val found = findViewByIdByName(session.tabContainer.rootView, MID_TAB_ID_NAME) ?: return null
        midTabViewRef = java.lang.ref.WeakReference(found)
        return found
    }

    /** 每帧调用（extraActionButton.icon）：发布键字形 drawable——从原图标位图提取加号
     *  字形（与背景基色的通道差 → alpha，原 RGB 保留：日间白字形/夜间深字形随原色
     *  渲染在着色玻璃表面上即原生按钮观感），顺带压制原生视图 alpha=0。 */
    private fun publishIconDrawable(session: GlassSession): android.graphics.drawable.Drawable? {
        val view = midTabViewOrNull(session) ?: return null
        if (view.alpha != 0f) view.alpha = 0f
        return publishArt(view)?.icon
    }

    /** 每帧调用（extraActionButton.accent）：强调色 = 原加号背景基色（边缘环不透明像素
     *  均值——中心是加号字形、边缘即背景）。null = 引擎纯玻璃不着色（静默降级）。 */
    private fun publishAccentColor(session: GlassSession): Color? {
        val view = midTabViewOrNull(session) ?: return null
        return publishArt(view)?.accent
    }

    private fun publishArt(view: View): PublishArt? {
        val imageView = firstImageViewOrNull(view) ?: return null
        val drawable = imageView.drawable ?: return null
        synchronized(publishArtCache) {
            if (publishArtCache.containsKey(drawable)) return publishArtCache[drawable]
        }
        val art = decodePublishArt(view.resources, drawable)
        synchronized(publishArtCache) { publishArtCache[drawable] = art }
        return art
    }

    /** 解码发布键图像：①边缘环（2px）分 上/下/左/右 四边求平均色，强调色 = 四边均值，
     *  背景 = 四边均值在归一化 (u,v) 上的**双线性插值**（原图背景是平滑对角渐变——
     *  全局均值差会把渐变远端留在字形里呈矩形残影，场估计残差 ≲ 12）；②每像素与
     *  估计背景的最大通道差，(差 − 14) × 6 clamp → 字形 alpha（原 RGB 保留：日间白
     *  字形/夜间深字形随原色渲染在着色玻璃表面上即原生按钮观感）。 */
    private fun decodePublishArt(
        resources: android.content.res.Resources,
        drawable: android.graphics.drawable.Drawable,
    ): PublishArt? = runCatching {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 四边平均色（各边 2px 环带，alpha < 128 剔除）
        var tr = 0L; var tg = 0L; var tb = 0L; var tn = 0
        var br = 0L; var bg = 0L; var bb2 = 0L; var bn = 0
        var lr = 0L; var lg = 0L; var lb = 0L; var ln = 0
        var rr = 0L; var rg = 0L; var rb = 0L; var rn = 0
        for (y in 0 until h) for (x in 0 until w) {
            val onEdge = x < 2 || y < 2 || x >= w - 2 || y >= h - 2
            if (!onEdge) continue
            val p = pixels[y * w + x]
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            when {
                y < 2 -> { tr += r; tg += g; tb += b; tn++ }
                y >= h - 2 -> { br += r; bg += g; bb2 += b; bn++ }
            }
            when {
                x < 2 -> { lr += r; lg += g; lb += b; ln++ }
                x >= w - 2 -> { rr += r; rg += g; rb += b; rn++ }
            }
        }
        if (tn == 0 || bn == 0 || ln == 0 || rn == 0) return null
        val topR = (tr / tn).toFloat(); val topG = (tg / tn).toFloat(); val topB = (tb / tn).toFloat()
        val botR = (br / bn).toFloat(); val botG = (bg / bn).toFloat(); val botB = (bb2 / bn).toFloat()
        val leftR = (lr / ln).toFloat(); val leftG = (lg / ln).toFloat(); val leftB = (lb / ln).toFloat()
        val rightR = (rr / rn).toFloat(); val rightG = (rg / rn).toFloat(); val rightB = (rb / rn).toFloat()
        // 强调色 = 四边均值（着色玻璃表面的 tint）
        val accent = Color(
            ((topR + botR + leftR + rightR) / 4f).toInt(),
            ((topG + botG + leftG + rightG) / 4f).toInt(),
            ((topB + botB + leftB + rightB) / 4f).toInt(),
        )

        val outPixels = IntArray(w * h)
        val w1 = (w - 1).toFloat()
        val h1 = (h - 1).toFloat()
        for (y in 0 until h) {
            val v = y / h1
            // 垂直插值（上↔下）
            val vr = topR + (botR - topR) * v
            val vg = topG + (botG - topG) * v
            val vb = topB + (botB - topB) * v
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                if ((p ushr 24) < 128) {
                    outPixels[i] = 0
                    continue
                }
                val u = x / w1
                // 水平插值（左↔右），与垂直插值取均值 = 双线性场估计
                val hr = leftR + (rightR - leftR) * u
                val hg = leftG + (rightG - leftG) * u
                val hb = leftB + (rightB - leftB) * u
                val ebr = (vr + hr) / 2f
                val ebg = (vg + hg) / 2f
                val ebb = (vb + hb) / 2f
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val diff = maxOf(abs(r - ebr.toInt()), abs(g - ebg.toInt()), abs(b - ebb.toInt()))
                val a = ((diff - 14) * 6).coerceIn(0, 255)
                outPixels[i] = (a shl 24) or (p and 0x00FFFFFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        PublishArt(android.graphics.drawable.BitmapDrawable(resources, out), accent)
    }.getOrNull()

    private fun firstImageViewOrNull(root: View): ImageView? {
        if (root is ImageView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.let { firstImageViewOrNull(it)?.let { iv -> return iv } }
            }
        }
        return null
    }

    // —— 稳态重申与 restore ——

    /** 每帧调用（mirrorVisibleGate）：对 alpha 压制视图失配重申（应用若新增写回路径，
     *  与芒果TV mirrorGate 同款兜底；稳态零写入）。 */
    private fun reassertSuppression(session: GlassSession) {
        synchronized(suppressedViews) {
            for ((v, _) in suppressedViews) {
                if (v.alpha != 0f) v.alpha = 0f
            }
        }
    }

    /** 会话还原（引擎 onAuxGlassRestore）：角标 overlay 摘除 + alpha 压制还原 +
     *  内容容器 ABOVE 规则回填。 */
    private fun restoreSessionEdits() {
        activeSessionRef = null
        restoreBadgeMirrors()
        synchronized(suppressedViews) {
            for ((v, original) in suppressedViews) {
                runCatching {
                    if (v.alpha == 0f && original != 0f) v.alpha = original
                }
            }
            suppressedViews.clear()
        }
        restoreContentContainer()
    }

    private fun findViewByIdByName(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", root.context.packageName)
        return if (id != 0) root.findViewById(id) else null
    }

    // —— 拖动悬停预览（§0.5）：直控翻转 + 动画口吞咽 + 落定直驱 ——

    /** 预览选中翻转（itemCheckedApplier）：setChecked 有切页（RadioGroup 级联 →
     *  onCheckedChanged → 切页口）/动画（h 重播选中动画）双副作用，反射通道不可用——
     *  状态照走应用自身 checked 通道（字体/文字色选择器/皮肤 selector 天然跟随），
     *  动画口 h 由 hook 按预览标志吞咽、落定由 g 直驱末帧（§0.5 B.1 动画口/直置口
     *  并存必用直置口、B.2 落定取自编程路径）。同态去重：重复 setChecked 会重放级联。 */
    private fun flipChecked(item: View, checked: Boolean) {
        val button = item as? CompoundButton ?: return
        if (button.isChecked == checked) return
        previewFlipping = true
        try {
            button.isChecked = checked
            settleIconLottie(button)
        } finally {
            previewFlipping = false
        }
    }

    /** 落定直驱：条目顶部 compound drawable 解包出内层 LottieDrawable（默认路径顶层
     *  是 StateListDrawable{checked: lottie, default: 静态 PNG}——顶层类名无 lottie
     *  前缀，直接判定会静默跳过），反射调 AccelWorldLottieKt.g 跳到主题 marker 段
     *  末帧（应用自身落定配方——皮肤路径 onCheckedChanged else 分支同款语义）。
     *  解包链：①包装构造口捕获映射（主）；②StateListDrawable.getStateDrawable(0) 隐藏 API
     *  反射（兜底，外壳结构 index0 = checked lottie）。锚点失效静默降级：预览期图标
     *  短暂保持未选中态后随真实选中恢复，无崩溃路径。 */
    private fun settleIconLottie(button: CompoundButton) {
        val method = settleMethod ?: return
        runCatching {
            val drawable = button.compoundDrawables.getOrNull(1) ?: return
            val lottie = resolveLottieDrawable(drawable) ?: return
            method.invoke(null, lottie)
        }
    }

    /** 顶层 drawable → 内层 LottieDrawable：①包装构造口捕获映射；②StateListDrawable
     *  getStateDrawable(0) 隐藏 API 反射（LSPosed 环境豁免隐藏 API 限制）。两路皆
     *  失败返回 null（静默跳过落定）。 */
    private fun resolveLottieDrawable(top: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable? {
        if (top.javaClass.name.startsWith("com.airbnb.lottie.")) return top
        if (top is android.graphics.drawable.StateListDrawable) {
            synchronized(stateListLottieMap) {
                stateListLottieMap[top]?.let { return it }
            }
            runCatching {
                val getState = top.javaClass.getDeclaredMethod("getStateDrawable", Int::class.javaPrimitiveType)
                getState.isAccessible = true
                val child = getState.invoke(top, 0) as? android.graphics.drawable.Drawable ?: return null
                if (child.javaClass.name.startsWith("com.airbnb.lottie.")) return child
            }
        }
        return null
    }
}
