package com.moji.liquidglass.hook.adapters

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.ui.graphics.Color
import com.moji.liquidglass.config.ModuleConfig
import com.moji.liquidglass.glass.ExtraActionButtonSpec
import com.moji.liquidglass.glass.GlassSession
import com.moji.liquidglass.glass.IconMode
import com.moji.liquidglass.glass.TabBarDescriptor
import com.moji.liquidglass.hook.HookFacade
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 小红书（com.xingin.xhs，第三方应用）适配器。基准版本（多版本兼容窗口，§0.6.3）：
 * 9.52.1（versionCode 9521800）/ 9.50.0（versionCode 9500803）——9.52.1 起切页管线
 * 混淆名整包迁移（n4d→ntc，成员字母整体位移），全部反射锚点为双版候选链；免适配版
 * 9.51.0（锚点同 9.50.0）走 9.50.0 候选腿，登记于 AdaptedVersionRegistry。
 *
 * - 主 Tab Activity：`com.xingin.xhs.index.v2.IndexActivityV2`（launcher，singleTask；
 *   LCBActivity 薄壳，底栏由后续 content 挂载，Activity 类本身不引用底栏类）。
 * - 底栏容器：`com.xingin.xhs.homepage.tabbar.TabBarView`（ConstraintLayout 子类，非混淆
 *   类名），挂载布局以它为根；另有同构商城版 `com.xingin.commercial.shop.malltabbar.TabBarView`
 *   （独立类、非继承，5 槽位结构一致），findTabBar 双类名匹配兜底。
 * - **资源 id 全表混淆**（AndResGuard，资源名 dump 全为 0_resource_name_obfuscated）：
 *   getIdentifier 不可用，定位只靠类名 + 子项结构（区别于微博 main_radio 资源名锚点）。
 * - 每 tab 视图：`homepage.tabbar.TabView`（ConstraintLayout 子类，非自绘）。原生 5 槽位 =
 *   首页/购物 TabView + **发布中键（RelativeLayout 包 ImageView+LottieAnimationView，
 *   非 TabView）** + 消息/我 TabView；容器第 0 个子项是纯 ImageView 装饰条。
 *   **v3.0 起玻璃栏 4 槽**：tabViewsOf 只收 TabView 条目，发布键退出镜像槽位、改由
 *   descriptor.extraActionButton 承担（见下方发布键条目）。
 * - 选中态：应用原生走标准 `View.setSelected` + `state_selected` selector
 *   （TabBarView.setXxxSelected 直接调 TabView.setSelected；图标经 setDrawable 合成
 *   selector、文字走 ColorStateList）——引擎逐帧 isSelected 读数/装配初值零改动工作，
 *   **无需选中桥接 hook**。
 * - 模式：WHOLE_TAB_FULL 整条目镜像（镜像含原生文字/角标/选中外观；引擎不绘标签、
 *   不染色）。预览选中翻转：TabView 重写了 `setSelected(boolean)`（公有、单布尔参），
 *   描述符声明 itemCheckedSetterMethodName = "setSelected" 走引擎反射通道；翻转只驱动
 *   原生 selector + 文字缩放动画，无切页副作用（切页走点击派发链路，不经 setSelected）。
 * - **纯文字底栏（应用设计，非缺陷）**：TabView 内图标 AppCompatImageView 在布局
 *   XML（r/lpv.xml）里默认 GONE，9.x 首页底栏就是纯文字（选中态仅文字放大 1.1），
 *   镜像如实呈现即正确观感；badge 三件套默认 GONE、由 RedDotPresenter 经 r4() 控制。
 * - **内容延伸（信息流全屏）**：内容根 ExploreScrollableViewPager 在布局 lpt/nyn.xml
 *   写死 `layout_marginBottom = 45dp`（底栏可视高），classes13 全量反编译无运行时改写
 *   点（导航栏 inset 由底栏自己吃：高度 45dp+inset + paddingBottom=inset）——装配时
 *   一次性归零该 margin（微博 tabcontent 归零同构且无运行时写回口）；外层 pager 冷启动
 *   count=1→4 异步、offscreenPageLimit(1)，消息/我的页晚挂载且 ContentView 可能重建，
 *   故以 TabView.setSelected（= 应用切页信号 jcd 链 J0 的落点）after hook 防抖重扫兜底。
 *   不随模块开关还原（重启应用即原生，微博先例）。
 * - **条目顶部留白中和（玻璃栏垂直居中）**：TabView 固定 60dp 高 + `paddingTop=15dp`
 *   设计留白，文字在余下 45dp 内容区居中（中心在条目 62% 高度处）；整条目 1:1 居中
 *   镜像会把这份重心偏下如实搬进玻璃槽位 → 装配时把抑制态条目 paddingTop 归零，
 *   ConstraintSet 约束不动、文字自动回到整条目垂直居中（padding 不在 ConstraintSet
 *   管理范围，应用切换选中态不会写回）。原生底栏已被 suppressNatively 隐藏，对应用
 *   原生视觉零影响——与高德"抑制态条目装饰中和"同一手法。
 * - **发布键独立按钮（v3.0，extraActionButton 通用通道，小黑盒 №15 同款形态）**：发布键
 *   退出玻璃槽位后原生视图随整栏 suppressNatively（alpha=0 + clickable 树禁用）隐藏，
 *   无需单独压制（小黑盒 vg_mid_tab 是 RadioGroup 兄弟层才需单独压）。玻璃栏右侧圆形
 *   **着色**玻璃按钮：**accent = 加号底色**（原图标 drawable 不透明像素直方图众数，
 *   静态实证 #FF2442 红；按实例缓存、换图/深浅色自动重算）+ **icon = 自绘白色圆头十字
 *   加号**（v3.0.2 用户定案：不使用原图标字形——v3.0 原样绘制红底残留、v3.0.1 通道差
 *   提取真机复验红底残留且位图几何偏心，自绘天然居中且免疫运行时 drawable 形态未知，
 *   见 plusIcon）；点击 = getIndexPost getter 穿透（performClick 不检查 isClickable，
 *   引擎 clickable 树禁用不影响，v2.2 槽位 2 即此路径）→ 失配回退装配期捕获的
 *   postKeyRef（capturePostKey）。装配时对发布键无任何视图手术（v1.4 raisePostIcon 的
 *   8dp 抬升随镜像退出一并删除）。
 * - **消息/我的页小白条区域内容（v2.6 页面容器导航衬垫归零）**：应用 SystemBarsManager
 *   从 ROOT navigation insets（rootWindowInsets 直读）把小白条高度写入页面容器底部衬垫
 *   （真机实证：消息页 = MessagePageView pb=54；我的页 = ProfileMainPageNoteInfoView
 *   pb=54，塌陷装配期写入）——content 层派发 insets 清零（installContentInsetsZeroing）
 *   拦不住 root 直读通路（两条 inset 通路必须分开处理，LiquidTab XhsMessageViewport
 *   定案），页面布局盒被衬垫整体抬离玻璃栏区 = 小白条区域无内容。列表余量 pass 同一轮
 *   DFS 内嵌 viewport 归零（applyViewportFit：消息页类名锚点 + 值驱动 [inset/2, inset]
 *   任意大容器兜底；发现记录原值清零、重申、离页还原）+ decor OnGlobalLayout 布局重申
 *   （装配期 60ms 追频 / 稳态 O(k) 廉价快路径）+ 列表余量单调化（只增不减，塌陷收缩期
 *   零改写零重布局）——v2.5 只修了余量 pass 误伤 ViewPager2 内部容器的布局抬离，衬垫
 *   这一显示层根因由 v2.6 补齐。
 * - installAppHooks（v2.4 共 3 个行为 hook）：①`TabView.setSelected` after → 储备
 *   重扫/列表余量/页面容器衬垫归零触发（应用切页信号 J0 的落点，300ms 防抖）；②切页入口 after → 切页
 *   同步重放（replayShowPage，候选链 9.52.1 ntc.p.C2 / 9.50.0 n4d.n.i2）；③切页延迟体
 *   invoke before → 按序号陈旧性自行吞咽（swallowStaleDeferredBody，候选链 ntc.l /
 *   n4d.l）——应用守卫第二条件是商城 tab 开关配置位，配置 false 时序号守卫失效，空闲
 *   冲刷时陈旧延迟体重执行 = 跳回首次切换的 tab（v2.4 修复）。v2.1 的
 *   `n4d.n` 构造捕获已删——v2.2 点击派发改 LiquidTab performTap 式（TabBarView
 *   getIndexXxx 真实名私有 getter + 穿透点击，见 dispatchTabClick），不再主动调切页
 *   入口，controller 来自被拦截调用的 thisObj。v1.4–v1.9
 *   的三个取证/兜底 hook（u4c.d0$a.onClick、pager.setCurrentItem、
 *   PagerAdapter.notifyDataSetChanged）亦已删；反射目标全部进程内缓存（v2.1），渲染侧
 *   接入 lightweightSampling 去重采样（3×全树绘制/帧 → 1×）。选中桥接（原生
 *   isSelected）、配色捕获（WHOLE_TAB_FULL 不需要）、预览副作用吞咽（setSelected 无
 *   切页副作用）均无需求。
 *
 * - **切页机制（v1.8 同步忠实重放，取代 v1.6/v1.7 直驱；v2.3 双版候选链）**：应用切页入口
 *   （9.52.1 `ntc.p.C2` / 9.50.0 `n4d.n.i2`）同步段写状态/发事件/序号自增后，把切页动作
 *   挂 IdleHandler（9.52.1 `f4c.f.r0` / 9.50.0 `oec.f.S0` = MessageQueue.IdleHandler）——
 *   玻璃帧循环使主队列整场不空闲，延迟体前台永不执行 = 点击滞留（v1.4 真机 35 连发滞留
 *   至 activity pause 集中爆发）；延迟体除 setCurrentItem 外还含懒加载激活
 *   （IndexPagerLazyLoad → setMaxLifecycle(RESUMED)，9.52.1 字段 e.ntc.t0.a / 9.50.0
 *   Y1().n4d.u0.b）——v1.6/v1.7 直驱 setCurrentItem 跳过它即"非首页空白"的根因，且丢失
 *   page 0 的回顶语义（9.50.0 延迟体经 y0()，其方法体即 setCurrentItem(0,false)；9.52.1
 *   延迟体 page 0 直接 setCurrentItem；两版"点击已选中 tab"的回顶刷新都由应用点击管线
 *   自管，不在延迟体）。v1.8 玻璃点击经切页入口全管线派发，入口 after
 *   hook 同步重放延迟体（含懒加载激活），随后自增序号字段（9.52.1 B / 9.50.0 G）；滞留
 *   延迟体在空闲冲刷时的幂等跳过由 v2.4 延迟体吞咽 hook 按序号陈旧性自行强制（应用守卫
 *   第二条件依赖商城 tab 开关配置位、不可依赖，见 installAppHooks ③）。引擎侧配套"静态
 *   空闲让步窗口"（GlassSession
 *   shouldIdleYield）冲刷其他未知的 idle 挂载任务。
 *
 * - **冷启动 count 窗口（v1.9 挂起 → v2.0 自增长 → v2.5 装配后预增长）**：外层 pager 的
 *   IndexPagerAdapterV2 冷启动 count=1（public int r，getCount() 直返），增长任务由
 *   onAttach 经 IdleHandler 排队（AB andr_homefeed_delay_init_fragment_ab；9.50.0 =
 *   n4d.n.onAttach case 15、9.52.1 = ntc.p.onAttach，且新增按需口 o2() expandTabCount——
 *   方法体同为 `r=4 + notifyDataSetChanged()`，与本适配器 growExplorePagerAdapter 字节
 *   等价）——帧循环饿死 IdleHandler 时 count 长期 =1（v2.7.log 实测 30s+），且
 *   ViewPager.setCurrentItemInternal 对 `item >= count` **钳位到 count-1 并静默丢弃**
 *   （不存储请求——v1.7"记忆 mCurItem 补切"前提证伪），窗口内一切 setCurrentItem（含
 *   应用自己滞留的延迟体）都是 no-op = "一开始点击没反应"。v2.0 在 count 未就绪时
 *   **字节级重放增长任务**（r=4 + notifyDataSetChanged）后当场切页；r 字段/adapter
 *   失配才降级挂起（pendingPage，500ms×20 兜底 ticker 补做）。v2.5 预增长：装配后
 *   800ms 主动执行同一增长（1.6s/3.2s 有界重试），恢复原生"启动后首空闲增长、页面
 *   预实例化"时序，消除首次点击 ~0.5s 的现场实例化延迟。
 */
object XhsAdapter : AppAdapter {

    override val targetPackage: String = "com.xingin.xhs"
    override val displayName: String = "小红书"
    override val tabActivityClassName: String? = "com.xingin.xhs.index.v2.IndexActivityV2"
    override val baselineVersions: List<String> = listOf("9.50.0", "9.52.1")

    private const val BAR_CLASS = "com.xingin.xhs.homepage.tabbar.TabBarView"
    private const val MALL_BAR_CLASS = "com.xingin.commercial.shop.malltabbar.TabBarView"
    private const val ITEM_CLASS = "com.xingin.xhs.homepage.tabbar.TabView"
    private const val MALL_ITEM_CLASS = "com.xingin.commercial.shop.malltabbar.TabView"

    /** 首页内容根 ViewPager（布局 lpt/nyn.xml 内 marginBottom=45dp 的储备承载者，非混淆类名）。 */
    private const val EXPLORE_PAGER_CLASS = "com.xingin.redview.viewpage.ExploreScrollableViewPager"

    /** ViewPager2（androidx，两版均未混淆）：「我的页」内容区容器。ViewPager2 继承
     *  ViewGroup、内部藏一个撑满自身的 RecyclerView 作页面容器——列表余量 pass 对该容器
     *  加 paddingBottom 会把整页内容抬离玻璃栏区（RecyclerView 子项在 padding 盒内布局，
     *  clipToPadding 只影响裁剪不影响布局盒），须整类排除（见 isViewPager2Scoped）。 */
    private const val VIEW_PAGER_2_CLASS = "androidx.viewpager2.widget.ViewPager2"

    /** 消息页根视图（非混淆类名，LiquidTab 同款锚点）：应用 SystemBarsManager 从
     *  ROOT navigation insets（rootWindowInsets 直读）把小白条高度写入其 paddingBottom——
     *  content 层派发 insets 清零（installContentInsetsZeroing）拦不住该通路，视图层归零
     *  是唯一解（见 viewportFits / applyViewportFit）。 */
    private const val MESSAGE_PAGE_CLASS = "com.xingin.im.v2.message.MessagePageView"

    // —— 应用切页管线（controller = 首页 ContentViewController，反编译实证；9.52.1 =
    //    ntc.p（classes19）、9.50.0 = n4d.n（classes13）——9.52.1 整包迁移 n4d→ntc 且成员
    //    字母整体位移：切页入口 i2→C2、序号 G→B、presenter n4d.n0→ntc.o0（t0→x0）、
    //    懒加载 n4d.u0.b→ntc.t0.a（访问口 Y1() 方法→字段 e），延迟段仍把切页动作挂
    //    MessageQueue.IdleHandler（oec.f.S0 → f4c.f.r0）。混淆名按双版钉扎为候选链；
    //    序号字段与懒加载方法按命中的类名取表——盲序候选会错绑（n4d.n 上 B:I 已被语义
    //    无关字段占用、n4d.u0 上另有语义不同的 a(I)），见 SEQ_FIELDS_BY_CONTROLLER /
    //    LAZY_METHODS_BY_LOADER。应用更新改名时 hook 安装失败打 WARN、派发自动降级
    //    直驱（不劣于 v1.7 行为）。 ——

    /** 切页管线候选（新名优先，controller 类名 × 切页入口成对）：切页全管线入口
     *  （public final (I)V），玻璃点击经它走应用完整管线（懒加载激活等语义保真）。 */
    private val SHOW_PAGE_CANDIDATES = arrayOf(
        "ntc.p" to "C2",   // 9.52.1（versionCode 9521800）
        "n4d.n" to "i2",   // 9.50.0（含同锚点的免适配版 9.51.0）
    )

    /** presenter 访问器（在两版共同基类上，同名）。 */
    private const val PRESENTER_GETTER = "getPresenter"

    /** pager 访问器候选（presenter 上，新名优先；两版无同形无参方法，顺序安全）。 */
    private val PAGER_GETTERS = arrayOf("x0", "t0")

    /** 懒加载激活链：IndexPagerLazyLoad（setMaxLifecycle(fragment, RESUMED) 页面激活，
     *  直驱跳过此步即"非首页空白"根因）。访问口候选：9.50.0 经 controller 方法 Y1()，
     *  9.52.1 直接读字段 e（两名字在两类上互斥，顺序无碰撞）。 */
    private const val LAZY_ACCESS_METHOD = "Y1"
    private const val LAZY_ACCESS_FIELD = "e"

    /** 懒加载激活方法（(I)V)按命中 loader 类名取：n4d.u0 上另有语义不同的 a(I)，盲序
     *  候选会错绑。 */
    private val LAZY_METHODS_BY_LOADER = mapOf(
        "ntc.t0" to "a",   // 9.52.1
        "n4d.u0" to "b",   // 9.50.0
    )

    /** 切页序号字段：延迟体守卫 `capturedSeq != field && flag` 判定是否被更新点击取代；
     *  重放后自增使滞留的 IdleHandler 幂等跳过（仅服务该守卫）。按命中 controller 类名
     *  取：n4d.n 上 B:I 已被语义无关字段占用，盲序候选会错绑。 */
    private val SEQ_FIELDS_BY_CONTROLLER = mapOf(
        "ntc.p" to "B",   // 9.52.1
        "n4d.n" to "G",   // 9.50.0
    )

    /** 切页延迟体 lambda 候选（切页动作挂 IdleHandler 的载体，invoke(Object)Object）：
     *  Triple(类名, capturedSeq 字段, controller 字段)。invoke 前按序号陈旧性自行吞咽
     *  （见 installAppHooks 延迟体守卫 hook）——应用守卫的第二条件是商城 tab 开关配置位
     *  （9.52.1 ntc.p.D / 9.50.0 n4d.n.Z1→H，同为 show_mall_tab_bar 系列），配置 false
     *  时序号守卫整体失效，不能依赖。 */
    private val DEFERRED_BODY_CANDIDATES = arrayOf(
        Triple("ntc.l", "a", "b"),   // 9.52.1（captured seq / controller）
        Triple("n4d.l", "d", "e"),   // 9.50.0
    )
    private const val DEFERRED_INVOKE = "invoke"

    /** IndexPagerAdapterV2 的 count 字段（public int r，初始 1；getCount() 直返此值）。
     *  应用 growth 任务（onAttach 经 IdleHandler 排队）就是 `r = 4 +
     *  notifyDataSetChanged()` 两行（9.52.1 ntc.f.invoke 增长 case 字节实证）。 */
    private const val PAGER_COUNT_FIELD = "r"

    /** 外层 pager 满页数（应用增长任务的目标值，两版一致）。 */
    private const val PAGER_FULL_COUNT = 4

    /** 预增长重试间隔：装配后 800ms 首试（避开冷启动首帧与玻璃装配同一批主线程工作），
     *  adapter 未就绪时 1.6s / 3.2s 各重试一次（有界，见 preGrowExplorePager）。 */
    private val PRE_GROW_RETRY_DELAYS = longArrayOf(1600L, 3200L)

    /** TabBarView 的条目 getter（真实方法名，非混淆类上的私有方法；LiquidTab
     *  XhsNavigationState.performTap 同款锚点，比业务混淆名抗版本漂移）：数组序 =
     *  原生 5 槽位序（2 = 发布键 RelativeLayout），玻璃槽位经 SLOT_GETTER_INDICES 查表。 */
    private val TAB_GETTERS = arrayOf(
        "getIndexHome", "getIndexStore", "getIndexPost", "getIndexMessage", "getIndexMe",
    )

    /** 玻璃槽位 → TAB_GETTERS 索引（v3.0 发布键退出槽位后玻璃栏 4 槽：首页/购物/消息/我，
     *  发布键不再是槽位，其 getter（索引 2）由 extraActionButton 的 dispatchPostClick 专用）。 */
    private val SLOT_GETTER_INDICES = intArrayOf(0, 1, 3, 4)

    // —— hook/装配期写入的运行时状态 ——

    /** 发布中键（RelativeLayout，原生 5 槽位序 2）装配期捕获：退出玻璃槽位后由
     *  descriptor.extraActionButton 玻璃栏右侧圆形按钮承担（图标逐帧取其首个 ImageView
     *  的 drawable，点击经 getIndexPost getter 穿透；本引用 = getter 失配的点击回退）。 */
    @Volatile
    private var postKeyRef: java.lang.ref.WeakReference<View>? = null

    /** 首次捕获的内容储备值（pager 原始 marginBottom，px），重扫日志对照用。 */
    @Volatile
    private var capturedReservePx: Int = 0

    /** 装配所在 Activity（onSessionCreated 写入）：选中态触发的储备重扫用。 */
    @Volatile
    private var hostActivity: Activity? = null

    private val sweepHandler = Handler(Looper.getMainLooper())
    private val sweepScheduled = AtomicBoolean(false)

    /** 列表余量/衬垫 pass 调度合并标志（v2.6.3）：拖动预览连翻/冷启动初选中会在同帧多次
     *  触发 scheduleListPass——立即轮与补扫对各自 pending 合并，同窗多次触发只跑一轮
     *  DFS（全 decor 遍历是适配器侧最贵的主线程工作，预览连翻期间逐次直跑曾成突发）。 */
    private val listPassImmediateScheduled = AtomicBoolean(false)
    private val listPassBackstopScheduled = AtomicBoolean(false)

    /** 当前活跃会话（onSessionCreated 写入）：列表余量/选中读数等会话期操作用。 */
    @Volatile
    private var activeSession: GlassSession? = null

    /** installAppHooks 捕获的 facade：列表余量/挂起补做等 hook 期日志用。 */
    @Volatile
    private var hookFacade: HookFacade? = null

    // —— 冷启动 count 窗口（v1.9 挂起 → v2.0 自增长）：外层 pager 的 adapter 冷启动
    //    count=1，增长任务经 IdleHandler 排队被帧循环饿死（30s+ 实证）；ViewPager 对越界
    //    setCurrentItem 钳位静默丢弃。v2.0 首选字节级重放应用的增长任务当场就绪，本组
    //    状态仅在增长反射失败时兜底挂起，由增长信号补做。——

    /** 挂起的切页目标（-1 = 无挂起）。后到覆盖先到（与应用 G 序号守卫同语义）。 */
    @Volatile
    private var pendingPage: Int = -1

    @Volatile
    private var pendingPagerRef: java.lang.ref.WeakReference<View>? = null

    @Volatile
    private var pendingControllerRef: java.lang.ref.WeakReference<Any>? = null

    /** 读 pager 当前 adapter 页数（adapter 为 null 或反射失败返回 -1）。 */
    private fun pagerAdapterCount(pager: View): Int = runCatching {
        val adapter = cachedMethod(pager.javaClass, "getAdapter")?.invoke(pager)
            ?: return -1
        (cachedMethod(adapter.javaClass, "getCount")?.invoke(adapter) as? Number)?.toInt() ?: -1
    }.getOrDefault(-1)

    /** 自增长外层 pager（v2.0）：应用在 onAttach 经 IdleHandler 排队的增长任务
     *  （AB andr_homefeed_delay_init_fragment_ab 的 case 15）就是 `IndexPagerAdapterV2.r = 4
     *  + notifyDataSetChanged()` 两行——帧循环饿死 IdleHandler 时该任务滞留，count 长期
     *  =1，一切越界 setCurrentItem 被钳位吞掉。此处字节级重放该任务，把增长提前到首次
     *  点击时刻（应用滞留的 case 15 将来冲刷时幂等：r 已是 4、重复 notify 仅重 populate）。 */
    private fun growExplorePagerAdapter(adapter: Any): Boolean = runCatching {
        val r = cachedDeclaredField(adapter.javaClass, PAGER_COUNT_FIELD) ?: return false
        if (r.getInt(adapter) < 4) r.setInt(adapter, 4)
        cachedMethod(adapter.javaClass, "notifyDataSetChanged")?.invoke(adapter) ?: return false
        true
    }.onFailure {
        hookFacade?.log(android.util.Log.WARN, "xhs pager grow failed", it)
    }.getOrDefault(false)

    /**
     * 外层 pager 预增长（v2.5，装配后 800ms 首试）：与应用增长任务字节等价的
     * [growExplorePagerAdapter]（r=4 + notifyDataSetChanged，9.52.1 ntc.f.invoke 增长
     * case 实证）提前到装配期执行，恢复原生「启动后首空闲增长、页面预实例化」时序——
     * 消除首次点击的现场实例化延迟。幂等（count ≥ 4 直接跳过，应用滞留的增长任务将来
     * 冲刷时同样无操作）；adapter 未就绪（pager 尚未 setAdapter / 反射失配）时按
     * PRE_GROW_RETRY_DELAYS 有界重试；用户在预增长前点击则回退切页重放内的增长路径
     * （不劣于 v2.0 行为）。
     */
    private fun preGrowExplorePager(facade: HookFacade, attempt: Int) {
        runCatching {
            val pager = explorePager() ?: return schedulePreGrowRetry(facade, attempt)
            val count = pagerAdapterCount(pager)
            if (count >= PAGER_FULL_COUNT) return
            val adapter = cachedMethod(pager.javaClass, "getAdapter")?.invoke(pager)
            if (adapter != null && growExplorePagerAdapter(adapter)) {
                facade.log(android.util.Log.INFO, "xhs pager pre-grown (count=$count)")
                return
            }
        }.onFailure { facade.log(android.util.Log.WARN, "xhs pager pre-grow failed", it) }
        schedulePreGrowRetry(facade, attempt)
    }

    /** 预增长有界重试：就绪/成功后不再排。 */
    private fun schedulePreGrowRetry(facade: HookFacade, attempt: Int) {
        if (attempt < PRE_GROW_RETRY_DELAYS.size) {
            sweepHandler.postDelayed({ preGrowExplorePager(facade, attempt + 1) }, PRE_GROW_RETRY_DELAYS[attempt])
        }
    }

    // —— 反射解析缓存（v2.1）：混淆名钉扎的方法/字段进程内只解析一次——此前每次点击/
    //    每次应用侧 i2 调用做 ~6 次 getMethod/getDeclaredField 查找（每次分配 + 异常路径
    //    开销）。主线程专用（hook 回调与 ticker 全在主线程），HashMap 无需并发容器；
    //    null = 已确认失配，缓存后不再重试（失败日志也只打一次）。 ——

    private val resolvedMethods = HashMap<String, java.lang.reflect.Method?>()
    private val resolvedFields = HashMap<String, java.lang.reflect.Field?>()

    /** 基本类型参数的 Class（javaPrimitiveType 对基本类型恒非空，预解析省去散播可空数组）。 */
    private val PRIM_INT = Int::class.javaPrimitiveType!!
    private val PRIM_BOOL = Boolean::class.javaPrimitiveType!!

    private fun cachedMethod(
        cls: Class<*>,
        name: String,
        vararg params: Class<*>,
    ): java.lang.reflect.Method? {
        val key = "${cls.name}#$name#${params.size}"
        if (resolvedMethods.containsKey(key)) return resolvedMethods[key]
        val method = runCatching { cls.getMethod(name, *params) }
            .onFailure { hookFacade?.log(android.util.Log.WARN, "xhs resolve failed: $name", it) }
            .getOrNull()
        resolvedMethods[key] = method
        return method
    }

    private fun cachedDeclaredField(cls: Class<*>, name: String): java.lang.reflect.Field? {
        val key = "${cls.name}#$name"
        if (resolvedFields.containsKey(key)) return resolvedFields[key]
        val field = runCatching { cls.getDeclaredField(name) }
            .onFailure { hookFacade?.log(android.util.Log.WARN, "xhs resolve field failed: $name", it) }
            .getOrNull()
            ?.apply { isAccessible = true }
        resolvedFields[key] = field
        return field
    }

    /** 私有方法解析缓存（getDeclaredMethod + setAccessible；如 TabBarView.getIndexXxx
     *  getter）。key 带 #D 后缀与 getMethod 缓存区分。 */
    private fun cachedDeclaredMethod(cls: Class<*>, name: String): java.lang.reflect.Method? {
        val key = "${cls.name}#$name#D"
        if (resolvedMethods.containsKey(key)) return resolvedMethods[key]
        val method = runCatching { cls.getDeclaredMethod(name).apply { isAccessible = true } }
            .onFailure { hookFacade?.log(android.util.Log.WARN, "xhs resolve declared failed: $name", it) }
            .getOrNull()
        resolvedMethods[key] = method
        return method
    }

    /** 内容根 pager 缓存（v2.1）：点击派发与储备重扫共用，避免每次点击/每次选中变化做
     *  全 decor DFS；detached（应用重建视图树）时自动失效回退全树查找。 */
    @Volatile
    private var explorePagerRef: java.lang.ref.WeakReference<View>? = null

    private fun explorePager(): View? {
        explorePagerRef?.get()?.let { pager ->
            if (pager.isAttachedToWindow) return pager
            explorePagerRef = null
        }
        val found = hostActivity?.let { findExplorePager(it) } ?: return null
        explorePagerRef = java.lang.ref.WeakReference(found)
        return found
    }

    private data class ListPaddingState(val bottom: Int, val clip: Boolean, var applied: Int)

    /** 消息/我的页被动态加过底部余量的列表（弱引用，离开页面还原）。 */
    private val paddedLists = java.util.WeakHashMap<View, ListPaddingState>()

    /** 页面容器导航衬垫原值（v2.6）：应用从 ROOT insets 直写 paddingBottom/bottomMargin
     *  （rootWindowInsets 通路，派发 insets 清零无效），离开页面按此还原；弱引用随视图
     *  丢弃。 */
    private data class ViewportFitState(val paddingBottom: Int, val bottomMargin: Int)

    /** 已归零衬垫的页面容器（弱引用，离开页面还原）。 */
    private val viewportFits = java.util.WeakHashMap<View, ViewportFitState>()

    /** viewport 衬垫锚点失配告警只发一次（№03.7 教训 11 口径：降级路径必须有日志，
     *  但不过量刷屏）；命中锚点后复位。 */
    private var viewportAnchorWarned = false

    /** 布局变化重申（v2.6.1，v2.6.2 拆廉价快路径）：我的页症状是"上滑一段距离（头部
     *  塌陷）后才出现"——塌陷时刻应用才写入导航衬垫，滚动期间事件驱动 pass 拦不住。
     *  decor OnGlobalLayout 在任何布局变化（含 setPadding 的 requestLayout）时回调：
     *  - 廉价路径（默认）：只重零已知衬垫根 + 已知列表余量重算（O(k)，无 DFS）；配合
     *    单调余量（只增不减），滚动稳态零写入零重布局
     *  - 全量 pass：当前槽位尚未发现衬垫（≤60ms 追频，页面装配期）或 1.5s 兜底
     *  自收敛：我们清零触发的回调里值已为 0、无写入不再布局。槽位离开即摘除。
     */
    private val layoutReassert = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            reassertTick()
        }
    }

    /** 全量 pass 节拍基准（事件驱动 pass 与 reassert 共用）。 */
    @Volatile
    private var lastFullPassUptime = 0L

    /** 当前 reassert 所在槽位与"该槽位是否已发现过衬垫根"（发现后 reassert 走廉价路径）。 */
    @Volatile
    private var lastPassSlot = -1

    @Volatile
    private var slotFitFound = false

    @Volatile
    private var reassertAttachedTo: java.lang.ref.WeakReference<View>? = null

    private fun ensureLayoutReassert(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        if (reassertAttachedTo?.get() === decor) return
        detachLayoutReassert()
        decor.viewTreeObserver.addOnGlobalLayoutListener(layoutReassert)
        reassertAttachedTo = java.lang.ref.WeakReference(decor)
    }

    private fun detachLayoutReassert() {
        val decor = reassertAttachedTo?.get() ?: return
        runCatching { decor.viewTreeObserver.removeOnGlobalLayoutListener(layoutReassert) }
        reassertAttachedTo = null
    }

    /**
     * reassert 回调主体：廉价路径重零已知根 + 已知列表余量重算；全量 pass 仅在当前槽位
     * 尚未发现衬垫（页面装配期 60ms 追频——发现 pass 的节流窗口内漏出的应用装配期衬垫
     * 写入在此补清，首开小白条区空白收敛到不可感知）或 1.5s 兜底时执行。全部自捕获
     * （布局回调异常会上抛宿主）。
     */
    private fun reassertTick() {
        val session = activeSession ?: return
        runCatching {
            val slot = session.selectedTabIndex()
            // v3.0 发布键退出槽位后 4 槽：消息 = 槽位 2、我的 = 槽位 3
            if (slot != 2 && slot != 3) return
            val now = android.os.SystemClock.uptimeMillis()
            val needFull = (slotFitFound.not() && now - lastFullPassUptime >= 60L) ||
                now - lastFullPassUptime >= 1500L
            if (needFull) {
                applyListPaddingPass()
            } else {
                rezeroKnownFits()
                recomputeKnownLists()
            }
        }
    }

    /** 已知衬垫根重零（O(k)）：应用对已发现根的写回（塌陷期/装配期）在此当帧清零。 */
    private fun rezeroKnownFits() {
        for ((root, _) in viewportFits) {
            val group = root as? ViewGroup
            if (root.paddingBottom != 0) {
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, 0)
            }
            val params = group?.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.bottomMargin != 0) {
                params.bottomMargin = 0
                group.layoutParams = params
            }
        }
    }

    /** 已知列表余量重算（O(k)）：几何随头部塌陷变化时按单调口径补写（只增不减）。 */
    private fun recomputeKnownLists() {
        val session = activeSession ?: return
        val bar = session.tabContainer
        val barLoc = IntArray(2).also(bar::getLocationInWindow)
        val gap = (12f * bar.resources.displayMetrics.density).toInt()
        for ((list, state) in paddedLists) {
            if (!list.isShown) continue
            applyListClearance(list, state, barLoc, gap)
        }
    }

    /** 单调余量落笔（v2.6.2）：wanted = max(原值, 当前 clearance, 已应用值)——头部塌陷使
     *  clearance 收缩时不再回写小值，滚动稳态零改写（v2.6.1 每拍收缩改写 = 每次触发
     *  RecyclerView 整体重布局，真机滚动卡顿根因）。激活期内 padding 单调增；离开页面
     *  restore 还原原值，重进重新计。 */
    private fun applyListClearance(
        view: View,
        state: ListPaddingState,
        barLoc: IntArray,
        gap: Int,
    ) {
        val listLoc = IntArray(2).also(view::getLocationInWindow)
        val clearance = (listLoc[1] + view.height - barLoc[1] + gap).coerceAtLeast(0)
        val wanted = maxOf(state.bottom, clearance, state.applied)
        if (view.paddingBottom != wanted) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, wanted)
        }
        state.applied = wanted
        (view as? ViewGroup)?.let { group ->
            if (group.clipToPadding) group.clipToPadding = false
        }
    }

    override val descriptor: TabBarDescriptor = TabBarDescriptor(
        containerClassName = BAR_CLASS,
        tabViewClassName = ITEM_CLASS,
        iconMode = IconMode.WHOLE_TAB_FULL,
        fallbackProbe = true,
        // TabView 重写 View.setSelected(boolean)：公有单布尔参，引擎反射通道直接可用，
        // 显式声明留档（默认名 setChecked 在小红书上不存在）
        itemCheckedSetterMethodName = "setSelected",
        // 条目自带选中态外观（state_selected 图标选择器 + 文字 ColorStateList），不整层染色
        tintRefractionContent = false,
        // 点击派发接管：应用切页入口（9.52.1 ntc.p.C2 / 9.50.0 n4d.n.i2）把切页动作挂在
        // MessageQueue.IdleHandler（f4c.f.r0 / oec.f.S0），玻璃帧循环使主队列整场不空闲 →
        // 切页滞留（v1.4 真机 35 连发实证）。v1.8 经切页入口全管线派发 + after hook 同步
        // 重放延迟体（见 dispatchTabClick / replayShowPage）
        tabClickHandler = { index, tabViews, container ->
            dispatchTabClick(index, tabViews, container)
        },
        // 发布键 → 玻璃栏右侧独立圆形**着色**玻璃按钮（extraActionButton 通用通道，小黑盒
        // №15 同款形态；发布键不再进镜像槽位——原生视图随整栏 suppressNatively alpha=0
        // 隐藏，无需单独压制）。accent = 加号底色（原图标 drawable 直方图众数，r/cg5.xml
        // 实证 #FF2442 红）+ icon = 自绘白色圆头十字加号（v3.0.2 用户定案：不使用原图标
        // 字形——提取真机复验红底残留 + 位图几何偏心，自绘天然居中且免疫运行时 drawable
        // 形态未知，见 plusIcon / decodePostAccent）；点击 = getIndexPost getter 穿透
        // （performClick 不检查 isClickable，引擎 clickable 树禁用不影响，v2.2 槽位 2
        // 即此路径）
        extraActionButton = ExtraActionButtonSpec(
            icon = { publishIconDrawable() },
            accent = { publishAccentColor() },
            onClick = { dispatchPostClick() },
        ),
    )

    override fun shouldProbe(activity: Activity): Boolean =
        activity.javaClass.name == tabActivityClassName

    override fun findTabBar(content: ViewGroup): View? {
        // 资源 id 全混淆、无资源名锚点：按类名两轮遍历（主页版优先，商城版兜底），
        // 要求容器 >= 2 个子项（实际结构 = 5 槽位 + 装饰条，远大于 2）
        for (barClassName in arrayOf(BAR_CLASS, MALL_BAR_CLASS)) {
            val found = ArrayList<View>(1)
            fun walk(v: View?) {
                if (v == null || found.isNotEmpty()) return
                if (v.javaClass.name == barClassName && v is ViewGroup && v.childCount >= 2) {
                    found.add(v)
                    return
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(content)
            if (found.isNotEmpty()) return found.first()
        }
        return null
    }

    override fun tabViewsOf(bar: View): List<View> {
        val items = ArrayList<View>()
        if (bar !is ViewGroup) return items
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i) ?: continue
            // 只收 TabView 条目（4 个页签）；第 0 子项是纯 ImageView 装饰条（顶部分割条），
            // 发布中键是 RelativeLayout——两者都不进玻璃槽位（发布键由 descriptor
            // extraActionButton 玻璃栏右侧圆形按钮承担，见 onSessionCreated 捕获）
            if (isTabItem(child)) items.add(child)
        }
        // 条目类锚点失配（应用更新改结构）时返回空列表，探测按未就绪重试，不误装
        if (items.count { isTabItem(it) } < 2) return emptyList()
        return items
    }

    private fun isTabItem(v: View): Boolean =
        v.javaClass.name == ITEM_CLASS || v.javaClass.name == MALL_ITEM_CLASS

    override fun onSessionCreated(
        session: GlassSession,
        activity: Activity,
        facade: HookFacade,
        config: () -> ModuleConfig,
    ) {
        // 显隐门控：沉浸场景若应用对底栏做平移下滑 + visibility 变化，玻璃按容器
        // isShown + translationY 跟随（微博同款；平移过半即藏，避免玻璃滞留在已滑走内容上）
        session.mirrorVisibleGate = {
            val bar = session.tabContainer
            bar.isShown && bar.translationY < bar.height * 0.5f
        }
        // 背景采样去重（v2.1，高德/相册同款配置，模块配置 lightweightSampling 可远程关）：
        // 默认实现下 vibrancy/折射副本/液泡三个玻璃节点每帧各自对 appRoot 全树 draw——
        // 信息流是重内容页，3×全树绘制/帧是滚动卡顿主源；开启后每帧录制一次 RenderNode、
        // 三节点回放同一录制（3→1）。
        session.dedupeBackdropSampling = config().lightweightSampling
        // 取证：记录底栏在窗口中的父链。资源 id/布局名全混淆，静态分析无法定位挂载者，
        // 运行期父链是应用更新后底栏失配排障的第一现场
        runCatching {
            val chain = buildString {
                var p = session.tabContainer.parent as? View
                while (p != null) {
                    append(" <- ").append(p.javaClass.name)
                    p = p.parent as? View
                }
            }
            facade.log(android.util.Log.INFO, "xhs bar parent chain:$chain")
        }
        // 内容延伸：信息流全屏（储备归零，装配时一次 + 选中态变化防抖重扫，见类 KDoc）
        zeroContentReserve(activity, facade)
        // 条目顶部设计留白中和：镜像垂直居中（见类 KDoc）
        neutralizeItemTopInset(session, facade)
        // 发布键捕获（extraActionButton 数据源：图标逐帧解析 + 点击回退引用，见
        // capturePostKey / postIconDrawable / dispatchPostClick）
        capturePostKey(session, facade)
        // 导航栏 inset 清零：全页全出血（见 installContentInsetsZeroing）
        installContentInsetsZeroing(activity)
        hostActivity = activity
        activeSession = session
        hookFacade = facade
        // 外层 pager 预增长（v2.5）：原生应用在启动后首个空闲时刻完成 count 1→4 增长
        // （页面预实例化），帧循环饿死 IdleHandler 使该时序消失——v2.0 起首击时才重放
        // 增长 = FragmentPagerAdapter 在点击瞬间现场实例化目标页（真机 ~0.5s）。装配后
        // 主动预增长恢复原生时序，消除首击延迟（见 preGrowExplorePager）
        sweepHandler.postDelayed({ preGrowExplorePager(facade, 0) }, 800L)
    }

    override fun installAppHooks(facade: HookFacade, config: () -> ModuleConfig, loader: ClassLoader) {
        hookFacade = facade
        // 储备重扫触发：应用切页 = vgd.d0.J0 → setXxxSelected → TabView.setSelected。
        // 外层 pager 冷启动 count=1→4 异步、offscreenPageLimit(1)（消息/我的页晚挂载），
        // ContentView 重建也可能把 45dp 储备带回来——选中变化时防抖重扫（幂等，按 pager
        // 类名全 decor 匹配）。TabView.setSelected 同时是拖动预览的翻转通道，预览连翻也
        // 会触发——重扫幂等且 300ms 防抖，开销可忽略。
        runCatching {
            val itemClass = Class.forName(ITEM_CLASS, false, loader)
            val setSelected = itemClass.getMethod("setSelected", PRIM_BOOL)
            facade.around(setSelected, null) { call ->
                if (call.args.firstOrNull() == true) {
                    scheduleReserveSweep(facade)
                    scheduleListPass()
                }
                null
            }
            facade.log(android.util.Log.INFO, "hooked Xhs TabView.setSelected reserve re-sweep")
        }.onFailure {
            facade.log(android.util.Log.WARN, "hook Xhs TabView.setSelected failed (reserve re-sweep degraded)", it)
        }
        // 切页同步重放：应用切页延迟体挂在 IdleHandler（9.50.0 oec.f.S0 / 9.52.1 f4c.f.r0），
        // 玻璃帧循环使主队列整场不空闲 → 前台永不执行（v1.4 真机 35 连发滞留实证）。在切页
        // 入口（9.52.1 ntc.p.C2 / 9.50.0 n4d.n.i2，候选链新名优先）同步段（状态写入/事件/
        // 序号自增）完成后立即重放延迟体等价动作，见 replayShowPage。after 回调异常会上抛
        // 宿主，重放内部必须全程自捕获。（v2.3 起共 2 个行为 hook：本 hook +
        // TabView.setSelected 视觉触发；n4d.n 构造捕获已删——穿透式点击不主动调切页入口，
        // controller 实例来自被拦截调用的 thisObj，无冷启动捕获需求；u4c.d0$a /
        // pager.setCurrentItem 取证与 PagerAdapter.notifyDataSetChanged 兜底此前已删，
        // 排障需要时按 №06 手册恢复。）
        runCatching {
            var hooked = false
            for ((controllerClass, showPageMethod) in SHOW_PAGE_CANDIDATES) {
                val cls = runCatching { Class.forName(controllerClass, false, loader) }
                    .getOrNull() ?: continue
                val showPage = cachedMethod(cls, showPageMethod, PRIM_INT) ?: continue
                facade.around(showPage, null) { call ->
                    val controller = call.thisObj
                    val page = (call.args.firstOrNull() as? Number)?.toInt()
                    if (controller != null && page != null) {
                        facade.log(android.util.Log.INFO, "xhs i2 entered: page=$page")
                        replayShowPage(controller, page, facade)
                    }
                    null
                }
                hooked = true
                facade.log(
                    android.util.Log.INFO,
                    "hooked Xhs showPage replay ($controllerClass.$showPageMethod)",
                )
                break
            }
            if (!hooked) error("no controller candidate resolved")
        }.onFailure {
            facade.log(
                android.util.Log.WARN,
                "hook Xhs showPage failed (controller renamed? direct-drive fallback)",
                it,
            )
        }
        // 延迟体守卫（写入点拦截，v2.4）：切页延迟体挂 IdleHandler 被帧循环饿死，空闲让步
        // 窗口冲刷时若守卫失效会重新执行 = 跳回陈旧页（时序：冷启动 count=1 首次点击走
        // IdleHandler 路径留延迟体，重放当场切页；后续点击 count 已就绪走 post 快速路径
        // 不留延迟体 → 冲刷时恰好只剩首切延迟体 → 跳回首次切换的 tab）。应用守卫 =
        // `captured != seq && 配置位`，第二条件是商城 tab 开关（两版同源），配置 false 即
        // 整体失效——这里按序号陈旧性自行吞咽：我们的切页重放已同步执行过同一切页动作并
        // 自增序号，延迟体对玻璃点击恒为冗余；captured == 当前序号（重放失败未自增）时
        // 放行，走应用原生兜底（冲刷时队列空闲，延迟体可正常执行）。
        runCatching {
            var hooked = false
            for ((bodyClass, capturedField, controllerField) in DEFERRED_BODY_CANDIDATES) {
                val cls = runCatching { Class.forName(bodyClass, false, loader) }
                    .getOrNull() ?: continue
                val invoke = cachedMethod(cls, DEFERRED_INVOKE, Any::class.java) ?: continue
                facade.around(
                    invoke,
                    before = { call ->
                        swallowStaleDeferredBody(call.thisObj, capturedField, controllerField)
                    },
                    after = null,
                )
                hooked = true
                facade.log(
                    android.util.Log.INFO,
                    "hooked Xhs deferred body guard ($bodyClass.$DEFERRED_INVOKE)",
                )
                break
            }
            if (!hooked) error("no deferred body candidate resolved")
        }.onFailure {
            facade.log(
                android.util.Log.WARN,
                "hook Xhs deferred body guard failed (native guard only, jump-back possible)",
                it,
            )
        }
    }

    /** 选中态变化触发的储备重扫（300ms 防抖，幂等）。 */
    private fun scheduleReserveSweep(facade: HookFacade) {
        if (!sweepScheduled.compareAndSet(false, true)) return
        sweepHandler.postDelayed({
            sweepScheduled.set(false)
            zeroContentReserve(hostActivity, facade)
        }, 300)
    }

    /**
     * 切页派发（v2.2，LiquidTab XhsNavigationState.performTap 同款）：玻璃点击 → 按槽位
     * 查 SLOT_GETTER_INDICES 调 `TabBarView.getIndexXxx()` 私有 getter（真实方法名，非
     * 混淆类上的稳定锚点）拿真实条目视图 → 穿透式 performClick 走应用完整原生管线
     * （throttle/Rx/事件）→ 切页入口 after hook 同步重放挂 IdleHandler 的延迟体
     * （growth + setCurrentItem + 懒加载激活，见 replayShowPage）。与 LiquidTab 的唯一
     * 架构差异：它无帧循环、IdleHandler 自然执行，重放 hook 不需要；我们帧循环饿死
     * IdleHandler，重放是卡点修复器。v3.0 起玻璃栏 4 槽（发布键退出槽位，见
     * dispatchPostClick），槽位即页序恒等映射。回退链：getter 失配 → 直驱降级
     * （explorePager + 自增长 + pending，懒加载缺失可能空白，不劣于 v1.7）→ 原穿透式
     * 点击（tabViews 槽位视图沿父链）。
     */
    private fun dispatchTabClick(index: Int, tabViews: List<View>, container: View) {
        val tabBarGetter = SLOT_GETTER_INDICES.getOrNull(index)?.let { TAB_GETTERS[it] }
        if (tabBarGetter != null) {
            val target = cachedDeclaredMethod(container.javaClass, tabBarGetter)?.let { getter ->
                runCatching { getter.invoke(container) as? View }.getOrNull()
            }
            if (target != null && performClickThroughTab(target, container)) {
                hookFacade?.log(android.util.Log.INFO, "xhs tap via native pipeline (slot=$index)")
                return
            }
        }
        // 降级 1：getter 失配/调用异常 → 直驱 pager（自增长已就绪即秒切；懒加载激活
        // 缺失，页面可能空白至下一次全管线切页），不劣于 v1.7。发布键退出槽位后
        // 4 槽 = 4 页恒等映射。
        val page = index
        val pager = explorePager()
        if (pager != null && switchPagerPage(pager, page)) {
            hookFacade?.log(android.util.Log.INFO, "xhs tab switch via direct fallback (page=$page)")
            return
        }
        // 降级 2：原穿透式点击（tabViews 槽位视图沿父链向上点到 tabContainer）。
        hookFacade?.log(android.util.Log.INFO, "xhs tab switch via click-through (slot=$index)")
        val target = tabViews.getOrNull(index) ?: return
        performClickThroughTab(target, container)
    }

    /**
     * 切页入口 after 同步重放（v1.8 核心修复，v2.3 双版候选链）：应用把切页动作挂
     * MessageQueue.IdleHandler（9.50.0 oec.f.S0 / 9.52.1 f4c.f.r0），玻璃帧循环使主队列
     * 整场不空闲 → 延迟体前台永不执行 = 点击滞留（v1.4 真机：35 次点击滞留至 activity
     * pause 集中爆发）。本重放在切页入口同步段（状态写入/事件/序号自增）完成后立即执行
     * 延迟体等价动作（9.50.0 n4d.l / 9.52.1 ntc.l 字节级对齐）：
     * - 全部页（含 page 0）→ `presenter.pager().setCurrentItem(page, false)`——9.50.0 的
     *   page 0 经 y0()，其方法体就是 setCurrentItem(0,false)（字节等价）；9.52.1 延迟体
     *   page 0 直接 setCurrentItem。回顶刷新语义两版都由应用"点击已选中 tab"管线自管，
     *   不在延迟体内；
     * - count 未就绪先字节级重放增长任务（r=4 + notifyDataSetChanged；9.52.1 起应用另有
     *   按需口 o2() expandTabCount，与本重放同字节）；
     * - 懒加载激活（见 [applyLazyLoad]，setMaxLifecycle(RESUMED)——直驱跳过此步即
     *   "非首页空白"根因）；
     * - 自增序号字段（见 [bumpPageSeq]）：原延迟体的守卫判定已被取代而跳过，避免退后台
     *   IdleHandler 冲刷时二次执行；字段访问失败则跳过自增，接受良性的延迟重复执行
     *   （setCurrentItem 同页无操作、懒加载集合幂等）。
     * 全程 runCatching：ModernFacade 下 after 回调异常会上抛宿主，必须就地隔离。
     */
    private fun replayShowPage(controller: Any, page: Int, facade: HookFacade) {
        runCatching {
            val cls = controller.javaClass
            val presenter = cachedMethod(cls, PRESENTER_GETTER)?.invoke(controller)
            var applied = page == 0
            if (presenter != null) {
                val presenterCls = presenter.javaClass
                val pager = PAGER_GETTERS.firstNotNullOfOrNull { getter ->
                    cachedMethod(presenterCls, getter)?.invoke(presenter) as? View
                }
                if (pager != null) {
                    var count = pagerAdapterCount(pager)
                    if (count <= page) {
                        // 冷启动 count 未就绪：setCurrentItem 会越界钳位被静默丢弃。
                        // 先字节级重放应用自己的增长任务（r=4 + notifyDataSetChanged，
                        // 原本经 IdleHandler 排队被帧循环饿死），失败才挂起等增长信号
                        val adapter = cachedMethod(pager.javaClass, "getAdapter")?.invoke(pager)
                        if (adapter != null && growExplorePagerAdapter(adapter)) {
                            count = pagerAdapterCount(pager)
                        }
                    }
                    val setCurrentItem = if (count > page) {
                        cachedMethod(
                            pager.javaClass,
                            "setCurrentItem",
                            PRIM_INT,
                            PRIM_BOOL,
                        )
                    } else {
                        null
                    }
                    if (setCurrentItem != null) {
                        setCurrentItem.invoke(pager, page, false)
                        applied = true
                    } else {
                        // 增长失败（adapter 为 null / r 字段失配）：挂起等应用自身增长
                        // 信号（不钳位调用、不自增序号，放行滞留延迟体将来自行切页，幂等）
                        deferPendingPage(pager, controller, page, count, facade)
                        return
                    }
                }
            }
            if (applied) {
                applyLazyLoad(controller, page)
                bumpPageSeq(controller)
                facade.log(android.util.Log.INFO, "xhs i2 replay done: page=$page")
            }
        }.onFailure {
            facade.log(android.util.Log.WARN, "xhs i2 replay failed (page=$page)", it)
        }
    }

    /** 懒加载激活（重放延迟体第 2 步）：loader 经 Y1()（9.50.0）/字段 e（9.52.1）访问、
     *  激活方法按 loader 类名取（LAZY_METHODS_BY_LOADER）。任一环节失配静默跳过——页面
     *  可能空白至下一次全管线切页。 */
    private fun applyLazyLoad(controller: Any, page: Int) {
        val cls = controller.javaClass
        val loader = cachedMethod(cls, LAZY_ACCESS_METHOD)?.invoke(controller)
            ?: cachedDeclaredField(cls, LAZY_ACCESS_FIELD)?.get(controller)
            ?: return
        val method = LAZY_METHODS_BY_LOADER[loader.javaClass.name] ?: return
        cachedMethod(loader.javaClass, method, PRIM_INT)?.invoke(loader, page)
    }

    /** 序号自增（重放延迟体第 3 步）：字段按命中 controller 类名取
     *  （SEQ_FIELDS_BY_CONTROLLER）。字段失配跳过，接受良性的延迟重复执行。 */
    private fun bumpPageSeq(controller: Any) {
        val name = SEQ_FIELDS_BY_CONTROLLER[controller.javaClass.name] ?: return
        val field = cachedDeclaredField(controller.javaClass, name) ?: return
        field.setInt(controller, field.getInt(controller) + 1)
    }

    /**
     * 延迟体守卫判定（延迟体 invoke 的 before）：captured 序号 ≠ controller 当前序号
     * （= 我们的切页重放已把序号自增越过它，该延迟体陈旧且切页动作已同步执行过）→ 返回
     * 非 null 值让 before 短路跳过原方法（HookFacade 约定：非 null 非 Unit 即吞咽）；
     * 相等（重放未覆盖/失败，序号未自增）→ 返回 null 放行，走应用原生兜底。反射任一环节
     * 失配一律放行（fail-open = 原生行为），不产生新失效面。
     */
    private fun swallowStaleDeferredBody(
        body: Any?,
        capturedField: String,
        controllerField: String,
    ): Any? {
        if (body == null) return null
        val guarded = runCatching {
            val cls = body.javaClass
            val captured = cachedDeclaredField(cls, capturedField)?.getInt(body)
            val controller = cachedDeclaredField(cls, controllerField)?.get(body)
            val seqName = controller?.javaClass?.name?.let { SEQ_FIELDS_BY_CONTROLLER[it] }
            val seq = if (controller != null && seqName != null) {
                cachedDeclaredField(controller.javaClass, seqName)?.getInt(controller)
            } else {
                null
            }
            if (captured != null && seq != null && seq != captured) {
                hookFacade?.log(
                    android.util.Log.INFO,
                    "xhs deferred body swallowed (stale seq: captured=$captured current=$seq)",
                )
                Integer.valueOf(0)
            } else {
                null
            }
        }.getOrNull()
        return guarded
    }

    /** 挂起切页请求（后到覆盖先到），并以 500ms×20 兜底 ticker 复查 count。 */
    private fun deferPendingPage(
        pager: View,
        controller: Any?,
        page: Int,
        count: Int,
        facade: HookFacade,
    ) {
        val isNew = pendingPage < 0
        pendingPage = page
        pendingPagerRef = java.lang.ref.WeakReference(pager)
        pendingControllerRef = controller?.let { java.lang.ref.WeakReference(it) }
        facade.log(android.util.Log.INFO, "xhs i2 deferred: page=$page (count=$count)")
        if (isNew) {
            for (i in 1..20) {
                sweepHandler.postDelayed({ applyPendingPage(facade) }, i * 500L)
            }
        }
    }

    /** count 就绪后补做挂起的切页（兜底 ticker 专用，v2.1 起增长信号 hook 已删；幂等）。 */
    private fun applyPendingPage(facade: HookFacade) {
        val page = pendingPage
        if (page < 0) return
        val pager = pendingPagerRef?.get()
        if (pager == null) {
            pendingPage = -1
            pendingPagerRef = null
            pendingControllerRef = null
            return
        }
        val count = pagerAdapterCount(pager)
        if (count < 0 || count <= page) return
        runCatching {
            cachedMethod(
                pager.javaClass,
                "setCurrentItem",
                PRIM_INT,
                PRIM_BOOL,
            )?.invoke(pager, page, false)
            pendingControllerRef?.get()?.let { controller ->
                applyLazyLoad(controller, page)
            }
            facade.log(android.util.Log.INFO, "xhs pending page applied: page=$page (count=$count)")
        }.onFailure {
            facade.log(android.util.Log.WARN, "xhs pending page apply failed (page=$page)", it)
        }
        pendingPage = -1
        pendingPagerRef = null
        pendingControllerRef = null
    }

    private fun findExplorePager(activity: Activity): View? {
        val decor = activity.window?.decorView as? ViewGroup ?: return null
        fun walk(v: View?): View? {
            if (v == null) return null
            if (v.javaClass.name == EXPLORE_PAGER_CLASS) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))?.let { return it }
            return null
        }
        return walk(decor)
    }

    /** 直驱 pager 切页（v1.8 降级通道：仅 controller 未捕获/派发失败时使用）。v1.9 起带
     *  页数检查：`setCurrentItemInternal` 对 `item >= count` 钳位到 count-1 并静默丢弃
     *  （不存储请求，v1.7"mCurItem 记忆补切"前提证伪）。v2.0 起先尝试自增长（与应用
     *  case 15 任务同字节），失败才挂起（与 i2 重放同一 pending 通道）。反射异常返回
     *  false 走穿透式回退。 */
    private fun switchPagerPage(pager: View, page: Int): Boolean = runCatching {
        var count = pagerAdapterCount(pager)
        if (count <= page) {
            val adapter = cachedMethod(pager.javaClass, "getAdapter")?.invoke(pager)
            if (adapter != null && growExplorePagerAdapter(adapter)) {
                count = pagerAdapterCount(pager)
            }
        }
        if (count > page) {
            cachedMethod(
                pager.javaClass,
                "setCurrentItem",
                PRIM_INT,
                PRIM_BOOL,
            )?.invoke(pager, page, false)
        } else {
            deferPendingPage(pager, null, page, count, hookFacade ?: return false)
        }
        true
    }.onFailure {
        hookFacade?.log(android.util.Log.WARN, "xhs direct pager switch failed", it)
    }.getOrDefault(false)

    /**
     * 穿透式点击派发（LiquidTab XhsNavigationState.performTap/performClickThroughTab 同款）：
     * 目标条目 performClick 返回 false（无监听器/监听器链被应用侧机制架空）时，沿父链
     * 向上点到 tabContainer 为止，再退回目标子树找 clickable 视图。
     */
    private fun performClickThroughTab(target: View, boundary: View): Boolean {
        var current: View? = target
        repeat(6) {
            val view = current ?: return@repeat
            if (view.isShown && view.performClick()) return true
            if (view === boundary) return@repeat
            current = view.parent as? View
        }
        val stack = ArrayDeque<Pair<View, Int>>()
        stack += target to 0
        while (stack.isNotEmpty()) {
            val (view, depth) = stack.removeLast()
            if (view !== target && view.isShown && view.isClickable && view.performClick()) return true
            if (view is ViewGroup && depth < 4) {
                for (i in 0 until view.childCount) stack += view.getChildAt(i) to (depth + 1)
            }
        }
        return false
    }

    /**
     * 导航栏 inset 清零（LiquidTab XhsAdapter.prepareContentForInstall 同款）：在 content
     * 上重写 insets 派发，把 navigationBars 底部置 0（状态栏/刘海/IME 不动）——页面自己
     * 想吃导航栏 inset 也拿不到，全页强制全出血（消息/我的页玻璃区有内容的前提）。仅装配
     * 时设置一次；视图树重建经 isStale 重装路径自然重设。
     *
     * v2.6 通路限定：本清零只改写**派发** insets；应用经 rootWindowInsets **直读**的
     * 通路（SystemBarsManager → 页面容器 paddingBottom 衬垫）不受影响，由列表余量 pass
     * 内嵌的 applyViewportFit 在视图层归零（两条通路必须分开处理，LiquidTab 定案）。
     */
    private fun installContentInsetsZeroing(activity: Activity) {
        runCatching {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            content.setOnApplyWindowInsetsListener { _, insets ->
                val type = android.view.WindowInsets.Type.navigationBars()
                val nav = insets.getInsets(type)
                android.view.WindowInsets.Builder(insets)
                    .setInsets(type, android.graphics.Insets.of(nav.left, nav.top, nav.right, 0))
                    .setInsetsIgnoringVisibility(type, android.graphics.Insets.of(nav.left, nav.top, nav.right, 0))
                    .build()
            }
            content.requestApplyInsets()
            hookFacade?.log(android.util.Log.INFO, "xhs content navigation insets zeroed")
        }.onFailure { hookFacade?.log(android.util.Log.WARN, "xhs content insets zeroing failed", it) }
    }

    /**
     * 消息(槽位2)/我的(槽位3)页列表余量（LiquidTab XhsNavigationState.onHostPreDraw 同款）：
     * 页面 RecyclerView 自身无底部储备，玻璃栏悬浮后列表末端的可滚动区不够——动态给
     * paddingBottom 加"列表底缘到栏顶 + 12dp"的余量并关 clipToPadding，滚动时内容可穿透
     * 绘制进玻璃区；离开页面还原原值。RecyclerView 判定沿类层次向上找 androidx/support
     * 实现（消息/我的页的列表都是其子类）；不下钻列表内部（行内嵌套轮播不处理）。
     * v2.6 起同一轮 DFS 内嵌页面容器导航衬垫归零（applyViewportFit，与余量 pass 共用
     * 切页触发/补扫节奏），并对锚点全程未命中打一次性 WARN（№03.7 教训 11 口径）。
     */
    private fun applyListPaddingPass() {
        val session = activeSession ?: return
        val activity = hostActivity ?: return
        val facade = hookFacade ?: return
        runCatching {
            val slot = session.selectedTabIndex()
            // v3.0 发布键退出槽位后 4 槽：消息 = 槽位 2、我的 = 槽位 3
            if (slot != 2 && slot != 3) {
                restoreListPadding(facade)
                restoreViewportFits(facade)
                detachLayoutReassert()
                lastPassSlot = -1
                slotFitFound = false
                return
            }
            if (slot != lastPassSlot) {
                lastPassSlot = slot
                slotFitFound = false
            }
            lastFullPassUptime = android.os.SystemClock.uptimeMillis()
            val bar = session.tabContainer
            val barLoc = IntArray(2).also(bar::getLocationInWindow)
            val gap = (12f * bar.resources.displayMetrics.density).toInt()
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val stack = ArrayDeque<View>()
            stack.add(decor)
            while (stack.isNotEmpty()) {
                val view = stack.removeLast()
                if (view === bar || !view.isShown) continue
                // 尺寸启发式先行（v2.1）：isRecyclerList 沿类层次逐级比对类名，decor 内
                // 每个可见视图都比对太贵——只有大视图才值得走类层次判定
                if (view.height > bar.height * 2 && view.width > bar.width / 2 &&
                    isRecyclerList(view) && !isViewPager2Scoped(view)
                ) {
                    val group = view as? ViewGroup
                    val state = paddedLists.getOrPut(view) {
                        ListPaddingState(view.paddingBottom, group?.clipToPadding ?: true, view.paddingBottom)
                    }
                    applyListClearance(view, state, barLoc, gap)
                    continue // 不下钻列表内部
                }
                // 页面容器导航衬垫归零（v2.6）：命中只记录+清零，照常下钻——页面根内部
                // 的真实列表仍由上方余量分支处理
                if (applyViewportFit(view, bar)) slotFitFound = true
                if (view is ViewGroup) for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
            // 锚点缺失一次性告警（教训 11：执行层在跑但逻辑未命中须可区分）；命中后复位，
            // 页面晚挂载场景的后续 pass 可重新评估
            if (viewportFits.isNotEmpty()) {
                viewportAnchorWarned = false
            } else if (!viewportAnchorWarned) {
                viewportAnchorWarned = true
                facade.log(
                    android.util.Log.WARN,
                    "xhs viewport fit: no anchor found yet (slot=$slot, late mount or anchor drifted?)",
                )
            }
            // 滚动期间衬垫写入的拦截通道（v2.6.1，见 layoutReassert 注释）
            ensureLayoutReassert(activity)
        }.onFailure { facade.log(android.util.Log.WARN, "xhs list padding pass failed", it) }
    }

    private fun restoreListPadding(facade: HookFacade) {
        if (paddedLists.isEmpty()) return
        runCatching {
            for ((list, state) in paddedLists) {
                if (list.paddingBottom == state.applied && list.paddingBottom != state.bottom) {
                    list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, state.bottom)
                }
                (list as? ViewGroup)?.clipToPadding = state.clip
            }
            paddedLists.clear()
        }.onFailure { facade.log(android.util.Log.WARN, "xhs list padding restore failed", it) }
    }

    /**
     * 页面容器导航衬垫归零（v2.6，LiquidTab XhsMessageViewport 定案迁移；v2.6.1 值驱动
     * 补位；v2.6.3 清理：移除槽位 4 ViewPager2 结构锚点——真机实证其 pb=mb=0 空命中，
     * 真衬垫承载者由值驱动覆盖）：应用 SystemBarsManager 从 ROOT navigation insets
     * （rootWindowInsets 直读）把小白条高度写入页面容器底部衬垫——content 层派发 insets
     * 清零（installContentInsetsZeroing）只改下发值、拦不住 root 直读通路，页面布局盒被
     * 衬垫整体抬离玻璃栏区 = 小白条区域无内容。命中两级：①类名锚点——消息页（玻璃槽位 2）
     * = MessagePageView 非混淆类名（真机实证 pb=54 即小白条 inset）；②值驱动——任何
     * 尺寸达标容器，底部 padding/margin ∈ [inset/2, inset] 即按衬垫处理（覆盖"上滑头部
     * 塌陷时才写入"的滚动期承载者，配合 layoutReassert 拦截）。发现即记录原值并归零，
     * 每次 pass 重申（应用写回再清），离开页面由 restoreViewportFits 还原。与列表余量
     * 分支无交集：RecyclerView 命中上方分支后 continue，不会走到本函数；ViewPager2 内部
     * RecyclerViewImpl 由 isViewPager2Scoped 排除余量误伤。
     */
    private fun applyViewportFit(view: View, bar: View): Boolean {
        val anchorHit = view.javaClass.name == MESSAGE_PAGE_CLASS
        val valueHit = !anchorHit && isNavFitByValue(view, bar)
        if (!anchorHit && !valueHit) return false
        return recordAndZeroFit(view)
    }

    /** 衬垫记录+归零：首次发现记原值打取证日志（logcat），随后清零 pb/mb。 */
    private fun recordAndZeroFit(view: View): Boolean {
        val group = view as? ViewGroup
        viewportFits.getOrPut(view) {
            val recorded = ViewportFitState(
                view.paddingBottom,
                group?.layoutParams.let { p -> (p as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0 },
            )
            hookFacade?.log(
                android.util.Log.INFO,
                "xhs viewport fit cleared: ${view.javaClass.name} " +
                    "pb=${recorded.paddingBottom} mb=${recorded.bottomMargin}",
            )
            recorded
        }
        if (view.paddingBottom != 0) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
        }
        if (group != null) {
            val params = group.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.bottomMargin != 0) {
                params.bottomMargin = 0
                group.layoutParams = params
            }
        }
        return true
    }

    /**
     * 值驱动衬垫判定：底部 paddingBottom/bottomMargin ∈ [inset/2, inset] 且视图尺寸
     * 达标（同列表守卫），即视为导航衬垫承载者。v2.6.2 区间收紧（原 (0, inset] 误伤
     * `UserAvatarCardView mb=6` 这类小余量，真机 trace 实证）。排除 paddedLists 在册
     * 视图——那里是我们自己写的余量值（clearance），不是应用衬垫。inset 上限 300px
     * 防串扰（<30 API 的 systemWindowInsetBottom 会混入 IME 高度，混入即超限自动禁用
     * 本判定，fail-open）。
     */
    private fun isNavFitByValue(view: View, bar: View): Boolean {
        if (view.height <= bar.height * 2 || view.width <= bar.width / 2) return false
        if (paddedLists.containsKey(view)) return false
        val inset = navInsetOf(view) ?: return false
        val group = view as? ViewGroup
        val mb = (group?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        return view.paddingBottom in (inset / 2)..inset || mb in (inset / 2)..inset
    }

    /** 导航条 inset（rootWindowInsets 直读——与应用写入衬垫的取值同源）；无值或超 300px
     *  （IME 混入等）返回 null 禁用值驱动。 */
    private fun navInsetOf(view: View): Int? = runCatching {
        val insets = view.rootWindowInsets ?: return null
        val bottom = if (android.os.Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        bottom.takeIf { it in 1..300 }
    }.getOrNull()

    /** 离开消息/我的页还原衬垫原值（与 restoreListPadding 同节奏，槽位离开分支调用）。 */
    private fun restoreViewportFits(facade: HookFacade) {
        if (viewportFits.isEmpty()) return
        runCatching {
            for ((root, state) in viewportFits) {
                if (root.paddingBottom == 0 && state.paddingBottom != 0) {
                    root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, state.paddingBottom)
                }
                (root as? ViewGroup)?.let { group ->
                    val params = group.layoutParams as? ViewGroup.MarginLayoutParams
                    if (params != null && params.bottomMargin == 0 && state.bottomMargin != 0) {
                        params.bottomMargin = state.bottomMargin
                        group.layoutParams = params
                    }
                }
            }
            viewportFits.clear()
        }.onFailure { facade.log(android.util.Log.WARN, "xhs viewport fit restore failed", it) }
    }

    private fun isRecyclerList(view: View): Boolean {
        var type: Class<*>? = view.javaClass
        while (type != null) {
            if (type.name == "androidx.recyclerview.widget.RecyclerView" ||
                type.name == "android.support.v7.widget.RecyclerView"
            ) return true
            type = type.superclass
        }
        return false
    }

    /**
     * ViewPager2 作用域判定（列表余量排除）：「我的页」内容区是 ViewPager2（继承
     * ViewGroup，内部藏一个撑满自身的 RecyclerView 作页面容器）——对该内部容器加
     * paddingBottom 会把整页内容抬离玻璃栏区（RecyclerView 子项在 padding 盒内布局，
     * clipToPadding 只影响裁剪不影响布局盒）。命中 = 自身沿类层次是 ViewPager2，或直接
     * parent 是 ViewPager2（覆盖其内部容器）；不命中照常下钻，页内真实列表仍会被正常
     * 加余量。androidx 老 ViewPager（ExploreScrollableViewPager）非 RecyclerView，
     * 天然不进本判定。
     */
    private fun isViewPager2Scoped(view: View): Boolean =
        isViewPager2Type(view.javaClass) || isViewPager2Type(view.parent?.javaClass)

    private fun isViewPager2Type(type: Class<*>?): Boolean {
        var t = type
        while (t != null) {
            if (t.name == VIEW_PAGER_2_CLASS) return true
            t = t.superclass
        }
        return false
    }

    /** 列表余量补扫：切页后立即（post 合并）+ 0.8/1.5s 各一次（列表晚挂载；LiquidTab 以
     *  250ms 周期 pre-draw 扫描，这里保持事件驱动口径——只在新挂载窗口内补扫，静止零
     *  开销）。立即轮 post 到下一轮消息循环：同帧多次触发合并为一轮 DFS。 */
    private fun scheduleListPass() {
        if (listPassImmediateScheduled.compareAndSet(false, true)) {
            sweepHandler.post {
                listPassImmediateScheduled.set(false)
                applyListPaddingPass()
            }
        }
        if (listPassBackstopScheduled.compareAndSet(false, true)) {
            sweepHandler.postDelayed({
                listPassBackstopScheduled.set(false)
                applyListPaddingPass()
            }, 800L)
            sweepHandler.postDelayed({ applyListPaddingPass() }, 1500L)
        }
    }

    /**
     * 发布键捕获（v3.0，extraActionButton 数据源）：发布键已退出玻璃槽位（tabViewsOf
     * 只收 TabView），按原生 5 槽位结构从容器直接子项捕获——非 ImageView 装饰条且非
     * TabView 条目 = 发布键 RelativeLayout。捕获失败打一次性 WARN（№03.7 教训 11 口径：
     * 执行层在跑但逻辑未命中须可区分），成功打 INFO 留结构现场。
     */
    private fun capturePostKey(session: GlassSession, facade: HookFacade) {
        runCatching {
            val bar = session.tabContainer as? ViewGroup
            var post: ViewGroup? = null
            if (bar != null) {
                for (i in 0 until bar.childCount) {
                    val child = bar.getChildAt(i)
                    if (child is ImageView || isTabItem(child)) continue
                    post = child as? ViewGroup
                    break
                }
            }
            val key = post
            if (key == null) {
                if (!publishKeyWarned) {
                    publishKeyWarned = true
                    facade.log(android.util.Log.WARN, "xhs publish key missed (structure changed?)")
                }
                return
            }
            publishKeyWarned = false
            postKeyRef = java.lang.ref.WeakReference(key)
            facade.log(android.util.Log.INFO, "xhs publish key captured: ${key.javaClass.name}")
        }.onFailure { facade.log(android.util.Log.WARN, "xhs capture publish key failed", it) }
    }

    /** 发布键锚点未命中告警只发一次（capturePostKey 专用，成功命中后复位）。 */
    @Volatile
    private var publishKeyWarned = false

    /**
     * 每帧调用（extraActionButton.icon）：**自绘加号**（v3.0.2 用户定案：不使用原图标
     * 字形——v3.0.1 字形提取真机复验红底残留且位图几何偏心，提取路线整体撤除）——白色
     * 圆头十字按宿主密度绘制成物理像素位图，ExtraIconPainter 按位图 intrinsic 1:1
     * **居中**呈现。null 仅在发布键未捕获时出现。
     */
    private fun publishIconDrawable(): android.graphics.drawable.Drawable? {
        val group = postKeyRef?.get() ?: return null
        return plusIcon(group.resources)
    }

    /**
     * 每帧调用（extraActionButton.accent）：强调色 = 加号底色（原生图标 = 红色圆角底 +
     * 白加号，r/cg5.xml 实证 #FF2442 / #FFFFFF），从原图标 drawable 提取（见
     * [decodePostAccent]）；null = 引擎纯玻璃不着色（静默降级）。
     */
    private fun publishAccentColor(): Color? {
        val group = postKeyRef?.get() as? ViewGroup ?: return null
        val drawable = postKeyDrawable(group) ?: return null
        synchronized(postAccentCache) {
            if (postAccentCache.containsKey(drawable)) return postAccentCache[drawable]
        }
        val accent = decodePostAccent(drawable)
        synchronized(postAccentCache) { postAccentCache[drawable] = accent }
        return accent
    }

    /** 发布键首个 ImageView 的当前 drawable（服务端 setPostTabIcon 换图自动跟随）。 */
    private fun postKeyDrawable(group: ViewGroup): android.graphics.drawable.Drawable? {
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? ImageView)?.let { return it.drawable }
        }
        return null
    }

    // —— 发布键按钮视觉（v3.0.2，用户定案：自绘加号 + 原图标仅取底色） ——

    /** 自绘加号缓存（按 densityDpi，密度变化即重绘；主线程帧循环专用，无需并发容器）。 */
    private var plusIconCache: Pair<Int, android.graphics.drawable.Drawable>? = null

    /** 强调色缓存（按原 drawable 实例：服务端换图/深浅色重建自动重算；失败也缓存，
     *  失败不打每帧重解码——§0.5 B.5 换肤重建映射纪律）。 */
    private val postAccentCache =
        java.util.WeakHashMap<android.graphics.drawable.Drawable, Color?>()

    /**
     * 自绘加号：32dp 透明位图上画白色圆头十字（臂长 9dp + 线宽 3.5dp ≈ 视觉 21.5dp，
     * 约 64dp 按钮的 1/3）。密度物理像素绘制 + `bitmap.density` 显式回写——
     * BitmapDrawable intrinsic 按密度换算，ExtraIconPainter 1:1 呈现时尺寸稳定。
     */
    private fun plusIcon(resources: android.content.res.Resources): android.graphics.drawable.Drawable {
        val dpi = resources.displayMetrics.densityDpi
        plusIconCache?.let { (cachedDpi, drawable) ->
            if (cachedDpi == dpi) return drawable
        }
        val d = resources.displayMetrics.density
        val size = (32f * d).toInt().coerceAtLeast(1)
        val arm = 9f * d
        val stroke = 3.5f * d
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = dpi
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = stroke
            color = android.graphics.Color.WHITE
        }
        val center = size / 2f
        canvas.drawLine(center - arm, center, center + arm, center, paint)
        canvas.drawLine(center, center - arm, center, center + arm, paint)
        val drawable = BitmapDrawable(resources, bitmap)
        plusIconCache = dpi to drawable
        return drawable
    }

    /**
     * 底色提取（仅取强调色，字形已不再来自原图标）：不透明像素（alpha ≥ 128）r/g/b 各
     * 5 位量化直方图众数——红底占绝对多数、白加号 ~8% 面积不动摇众数 → 众数 = 精确底色
     * （矢量内边距使位图四边透明，不参与统计；服务端换图/深浅色重建 drawable 自动重算）。
     * 全透明/尺寸非法/解码异常 → null（缓存，静默降级纯玻璃）。
     */
    private fun decodePostAccent(
        drawable: android.graphics.drawable.Drawable,
    ): Color? = runCatching {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        val baseBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        drawable.bounds = baseBounds
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val hist = IntArray(1 shl 15)
        var opaque = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            hist[(((p shr 19) and 0x1F) shl 10) or (((p shr 11) and 0x1F) shl 5) or ((p shr 3) and 0x1F)]++
            opaque++
        }
        if (opaque == 0) return null
        var modeIdx = 0
        var modeCount = 0
        for (i in hist.indices) {
            if (hist[i] > modeCount) {
                modeCount = hist[i]
                modeIdx = i
            }
        }
        Color(
            ((modeIdx shr 10) and 0x1F) shl 3,
            ((modeIdx shr 5) and 0x1F) shl 3,
            (modeIdx and 0x1F) shl 3,
        )
    }.getOrNull()

    /**
     * 发布键点击派发（extraActionButton.onClick）：经 `getIndexPost()` 私有 getter（真实
     * 方法名稳定锚点，cachedDeclaredMethod 解析）拿真实发布键视图 → 穿透式 performClick
     * 走应用完整原生管线（登录/广告门控保留）——v2.2 槽位 2 同款路径，performClick 不
     * 检查 isClickable，引擎 clickable 树禁用不影响。getter 失配回退装配期捕获的
     * postKeyRef；两路全失效打 WARN 留痕。
     */
    private fun dispatchPostClick() {
        val container = activeSession?.tabContainer
        val getterTarget = container?.let { bar ->
            cachedDeclaredMethod(bar.javaClass, TAB_GETTERS[2])?.let { getter ->
                runCatching { getter.invoke(bar) as? View }.getOrNull()
            }
        }
        val target = getterTarget ?: postKeyRef?.get()
        val boundary = container ?: target?.parent as? View ?: target
        if (target != null && boundary != null && performClickThroughTab(target, boundary)) {
            hookFacade?.log(android.util.Log.INFO, "xhs publish via native pipeline")
            return
        }
        hookFacade?.log(android.util.Log.WARN, "xhs publish dispatch failed (post key gone?)")
    }

    /**
     * 内容延伸：ExploreScrollableViewPager 的静态 `marginBottom=45dp`（底栏可视高储备）
     * 归零，信息流延伸到底栏后面，玻璃采样条带有活内容可折射。仅装配时一次；应用无
     * 该 margin 的运行时写入口（classes13 全量反编译实证），视图树重建经 isStale 重装
     * 路径自然重做。不随模块开关还原——重启应用即原生（微博静态归零先例）。
     * v2.1：走 [explorePager] 缓存——储备重扫由选中变化防抖触发，缓存命中且 margin 已
     * 归零时 O(1) 返回（此前每次全 decor DFS + 每次打一条日志）；视图树重建时缓存自动
     * 失效回退全树查找。
     */
    private fun zeroContentReserve(activity: Activity?, facade: HookFacade) {
        if (activity == null) return
        runCatching {
            val pager = explorePager() ?: return
            val lp = pager.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            if (lp.bottomMargin == 0) return
            if (capturedReservePx == 0) capturedReservePx = lp.bottomMargin
            lp.bottomMargin = 0
            pager.layoutParams = lp
            facade.log(
                android.util.Log.INFO,
                "xhs content reserve zeroed (reserve=${capturedReservePx}px)",
            )
        }.onFailure { facade.log(android.util.Log.WARN, "xhs content reserve zeroing failed", it) }
    }

    /**
     * 条目顶部设计留白中和：TabView 60dp 高 + paddingTop=15dp（XML 设计值）让文字中心
     * 落在条目 62% 高度处，整条目 1:1 居中镜像会把这份偏下如实搬进玻璃槽位。归零抑制态
     * 条目的 paddingTop 后文字回到整条目垂直居中（约束不动，仅 padding 盒变化；padding
     * 不在 ConstraintSet 管理范围，应用切换选中态不写回）。原生底栏已 alpha=0 隐藏，
     * 对应用原生视觉零影响。仅装配时一次；重装路径自然重做。
     */
    private fun neutralizeItemTopInset(session: GlassSession, facade: HookFacade) {
        runCatching {
            var fixed = 0
            for (v in session.tabViews) {
                if (isTabItem(v) && v.paddingTop > 0) {
                    v.setPadding(v.paddingLeft, 0, v.paddingRight, v.paddingBottom)
                    fixed++
                }
            }
            facade.log(
                android.util.Log.INFO,
                if (fixed > 0) "xhs item top inset neutralized: $fixed tab(s)"
                else "xhs item top inset none (structure changed?)",
            )
        }.onFailure { facade.log(android.util.Log.WARN, "xhs item top inset neutralizing failed", it) }
    }

    override fun onTabActivityResumed(activity: Activity, facade: HookFacade, config: () -> ModuleConfig) {
        AppAdapter.applyTransparentNavigationBar(activity, config)
    }
}
