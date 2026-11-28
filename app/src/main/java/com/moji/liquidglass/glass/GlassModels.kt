package com.moji.liquidglass.glass

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.moji.liquidglass.config.ModuleConfig
import kotlinx.coroutines.channels.Channel

/** 玻璃栏样式参数（模块设置页可调，经 GlassInstaller.glassStyle 下发到玻璃层）。 */
data class GlassStyle(
    /** 两侧水平边距（dp）：玻璃胶囊不再顶到屏幕边缘。 */
    /** 玻璃栏最小安全边距（dp）：实际边距 = max(推导边距, 此值)，
     *  tab 少时栏收窄为内容宽居中，边距自动放大但不低于此下限。 */
    val minHorizontalMarginDp: Float = 20f,
    /** 小白条上方的额外间隙（dp），叠加在导航栏 inset 之上。 */
    val bottomGapDp: Float = 0f,
    /** 栏高（dp），内容行 = 栏高 - 8dp。 */
    val barHeightDp: Float = 60f,
    /** 折射模糊半径（dp）。 */
    val blurRadiusDp: Float = 5f,
    /** 玻璃表面色不透明度（0~1）：叠在模糊内容上的底色浓度，0 = 纯透明玻璃。 */
    val surfaceAlpha: Float = 0.25f,
    /** 色彩饱和度：模糊内容的鲜艳度（vibrancy），默认 1.5（iOS 同款）。 */
    val saturation: Float = 1.5f,
    /** 折射量：玻璃边缘与液泡折射位移幅度（shader refractionAmount）的缩放系数，
     *  0 = 无折射平边。默认 1（100%）。 */
    val refractionAmountScale: Float = 1f,
    /** 折射高度：折射带宽（边缘向内发生折射的距离，shader refractionHeight）的缩放系数，
     *  0 = 无折射平边。默认 0.50（50%）。 */
    val refractionHeightScale: Float = 0.50f,
    /** 是否显示 tab 文字标签。 */
    val showLabels: Boolean = true,
    /** 全局玻璃栏边缘光/内晕开关（实验，默认开）：关闭 = 原 Highlight.Default、无内晕。 */
    val edgeGlow: Boolean = true,
    /** 边缘光宽度（dp）。 */
    val edgeWidthDp: Float = 1.5f,
    /** 边缘光柔化（dp）。 */
    val edgeBlurDp: Float = 0.75f,
    /** 边缘光强度（0~1）。 */
    val edgeAlpha: Float = 0.22f,
    /** 内晕半径（dp）。 */
    val innerGlowRadiusDp: Float = 10f,
    /** 内晕强度（0~1）。 */
    val innerGlowAlpha: Float = 0.35f,
) {
    companion object {
        fun fromConfig(config: ModuleConfig): GlassStyle = GlassStyle(
            minHorizontalMarginDp = config.barMarginDp,
            bottomGapDp = config.barBottomGapDp,
            barHeightDp = config.barHeightDp,
            blurRadiusDp = config.barBlurDp,
            surfaceAlpha = config.barSurfaceAlpha,
            saturation = config.barSaturation,
            refractionAmountScale = config.barRefractionAmountScale,
            refractionHeightScale = config.barRefractionHeightScale,
            showLabels = config.barShowLabels,
            edgeGlow = config.barEdgeGlow,
            edgeWidthDp = config.barEdgeWidthDp,
            edgeBlurDp = config.barEdgeBlurDp,
            edgeAlpha = config.barEdgeAlpha,
            innerGlowRadiusDp = config.barInnerGlowRadiusDp,
            innerGlowAlpha = config.barInnerGlowAlpha,
        )
    }
}

/** 从原底栏提取出的单个 tab 数据。 */
class GlassTab(
    val title: String,
    /** LOTTIE_RESOLVE 模式：应用真实 Lottie 图标视图（玻璃层逐帧镜像绘制）。 */
    val iconView: View?,
    /** Lottie 图标按当前帧位图化的结果（3x 采样，视图镜像不可用时兜底）。 */
    val icon: android.graphics.Bitmap?,
    /** WHOLE_TAB 模式：整个 tab 视图（图标/标签/角标一体，逐帧镜像）。 */
    val tabView: View? = null,
    /** true = 镜像整个 tab 视图（WHOLE_TAB）；false = 图标镜像 + Compose 标签/角标。 */
    val mirrorWholeTab: Boolean = false,
    /** 角标视图（数字/红点，含自身动画），镜像绘制优先于文本/圆点兜底。 */
    val badgeView: View?,
    /** update_noti_num 数字角标内容，null 表示无数字角标。 */
    val badgeText: String?,
    /** 引导点/红点角标（guide_iv、trash_noti_red_dot 可见时为 true）。 */
    val badgeDot: Boolean,
)

/** 整条玻璃底栏的装配参数。 */
class GlassBarSpec(
    val tabs: List<GlassTab>,
    val initialIndex: Int,
    /** 未选中内容色（取自应用文字选择器 default 色）。null 时 GlassRoot 回退参考项目配色。 */
    val contentColor: Color?,
    /** 选中强调色（取自应用文字选择器 selected 色）。null 时回退参考项目 iOS 蓝。 */
    val accentColor: Color?,
)

/**
 * 一次玻璃底栏装配的会话：持有原视图引用用于选中同步 / 点击转发 / 还原。
 * hook 层（各 AppAdapter）与玻璃层（GlassRoot）共同持有。
 *
 * 覆盖层模式下应用内容视图原位不动，本会话只管理 ComposeView 覆盖层与底栏可见性。
 */
class GlassSession(
    /** 应用内容根视图（原位保留，作为玻璃采样的绘制源）。 */
    val appRoot: View,
    /** 原底栏容器（装配时可见但隐形 alpha=0）。 */
    val tabContainer: View,
    /** 原 tab 视图列表（保持在视图树中，用于 isSelected 读取与 performClick 转发）。 */
    val tabViews: List<View>,
    val composeView: View,
    /** 描述符驱动的提取器（动画驱动/拖动预览按角色解析视图）。 */
    val extractor: TabExtractor,
) {
    /** 装配时原底栏的可见性，还原时恢复。 */
    var originalContainerVisibility: Int = tabContainer.visibility
        private set

    /** 装配时原底栏的 a11y 参与度，还原时恢复（装配后被 NO_HIDE_DESCENDANTS 隐身）。 */
    var originalContainerImportance: Int = tabContainer.importantForAccessibility
        private set

    /** 装配前 decorView 上原有的 ViewTree owner（GlassInstaller 覆盖 decor owner 前捕获、
     *  经 [attachDecorOwners] 注入，还原时恢复——避免模块 owner 永久污染应用自身解析）。 */
    private var originalDecorOwners: Triple<Any?, Any?, Any?>? = null
    private var originalDecorRef: java.lang.ref.WeakReference<View>? = null

    /** 装配时（覆盖 decor owner 之前）由 GlassInstaller 注入的 owner 快照与载体视图。 */
    fun attachDecorOwners(
        decor: View,
        lifecycle: Any?,
        viewModel: Any?,
        savedState: Any?,
    ) {
        originalDecorRef = java.lang.ref.WeakReference(decor)
        originalDecorOwners = Triple(lifecycle, viewModel, savedState)
    }

    /** 玻璃层检测到原视图树被应用重建（失联）时回调，hook 层据此重装。 */
    @Volatile
    var onStale: (() -> Unit)? = null

    /** 镜像绘制门控（适配器注入）：返回 false 时玻璃镜像不绘制（应用要求隐藏底栏）。 */
    @Volatile
    var mirrorVisibleGate: (() -> Boolean)? = null

    // —— 辅助玻璃视图（声明式通道，默认 null = 其余应用零影响）——
    // 应用内除底栏外需要"同款玻璃底"的视图（如文件管理文件页的存储状态条
    // space_situation）：原生视图由适配器压制（alpha=0 + 背景置空 + 宽度包裹内容），
    // 引擎逐帧镜像内容 + 绘制同款玻璃胶囊（采样同源、样式参数取自 GlassStyle）。
    /** 辅助玻璃视图提供者（帧循环每 tick 调用，返回当前有效的视图列表；空/null = 不渲染）。
     *  压制（alpha/背景/宽度）由适配器负责（应用专属知识），引擎只负责镜像与玻璃绘制。 */
    @Volatile
    var auxGlassViews: (() -> List<View>)? = null

    /** 辅助玻璃还原回调（restore() 尾部调用）：适配器恢复压制视图的原 alpha/背景/宽度
     *  与分割线等一次性显隐修改。 */
    @Volatile
    var onAuxGlassRestore: (() -> Unit)? = null

    /** 辅助玻璃形状钩子（默认 null = 胶囊 Capsule，与玻璃底栏同款）：按视图声明
     *  玻璃底形状的备用通道（当前无应用使用——文件管理存储条曾声明圆角矩形，
     *  2.9.17 起回退默认胶囊）。 */
    @Volatile
    var auxGlassShape: ((View) -> androidx.compose.ui.graphics.Shape)? = null

    /** 背景采样去重（TargetHook 会话创建期统一设置 = 唯一设置点，默认 true）：true 时
     *  ClippedViewBackdrop 每 tick 只把内容条带录制一次（RenderNode），三个玻璃节点
     *  回放同一录制——与直绘逐像素等价，全树遍历 3 次/tick → 1 次/tick（未开启时
     *  drawDirect 每 tick 三次全树单参直绘 = "全屏采样"级开销，GL/TextureView 内容页
     *  更是整屏内容重绘）。false = 各自直绘（配置 lightweightSampling 远程关，视觉
     *  异常回退）。跨 Surface 重建的回放空窗由 TargetHook onResume invalidateBackdrop
     *  覆盖。 */
    @Volatile
    var dedupeBackdropSampling: Boolean = true

    /** 实时显示列表采样（**引擎默认 true**，TargetHook 会话创建期按配置 `liveNodeSampling`
     *  与 descriptor `backdropDirectDraw` 唯一设置）：去重开启时生效，改为引用内容视图
     *  自身已录制的 `RenderNode`（隐藏 API `View.mRenderNode`，取不到自动回退），条带仅
     *  在几何变化/唤醒时重录、其余帧纯回放——主线程每帧零重编码，条带内容仍是应用当帧
     *  录制的显示列表（**无降频滞后**）。
     *  根因（对标 SuperLiquid `Backdrop.drawRecorded`）：每 tick `view.draw()` 会把宿主
     *  内容树重新编码——相册 GL 内容页整屏重画、小黑盒上滑新绑定条目逐帧重付首建成本
     *  （№02.2/№15.7）。默认开 = 全部应用受益；个别应用显示列表回放异常时由
     *  descriptor `backdropDirectDraw` 或远程配置 `liveNodeSampling=false` 回退旧直绘。 */
    @Volatile
    var backdropLiveNodeSampling: Boolean = true

    /** 镜像显示列表引用（**引擎默认 true**，与 [backdropLiveNodeSampling] 同一配置源、
     *  descriptor `viewDirectMirror` 可关闭）：玻璃镜像 Painter 不再每 tick 单参
     *  `view.draw(宿主视图)`，改为引用该视图已录制的 RenderNode（`ViewDisplayList`）。
     *  主线程零重编码，RenderThread 按镜像盒裁剪栅格化；alpha≠1 / RenderEffect /
     *  硬件层视图自动回退直绘，行为与旧实现逐位一致。适用面 = 整条目镜像（微博/小黑盒/
     *  小红书）与逐帧 Lottie 图标镜像——这两类每 tick 会把宿主条目重新绘制 2 遍
     *  （可见行 + 折射副本行）。 */
    @Volatile
    var viewNodeMirror: Boolean = true

    /** 条带显示列表引用当前是否生效（引擎写入、适配器只读）：true = 本 tick 走 live
     *  路径；false = 回退 `view.draw`。小黑盒滚动冻结门控据此降级为"仅 live 失效时
     *  兜底"（live 生效时滚动期保持实时折射，不再定格快照）。 */
    @Volatile
    var liveSamplingActive: Boolean = false

    /** 一次性诊断日志（同一 key 只输出一次，主线程调用）：镜像/采样模式只在首次
     *  生效或首次回退时各留一条，避免逐 tick 刷屏。 */
    fun noteOnce(key: String, message: String) {
        synchronized(notedOnce) {
            if (!notedOnce.add(key)) return
        }
        logger?.invoke(message)
    }

    private val notedOnce = HashSet<String>()

    /** 采样冻结门控（声明式通道，默认 null = 其余应用零影响）：适配器注入，返回 true
     *  时 backdrop 不对 appRoot 做逐帧外部 draw——**进入冻结时一次性录制**条带、冻结
     *  期间纯回放，离开后自动恢复实时采样。外部高频 view.draw 会打乱部分自绘渲染树的
     *  合成——WebView（Chromium）被外部重绘会间歇输出空白帧（整页闪白，vivo 主题 VIP
     *  专区 H5 页实证）、GL 内容（相册）采样即触发 GL 绘制——适配器在含此类内容的页面
     *  声明冻结，用定格快照换取零干扰。与采样去重（[dedupeBackdropSampling]）解耦：
     *  非冻结页保持原直绘路径（去重回放跨 Surface 重建的显示列表会被 HWUI 丢弃，
     *  表现为"玻璃轮廓在、内容空"——回放型采样必须配合 [invalidateBackdrop]）。冻结
     *  录制失败/无录制产物时冻结帧不绘制（透明，极端降级）。每帧调用（draw 阶段），
     *  闭包应自缓存、避免全树遍历热路径。 */
    @Volatile
    var backdropFreezeGate: (() -> Boolean)? = null

    /** 强制重录制请求（[invalidateBackdrop] 置位，backdrop 消费后自动清除）：跨 Surface
     *  重建（后台切换）会丢弃 bandNode 已录制的显示列表，去重/冻结回放在重录制前为空。
     *  hook 层在 Activity onResume 时调用 [invalidateBackdrop]（主线程）。 */
    @Volatile
    var backdropForceRecord: Boolean = false

    /** 回前台唤醒 backdrop：下一帧强制重录制采样条带一次（去重帧 = 多录一次；冻结帧 =
     *  单次外部 draw 后继续纯回放）。深度静止睡眠中时一并唤醒帧循环（无信号源）
     *  立即消费重录制。 */
    fun invalidateBackdrop() {
        if (!backdropForceRecord) {
            backdropForceRecord = true
            logger?.invoke("backdrop invalidate (re-record after resume)")
        }
        wakeFrameLoop()
    }

    /** 内容活动信号会话级消费者（可选，采样冻结检测等）：引擎唯一信号管线广播（hook
     *  的 onDraw 信号）时调用，[armActivityWindow] 之外的第二消费者。玻璃发起的宿主
     *  视图直绘期间不调用（信号抑制，防自激回路）。主线程调用。 */
    @Volatile
    var onContentActivity: (() -> Unit)? = null

    /** 拖动悬停预览激活中（引擎维护，适配器 hook 回调只读）。部分应用的条目选中翻转
     *  方法带**切页副作用**（微博 TabViewGroupV2.setChecked(true) 的选中变化监听器直接
     *  TabHost.setCurrentTabByTag）——预览翻转条目选中外观时会误触切页。适配器应 hook
     *  该副作用入口并在本标志为 true 时吞掉；正式切页仍由松手后 onTabClicked 的
     *  performClick 完成（那时标志已复位）。主线程读写。 */
    @Volatile
    var isTabPreviewActive: Boolean = false
        private set

    /** 选中 tab 的进入动画是否进行中（帧循环据此自驱满帧率）。 */
    val tabAnimating: Boolean get() = animLottie != null

    /** 静止降帧两档驱动 + 高刷减半分频（TargetHook 会话创建期对所有会话统一开启，唯一
     *  设置点；配置 `idleFrameSleep` 关闭时保持 false = 逐位不变旧行为每帧恒满档）：
     *  true 时帧循环按"活动窗口内满帧率、静止 30fps 低频"两档 tick，且高刷屏（实测
     *  平均 >66Hz）玻璃满帧率档**按帧计数减半分频**（偶数帧 tick，任意刷新率恒整数倍
     *  零拍频；120 帧窗口重估，LTPO 动态刷新率自动跟随）。
     *  **前提 = 已接内容活动信号**（内容重绘/交互 → [armActivityWindow]）——静止档
     *  只有 30fps 采样，无信号的内容变化滞后 ≤33ms 由低频档自愈，持续无信号的内容
     *  变化则持续滞后。适用 = 采样开销大的应用（重内容页/整条目镜像 + 逐帧 lottie
     *  镜像）：满帧率采样在重页面上与宿主自绘叠加挤占帧预算 = 滑动卡顿，两档化后
     *  静止开销降 ~90%、滚动期信号升档 + 分频减半保流畅。 */
    @Volatile
    var idleDecayTickEnabled: Boolean = false

    /** 高刷减半 tick（默认 false = 满帧，TargetHook 按配置 `highRefreshHalfRate` 唯一
     *  设置）：仅面板实测平均 ≥100Hz 时生效（90Hz 永不减半——旧 >66Hz 判定让 90Hz
     *  掉到 45fps 是明确缺陷）。**默认关**：减半会让折射层落后半帧（相册 №02.7
     *  "拖影不跟手"），每 tick 重编码成本已由显示列表引用（ViewDisplayList）消除；
     *  仅 120Hz 真机实测玻璃侧掉帧时远程打开兜底。 */
    @Volatile
    var highRefreshHalfRate: Boolean = false

    /** 深度静止档帧循环睡眠（1Hz，与 [idleDecayTickEnabled] 同一设置点统一开启，默认
     *  false = 逐位不变旧行为）：前提 = [idleDecayTickEnabled] 已开启（信号驱动会话，
     *  **无信号源不得开启**）。true 时帧循环在"活动窗口（1s）结束 → 30fps 静止档维持
     *  [deepIdleDelayMs]"后进入深度静止——退出 `withFrameNanos` 循环（停止订阅
     *  Choreographer 帧，这是原实现静止时仍 60Hz 的根因：帧回调每颗 vsync 挂起、窗口
     *  永不空闲、LTPO 无法降档），改由墙钟定时 + [frameLoopWake] 唤醒通道驱动，玻璃
     *  以 [deepIdleTickIntervalNs]（默认 1s，即 LTPO 最低档 1Hz）心跳采样。任何活动
     *  信号（[armActivityWindow]/[invalidateBackdrop]/选中动画）经唤醒通道立即回到
     *  满帧率，无感知过渡。视频 TextureView 已在默认信号类内——播放期间信号逐帧
     *  续期、永不降档。 */
    @Volatile
    var idleFrameSleepEnabled: Boolean = false

    /** 深度静止档进入延迟（毫秒）：30fps 静止档持续此时长后进入 1Hz 睡眠。两级衰减
     *  设计——短停顿期间玻璃仍 30fps 跟得上无信号的内容变化，只有真正长静止才付 1Hz
     *  滞后代价。默认 2s：总时长 = 活动窗口 1s + 2s = 最后活动后 ~3s 进入 1Hz。 */
    @Volatile
    var deepIdleDelayMs: Long = 2_000L

    /** 深度静止档玻璃采样心跳间隔（纳秒，默认 1s = 1Hz）：显示管线对 1Hz 内容更新
     *  可降到面板最低档（LTPO 1Hz），对齐原生静止页面功耗。 */
    @Volatile
    var deepIdleTickIntervalNs: Long = 1_000_000_000L

    /** 深度静止档唤醒通道（CONFLATED，主线程专用）：睡眠中的帧循环挂起在 receive 上，
     *  墙钟超时（1Hz 心跳）或 [wakeFrameLoop]（活动信号）任一到达即返回。发送永不
     *  挂起、覆盖旧值，睡眠/泵帧两态下调用均无害。 */
    internal val frameLoopWake = Channel<Unit>(Channel.CONFLATED)

    /** 唤醒深度静止睡眠中的帧循环（主线程调用；非睡眠态下为一次通道写，无副作用）。 */
    fun wakeFrameLoop() {
        frameLoopWake.trySend(Unit)
    }

    // —— 相册的"静止降帧"档位（现引擎统一档位）：内容活动（GLRootView.onDraw 等
    //    信号）或底栏交互后的窗口期内满帧率；静止后降到 30fps（GlassRoot 帧循环门控）。
    //    静止档**仍以低频持续采样**，GL 合成空窗的空白帧最多停留 ~33ms 自愈，不会
    //    冻帧；图标/光效动画在交互窗口的满帧率内完整转播 ———
    @Volatile
    private var activeUntilMs = 0L

    /** 内容/交互活动窗口（满帧率档）：内容活动信号管线（单一汇入口）与各交互点调用。
     *  主线程调用。深度静止睡眠中时经 [wakeFrameLoop] 立即唤醒回满帧率（睡眠/泵帧
     *  两态下调用均无害）。
     *  **自激防护**：玻璃发起的宿主视图直绘（[withSignalSuppression] 置位期间）产生的
     *  onDraw 信号是绘制副作用而非内容活动，直接吞掉——镜像 Painter 每 tick 单参
     *  `view.draw` 直绘被 hook 类（ImageView/CompoundButton 条目），不吞则静止档首个
     *  tick 即被拉回满帧（深度静止永不进入，真机"静止稳定 60Hz"根因）。 */
    fun armActivityWindow(durationMs: Long = 1_000L) {
        if (signalSuppressed) return
        activeUntilMs = android.os.SystemClock.elapsedRealtime() + durationMs
        wakeFrameLoop()
    }

    /** 满帧率档是否生效（false = 静止，帧循环按 30fps 低频 tick）。 */
    val contentActive: Boolean
        get() = android.os.SystemClock.elapsedRealtime() < activeUntilMs

    companion object {
        /** 玻璃发起的宿主视图绘制进行中（主线程标志）：镜像 Painter 单参直绘 + backdrop
         *  采样重录期间置位。期间 hook 到的 onDraw 信号（ImageView/CompoundButton 等
         *  声明点）是**绘制副作用**而非内容活动——真内容信号在宿主自身 traversal 栈
         *  触发，与玻璃 draw 相主线程串行不重叠，不会误吞。 */
        @Volatile
        var signalSuppressed: Boolean = false

        /** 玻璃直绘区段包装：置位抑制标志执行 [block]，结束恢复（支持嵌套，按前置值
         *  恢复）。 */
        inline fun <T> withSignalSuppression(block: () -> T): T {
            val prev = signalSuppressed
            signalSuppressed = true
            return try {
                block()
            } finally {
                signalSuppressed = prev
            }
        }
    }

    /** WHOLE_TAB 图标帧预览钩子：返回指定 tab 的"选中动画 Lottie drawable"（任意对象，
     *  引擎反射 setProgress 驱动并临时挂载到该 tab 图标 ImageView 使帧可被镜像），取不到
     *  返回 null。未注入（相册走 setChecked 原生路径，图标外观自带选中态）时图标帧预览
     *  整段跳过。由适配器按应用注入（vivo 官网：底栏内部 item 状态表反射）。 */
    @Volatile
    var previewIconLottie: ((index: Int) -> Any?)? = null

    /** 预览图标换图钩子（默认 null = 其余应用零影响）：WHOLE_TAB 条目的图标为"随选中
     *  态换图"的双 drawable 应用（如文件管理 discolor 图标：选中 = 品牌色 drawable、
     *  非选中 = 灰色 drawable，换图由应用自身选择路径 H()/r() 驱动、setChecked 不换图）
     *  在适配器提供此闭包，setTabChecked 每次翻转后调用——拖动预览期间悬停 tab 换上
     *  选中态 drawable、被拖离的原选中换回非选中态（与商店"气泡内图标 = 换图后选中态
     *  资源的染色副本"机制对齐，否则气泡里是非选中细轮廓的染色，观感浅于强调色）。 */
    @Volatile
    var previewIconSwapper: ((index: Int, checked: Boolean) -> Unit)? = null

    /** 帧驱动钩子：对 [previewIconLottie] 返回的 drawable 驱动到指定进度（0=首帧，
     *  1=末帧）。未注入时回退反射 `setProgress`（Lottie 方法未被混淆的应用，如商店）；
     *  R8 连库方法一起混淆的应用（vivo 官网，进度真名 "L"）必须注入。 */
    @Volatile
    var previewIconProgress: ((lottie: Any, progress: Float) -> Unit)? = null

    /** WHOLE_TAB 帧预览的"反选还原"钩子（可选，默认 null = 维持动画首帧回退语义）：
     *  液泡离开被拖的原选中 tab 时，以应用自身的反选换图语义回到未选中状态（vivo
     *  官网 VBottomNavigationView.w(item)：y(false)+停动画+换回未选中静态图——其
     *  选中动画首帧仍带选中观感，不能当未选中外观）。返回 true = 已应用；null 或
     *  返回 false → 引擎回退动画首帧（旧行为，未注入的应用逐位不变）。 */
    @Volatile
    var previewIconUnselected: ((index: Int) -> Boolean)? = null

    /** 诊断日志出口（hook 层注入 facade.log）。 */
    @Volatile
    var logger: ((String) -> Unit)? = null

    /** 内容采样停用（声明式通道，默认 false = 既有应用逐位不变）。true = 条带不录制/
     *  不回放/不直绘，玻璃节点只留表面色（特效层内容为空）。为"采样条带在 RenderEffect
     *  特效链内回放含 native 消费 TextureView（地图引擎为生产者）的绘制算子 = 宿主 GL
     *  渲染死亡"的应用声明——2026-11-08 高德真机二分定案：采样+特效同开 = 整页黑屏 +
     *  液泡边缘黑色锯齿；任一单独停用 = 正常（两档真机各验证一次）。采样回放不进特效
     *  层（特效关）或特效层不含 TextureLayer 算子（采样关）均无害。 */
    var backdropSamplingDisabled: Boolean = false

    /** tab 转发后的取证回调（hook 层注入，如 padding dump）。在主线程调用。 */
    @Volatile
    var afterForward: (() -> Unit)? = null

    /** 应用当前选中 tab 下标；读不到返回 -1。主线程调用。
     *  WHOLE_TAB 拖动预览期间冻结为预览起点：预览翻转的是条目自身 isSelected
     *  （setChecked 内部调 setSelected），不冻结会让帧循环误判应用已切页，
     *  松手时 onTabSelected 守卫会跳过 performClick 转发。 */
    fun selectedTabIndex(): Int {
        wholeTabPreviewOrigin.takeIf { it >= 0 }?.let { return it }
        return tabViews.indexOfFirst { it.isSelected }
    }

    /** 玻璃 tab 被点击：转发到原 TabView（其 OnClickListener 会走应用自身的切页逻辑）。 */
    fun onTabClicked(index: Int) {
        armActivityWindow()
        val view = tabViews.getOrNull(index) ?: return
        // WHOLE_TAB 预览收口（微博类应用）：预览已把目标条目 checked 翻成 true，而部分
        // 应用的切页信号恰是 checked 的"变化"（微博 setChecked(false→true) 的监听器才调
        // TabHost.setCurrentTabByTag；已选中时再 setChecked(true) 直接 return）——不还原
        // 的话松手转发 performClick 是无效转换，页面永远不切。此处先把条目还原到预览前
        // 状态（还原期间的切页副作用仍被吞咽：还原 origin 的 setChecked(true) 不切页，
        // TabHost 本就停在 origin），再放行 performClick 产生真实变化 → 应用正常切页。
        // 相册等无副作用应用：还原与后续应用自身的切换同栈完成、中间无绘制，无可见抖动。
        val previewing = wholeTabPreviewOrigin >= 0
        // 收口回退：applier 全量重设型适配器（高德）声明 skipPreviewRestoreOnSubmit 时
        // 跳过——悬停态与点击目标一致，收口反而把悬停态翻回原 tab（见 descriptor KDoc）
        if (previewing && !extractor.descriptor.skipPreviewRestoreOnSubmit) {
            val origin = wholeTabPreviewOrigin
            if (index != origin) {
                isTabPreviewActive = true
                setTabChecked(index, false)
                setTabChecked(origin, true)
            }
        }
        isTabPreviewActive = false // 正式点击切换：选中翻转的切页副作用必须放行
        val start = android.os.SystemClock.elapsedRealtime()
        // 描述符接管点击派发（如小红书：生效监听在祖先容器，条目 performClick 无效，
        // 走适配器的穿透式点击）；默认对条目视图 performClick 走应用自身监听器。
        extractor.descriptor.tabClickHandler?.invoke(index, tabViews, tabContainer)
            ?: view.performClick()
        val cost = android.os.SystemClock.elapsedRealtime() - start
        if (cost > 100) {
            logger?.invoke("performClick(index=$index) blocked main thread for ${cost}ms")
        }
        if (wholeTabPreviewOrigin >= 0) {
            // 拖动预览提交：清除冻结，交还应用真实选中读数（视觉由应用自身 setChecked 流程接管）。
            // 点击的 tab 图标由应用切换流程（I() 播放/换静态图）接管，不恢复保存的原 drawable；
            // 其余 tab 已在离开时逐一恢复，此处仅清缓存。
            wholeTabPreviewOrigin = -1
            wholeTabPreviewTarget = -1
            savedPreviewDrawables.clear()
        }
        afterForward?.invoke()
    }

    // —— 选中 tab 的 Lottie 进入动画由玻璃侧逐帧直控进度（不依赖应用动画器：
    //    playAnimation 在原视图隐藏等场景下不可靠）——
    private var animLottie: View? = null
    private var animStartMs = 0L
    private var animDurationMs = 400f
    /** 当前处于选中视觉末帧的 lottie（正式选中 tab）。 */
    private var selectedLottie: View? = null
    /** 拖动预览中临时置为末帧的 lottie（非正式选中）。 */
    private var previewLottie: View? = null
    /** 上一次进入动画的 tab 下标（选中切换时对其做双 Lottie 全量复位）。 */
    private var lastAnimatedIndex = -1
    /** 复位时观测到非零帧/动画器存活的取证（只记首条，防刷屏）。 */
    private var resetObservedDirty = false
    private val lottieCache = HashMap<Int, View?>()

    private fun lottieAt(index: Int): View? = lottieCache.getOrPut(index) {
        tabViews.getOrNull(index)?.let { extractor.findLottieView(it) }
    }

    private fun tabIndexOfLottie(lottie: View): Int =
        (0 until tabViews.size).firstOrNull { lottieAt(it) === lottie } ?: -1

    /**
     * 复位一个 Lottie 到未选中基帧：`cancelAnimation()` 先于 `setProgress(0)`——
     * 与应用自身复位语义逐字一致（商店 TabView.v(true)）。cancel 在动画器仍处
     * started 态时触发 onAnimationCancel，应用的图标选中色回调借此翻回未选中色
     * （TabView 的 t(D && isSelected())：onAnimationStart→品牌色、onAnimationCancel→
     * 按 isSelected 回归）；引擎的 setProgress 直写不产生任何动画器事件，选中色
     * 回调会滞留品牌色帧，呈"切走后图标未恢复未选中"（刷新完成动画播放过再切走
     * 时的低概率路径，真机实测）。失败静默（应用换非 Lottie 实现时由 setLottieProgress
     * 的既有软失败兜底）。
     */
    private fun resetLottieToBase(lottie: View) {
        runCatching {
            lottie.javaClass.getMethod("cancelAnimation").invoke(lottie)
        }
        setLottieProgress(lottie, 0f)
    }

    /**
     * 旧选中 tab 的双 Lottie（主 + 刷新）全量复位：商店首页是双 Lottie，可见性由
     * 应用刷新态状态机翻转——单跟踪 selectedLottie 只覆盖装配/选中时刻的可见者，
     * 刷新态中切走的路径会漏掉主 Lottie（其上还挂着引擎上次驱动的选中末帧或应用
     * 刷新完成动画的残留进度）。全量复位对齐应用自身不变量：未选中 = 两枚 Lottie
     * 都在基帧（v(true) 同款）。应用早一步复位时此处幂等无害。
     */
    private fun resetPreviousTabLotties(previousIndex: Int) {
        if (previousIndex !in tabViews.indices) return
        for (lottie in extractor.findLottieViews(tabViews[previousIndex])) {
            if (!resetObservedDirty) {
                val dirty = runCatching {
                    lottie.javaClass.getMethod("isAnimating").invoke(lottie) as Boolean
                }.getOrDefault(false) ||
                    runCatching {
                        lottie.javaClass.getMethod("getProgress").invoke(lottie) as Float
                    }.getOrDefault(0f) != 0f
                if (dirty) {
                    resetObservedDirty = true
                    logger?.invoke("tab reset: prev tab #$previousIndex lottie dirty (progress/animating) -> cancel+0")
                }
            }
            resetLottieToBase(lottie)
        }
    }

    /**
     * 开始播放指定 tab 的 Lottie 进入动画：记录起点与时长，由 [driveTabAnimation]
     * 逐帧推进。若目标已在拖动预览末帧（拖动后松手），跳过动画直接定帧；
     * 上一个选中 tab 复位到基础帧。主线程调用。
     */
    fun beginTabAnimation(index: Int) {
        // 深度静止档唤醒：LOTTIE_RESOLVE（商店）选中动画走 animLottie 自驱（fullRate 含
        // tabAnimating），非 LOTTIE 系由下方 armActivityWindow 唤醒——动画开始即回满帧率
        wakeFrameLoop()
        // WHOLE_TAB 系（相册/微博）：选中变化（玻璃侧点击与应用侧同步都经此入口）伴随
        // 图标动画，开满帧率窗口让镜像逐帧跟随
        if (extractor.descriptor.iconMode != IconMode.LOTTIE_RESOLVE) armActivityWindow()
        // 旧选中 tab 先全量复位（双 Lottie、cancel+0）：拖动预览提交后这里同样走到，
        // 预览末帧残留一并清掉
        val previous = lastAnimatedIndex
        lastAnimatedIndex = index
        if (previous != index) resetPreviousTabLotties(previous)
        val lottie = lottieAt(index)
        if (lottie == null) {
            animLottie = null
            selectedLottie = null
            previewLottie = null
            return
        }
        val fromPreview = lottie === previewLottie
        previewLottie?.takeIf { it !== lottie }?.let { resetLottieToBase(it) }
        previewLottie = null
        selectedLottie?.takeIf { it !== lottie }?.let { resetLottieToBase(it) }
        selectedLottie = lottie
        if (fromPreview) {
            animLottie = null
            return
        }
        animLottie = lottie
        animStartMs = android.os.SystemClock.elapsedRealtime()
        animDurationMs = queryLottieDuration(lottie)
        logger?.invoke("tab animation: index=$index duration=${animDurationMs.toInt()}ms")
    }

    /** 每帧调用（主线程）：按墙钟推进进入动画进度到 [0,1]，结束后停在末帧（选中态）。
     *  原生动画器在跑时让位（容器可见但隐形后可见性恢复会自动播放原生动画）。 */
    fun driveTabAnimation() {
        val lottie = animLottie ?: return
        if (isLottieAnimating(lottie)) return
        val progress = ((android.os.SystemClock.elapsedRealtime() - animStartMs) / animDurationMs)
            .coerceIn(0f, 1f)
        setLottieProgress(lottie, progress)
        if (progress >= 1f) animLottie = null
    }

    /**
     * 拖动液泡的悬停预览：原选中 tab 立即回归未选中，悬停 tab 预览选中；拖回原 tab 时
     * 完全还原。同一目标去重。主线程调用。
     *
     * LOTTIE_RESOLVE（商店）：悬停 tab Lottie 直控选中末帧 + 标签 TextView 翻 isSelected
     * （颜色选择器随视图状态切换；子 TextView 翻转为 best-effort，duplicateParentState
     * 的视图可能不生效）。WHOLE_TAB 系（相册/微博）：翻转条目 setChecked（图标选中外观
     * 原生驱动，镜像实时跟随；selectedTabIndex 同步冻结防读数污染）。
     */
    fun previewTabChange(target: Int) {
        if (extractor.descriptor.iconMode != IconMode.LOTTIE_RESOLVE) {
            isTabPreviewActive = true // 预览翻转选中外观期间，适配器吞掉应用的切页副作用
            armActivityWindow()
            previewWholeTab(target)
            return
        }
        val targetLottie = lottieAt(target) ?: return
        if (targetLottie === previewLottie) return
        if (targetLottie === selectedLottie) {
            // 拖回原 tab：复位预览目标并还原选中态
            previewLottie?.let {
                resetLottieToBase(it)
                setTabSelected(tabIndexOfLottie(it), false)
            }
            previewLottie = null
            animLottie = null
            setLottieProgress(targetLottie, 1f)
            setTabSelected(target, true)
            return
        }
        // 悬停新 tab：挂起进行中的进入动画（避免双写进度），复位旧预览与原选中
        animLottie = null
        previewLottie?.let {
            resetLottieToBase(it)
            setTabSelected(tabIndexOfLottie(it), false)
        }
        selectedLottie?.let {
            resetLottieToBase(it)
            setTabSelected(tabIndexOfLottie(it), false)
        }
        setLottieProgress(targetLottie, 1f)
        setTabSelected(target, true)
        previewLottie = targetLottie
    }

    /** 翻转 tab 标签的选中态（TextView 颜色选择器随视图状态切换；best-effort）。 */
    private fun setTabSelected(index: Int, selected: Boolean) {
        val tv = tabViews.getOrNull(index)?.let { extractor.findTextView(it) } ?: return
        runCatching { tv.isSelected = selected }
    }

    // —— WHOLE_TAB（条目级选中态，如相册 VNavMenuItem）拖动预览：直接翻转条目 checked——
    /** 预览起点（原生选中读数冻结值），<0 = 预览未激活。 */
    private var wholeTabPreviewOrigin = -1
    /** 当前悬停预览目标，<0 = 无。 */
    private var wholeTabPreviewTarget = -1
    private val wholeTabSetChecked: java.lang.reflect.Method? = tabViews.firstOrNull()?.javaClass?.let { cls ->
        // 方法名按描述符解析（默认 setChecked）：部分库被方法级混淆（如 vivo 官网 originui
        // 13.3.0.1 的选中外观翻转为 y(boolean)，无 setChecked），适配器按取证声明。
        // 解析失败保持 null（预览静默失效、不崩溃），不回退瞎猜方法名。
        runCatching {
            cls.getMethod(
                extractor.descriptor.itemCheckedSetterMethodName ?: "setChecked",
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull()
    }

    /** WHOLE_TAB 悬停预览：悬停 tab = 动画末帧（选中外观），被拖离的原选中 tab = 动画
     *  首帧（未选中外观），其他经过后离开的 tab = 恢复原静态图标（非首帧）；拖回原 tab
     *  完全还原并结束预览。相册经 setChecked 原生驱动同语义（见 previewIconLottie）。 */
    private fun previewWholeTab(target: Int) {
        if (target == wholeTabPreviewTarget) return
        // 纯反射入口守卫：条目上无 void(boolean) 翻转方法**且**描述符未声明 applier 时
        // 预览无从翻转，放弃（静默降级，不崩溃）。有 applier 的适配器（高德/原子笔记）
        // 经 setTabChecked 走直控通道——守卫必须把 applier 算进去，否则纯 applier 适配器
        // （条目是普通 ViewGroup、无 setChecked 可解析）预览静默失效。
        if (wholeTabSetChecked == null && extractor.descriptor.itemCheckedApplier == null) return
        val origin = wholeTabPreviewOrigin.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                .also { wholeTabPreviewOrigin = it }
        if (target == origin) {
            // 悬停回原 tab：预览保持激活，仅清悬停目标——边界抖动不反复挂载/恢复
            // （那会表现为"动画被反复触发"）；原 tab 恢复保存的原选中静态图（拖离时
            // 被反选换图/挂首帧过，悬停本就该呈选中落定外观，§0.5 A.1）；还原统一在
            // 拖动结束的 endTabPreview() 做（LiquidBottomTabs onDragStopped 回原 tab）。
            // **原 tab 还原+复选必须收在"真实外出过"守卫内**（2.9.47 深色悬停闪烁修复）：
            // 悬停原 tab 期间 wholeTabPreviewTarget 恒 -1，上方同目标去重永不命中，此分支
            // 随每个 drag 事件重入——若无条件 restore+setChecked(origin,true)，对 vivo 选中
            // 外观路径（setChecked 内部驱动选中外观/标签显隐，携带过渡动画）= 动画在推进
            // 前逐事件重启，镜像读到反复归零的中间态 = 液泡下方（恰为光斑/整栏提亮覆盖区）
            // 高频亮暗交替（深色读作"光斑/提亮闪烁"）。无外出经历时原 tab 本就处于选中
            // 落定态，零写入即正确语义。
            if (wholeTabPreviewTarget >= 0) {
                setTabChecked(wholeTabPreviewTarget, false)
                restorePreviewFrame(wholeTabPreviewTarget)
                wholeTabPreviewTarget = -1
                restorePreviewFrame(origin)
                setTabChecked(origin, true)
            }
            return
        }
        val previous = wholeTabPreviewTarget
        if (previous >= 0) {
            // 离开的条目恢复原静态图标（非动画首帧）
            setTabChecked(previous, false)
            restorePreviewFrame(previous)
        } else {
            // 被拖离的原选中条目 = 回到未选中状态：优先适配器注入的应用自身反选换图
            // 语义（换回未选中静态图——动画首帧可能仍带选中观感，不能当未选中外观）；
            // 未注入/失败回退动画首帧（旧行为）。当前 drawable 先存，拖动结束统一还原
            setTabChecked(origin, false)
            if (previewIconUnselected != null) savePreviewDrawable(origin)
            if (previewIconUnselected?.invoke(origin) != true) {
                showPreviewFrame(origin, 0f)
            }
        }
        setTabChecked(target, true)
        showPreviewFrame(target, 1f)
        wholeTabPreviewTarget = target
    }

    /** 帧预览期间被临时换上 Lottie 的 tab 下标 → 原图标 drawable（离开即恢复）。 */
    private val savedPreviewDrawables = android.util.SparseArray<android.graphics.drawable.Drawable>()

    private fun previewIconView(index: Int): android.widget.ImageView? {
        val tabView = tabViews.getOrNull(index) ?: return null
        val iconIdName = extractor.descriptor.elementIds["icon"] ?: return null
        val iconId = tabView.context.resources.getIdentifier(iconIdName, "id", tabView.context.packageName)
        if (iconId == 0) return null
        return tabView.findViewById<View>(iconId) as? android.widget.ImageView
    }

    /** 保存该 tab 图标当前 drawable（[previewIconUnselected] 应用反选换图前调用，
     *  供拖动结束 [endTabPreview] 统一还原；无图标视图则无操作）。 */
    private fun savePreviewDrawable(index: Int) {
        previewIconView(index)?.let { savedPreviewDrawables.put(index, it.drawable) }
    }

    /** WHOLE_TAB 图标帧预览：把适配器钩子提供的 Lottie drawable 临时挂到该 tab 图标
     *  ImageView 上并驱动到指定帧（未选中条目视图上挂的是静态图，不挂载帧变化无法被
     *  镜像）。挂载前保存原 drawable 供离开时恢复。钩子未注入或取不到 drawable（应用
     *  更新致反射失效）时静默跳过。 */
    private fun showPreviewFrame(index: Int, progress: Float) {
        val lottie = previewIconLottie?.invoke(index) ?: return
        val iconView = previewIconView(index) ?: return
        if (iconView.drawable !== lottie) {
            savedPreviewDrawables.put(index, iconView.drawable)
            iconView.setImageDrawable(lottie as android.graphics.drawable.Drawable)
        }
        val drive = previewIconProgress
        if (drive != null) drive(lottie, progress) else setLottieProgress(lottie, progress)
    }

    /** 恢复该 tab 的原静态图标（预览离开/拖回时调用；无保存记录则跳过）。 */
    private fun restorePreviewFrame(index: Int) {
        val saved = savedPreviewDrawables.get(index, null) ?: return
        previewIconView(index)?.let { iconView ->
            if (iconView.drawable !== saved) iconView.setImageDrawable(saved)
        }
        savedPreviewDrawables.remove(index)
    }

    /** 拖动结束（LiquidBottomTabs onDragStopped 回到原选中 tab 时调用）：全部条目恢复
     *  原静态图标、原 tab 回选中态，预览状态清零。幂等。 */
    fun endTabPreview() {
        isTabPreviewActive = false // 预览结束：还原的 setChecked 走应用原生语义（已在原页，无切页）
        val origin = wholeTabPreviewOrigin.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        for (i in 0 until savedPreviewDrawables.size()) {
            val index = savedPreviewDrawables.keyAt(i)
            previewIconView(index)?.let { iconView ->
                val saved = savedPreviewDrawables.valueAt(i)
                if (iconView.drawable !== saved) iconView.setImageDrawable(saved)
            }
        }
        savedPreviewDrawables.clear()
        setTabChecked(origin, true)
        wholeTabPreviewOrigin = -1
        wholeTabPreviewTarget = -1
    }

    /** 翻转 WHOLE_TAB 条目的 checked 态：VNavMenuItem.setChecked 驱动图标选中外观与标签
     *  显隐（内部调 setSelected），与应用自身切页同路径，无监听器副作用。best-effort。
     *  描述符声明了 itemCheckedApplier 时（条目选中外观由"数据模型 + setData 重渲染"
     *  驱动、视图上无 void(boolean) 翻转方法，如高德地图）走直控通道。翻转后调用
     *  [previewIconSwapper]（图标随选中态换图的应用，如文件管理 discolor 双 drawable）。
     *
     *  **反射通道同态去重**（2.9.47 深色悬停闪烁修复）：条目 `isSelected` 已等于目标态
     *  时跳过反射翻转——vivo 选中外观/标签过渡路径携带动画（多次真机实证：管家
     *  "悬停时动画不断播放"、健康/闹钟 setChecked 末尾 x()/w() 启动 ~250ms 图标缩放
     *  ValueAnimator），重复同态翻转 = 动画反复重启 = 镜像逐帧亮暗交替（深色下读作
     *  光斑/提亮闪烁）；手机管家 `lastAppliedChecked` 同款先例。去重只跳反射翻转，
     *  [previewIconSwapper] 照常调用（健康=幂等收敛/管家=内部去重/主题=同实例 setIcon
     *  早退，均安全）。若某应用的 setter 不维护 isSelected，去重条件永假 = 天然退化
     *  为现状，无回归面。applier 通道不动（应用自行保证幂等）。 */
    private fun setTabChecked(index: Int, checked: Boolean) {
        val view = tabViews.getOrNull(index) ?: return
        extractor.descriptor.itemCheckedApplier?.let { applier ->
            runCatching { applier(view, checked) }
                .onFailure { logger?.invoke("itemCheckedApplier($index,$checked) failed: $it") }
            runCatching { previewIconSwapper?.invoke(index, checked) }
            return
        }
        val method = wholeTabSetChecked ?: return
        if (view.isSelected != checked) {
            runCatching { method.invoke(view, checked) }
                .onFailure { logger?.invoke("setChecked($index,$checked) failed: $it") }
        }
        runCatching { previewIconSwapper?.invoke(index, checked) }
    }

    private fun setLottieProgress(lottie: Any, progress: Float) {
        runCatching {
            lottie.javaClass.getMethod("setProgress", Float::class.javaPrimitiveType)
                .invoke(lottie, progress)
        }.onFailure { logger?.invoke("setLottieProgress($progress) failed: $it") }
    }

    private fun isLottieAnimating(lottie: View): Boolean = runCatching {
        lottie.javaClass.getMethod("isAnimating").invoke(lottie) as Boolean
    }.getOrDefault(false)

    private fun queryLottieDuration(lottie: View): Float = runCatching {
        (lottie.javaClass.getMethod("getDuration").invoke(lottie) as Int).toFloat()
    }.getOrNull()?.takeIf { it > 0f } ?: 400f

    /** 装配期缺色的延迟补取（应用可能晚于装配才应用主题色，如清除后台后的暖启动加载态）。
     *  当前有缺色且新取到不同值时返回新 (内容色, 强调色)，否则 null。主线程调用。 */
    fun reextractColorsIfMissing(
        contentColor: androidx.compose.ui.graphics.Color?,
        accentColor: androidx.compose.ui.graphics.Color?,
    ): Pair<androidx.compose.ui.graphics.Color?, androidx.compose.ui.graphics.Color?>? {
        if (contentColor != null && accentColor != null) return null
        val extracted = extractor.extractLabelColors(tabViews)
        if (extracted.first == null && extracted.second == null) return null
        if (extracted.first == contentColor && extracted.second == accentColor) return null
        return extracted.also {
            logger?.invoke("glass colors re-extracted: content=${it.first}, accent=${it.second}")
        }
    }

    /** 原视图树是否已被应用重建（tab 视图不再挂在本会话的 Compose 视图下）。 */
    fun isStale(): Boolean {
        val root = composeView.rootView
        return tabViews.any { it.rootView !== root }
    }

    /**
     * 原生底栏"可见但隐形"：VISIBLE + alpha=0 + 递归取消 clickable。应用侧的可见性
     * 守卫（isShown 类）、滚动监听、刷新状态机、Lottie 可见性恢复全部按原生逻辑工作
     * （GONE 会让点击转发的回顶+刷新分支失效——切页路径不检查可见性所以此前未察觉）；
     * 真实触摸穿透到信息流；performClick 转发直接调监听器、不受 clickable 影响。
     * 同时把底栏对无障碍整树隐身（NO_HIDE_DESCENDANTS）：隐形视图本不该被 a11y 遍历，
     * 且商店 TabView 的无障碍节点会解析自身缺失的 string 资源——系统 a11y prefetch
     * 一旦走到它就 Resources$NotFoundException 主线程崩溃（商店启动闪退的根因链）。
     */
    fun suppressNatively() {
        tabContainer.visibility = View.VISIBLE
        tabContainer.alpha = 0f
        tabContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setClickableTree(tabContainer, false)
    }

    /** 防应用刷新重设 clickable：帧循环低频重申。 */
    fun reassertClickSuppression() {
        if (tabContainer.visibility == View.VISIBLE) setClickableTree(tabContainer, false)
    }

    private fun setClickableTree(root: View, clickable: Boolean) {
        root.isClickable = clickable
        (root as? ViewGroup)?.let { group ->
            for (i in 0 until group.childCount) {
                group.getChildAt(i)?.let { setClickableTree(it, clickable) }
            }
        }
    }

    /** 还原：移除 ComposeView 覆盖层、恢复底栏可见性/透明度/可点击（内容视图从未被移动）。主线程调用。 */
    fun restore() {
        try {
            onAuxGlassRestore?.invoke()
        } catch (_: Throwable) {
        }
        try {
            (composeView as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()
        } catch (_: Throwable) {
        }
        try {
            composeView.parent?.let { (it as ViewGroup).removeView(composeView) }
            tabContainer.alpha = 1f
            setClickableTree(tabContainer, true)
            tabContainer.visibility = originalContainerVisibility
            tabContainer.importantForAccessibility = originalContainerImportance
            // decorView 的 ViewTree owner 换回装配前的原值（仅当仍是同一棵装饰视图）
            val decor = originalDecorRef?.get()
            val owners = originalDecorOwners
            if (decor != null && owners != null) {
                decor.setViewTreeLifecycleOwner(owners.first as? androidx.lifecycle.LifecycleOwner)
                decor.setViewTreeViewModelStoreOwner(owners.second as? androidx.lifecycle.ViewModelStoreOwner)
                decor.setViewTreeSavedStateRegistryOwner(owners.third as? androidx.savedstate.SavedStateRegistryOwner)
            }
        } catch (_: Throwable) {
        }
    }
}
