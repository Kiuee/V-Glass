package com.moji.liquidglass.config

import org.json.JSONObject

/**
 * 模块配置：App 设置页生成 JSON，经 LSPosed 远程配置下发，
 * 宿主进程内通过 getRemotePreferences / XSharedPreferences 读取。
 */
data class ModuleConfig(
    /** 玻璃底栏总开关。 */
    val enabled: Boolean = true,
    /** 内容延伸策略：
     *  off     — 不干预列表留白
     *  source  — hook 留白静态方法返回 0（默认；10.4.81.0 为 ya.h.j()，混淆名随版本漂移；
     *            失配自动降级 View.setPadding 平台收口。覆盖层架构下触摸路径原生化，
     *            实测留白清零生效）
     *  applier — j() 保持真实值，改写最终落地 padding 的应用点（libxposed 102 不可改参，
     *            已改为 proceed 后重入落地，见 ModernFacade.mapIntArg；创建期 padding
     *            由装配后扫描兜底，暂不默认） */
    val contentExtend: String = CONTENT_EXTEND_SOURCE,
    /** 玻璃栏最小安全边距（dp）：栏宽 = min(可用宽 − 2×此值, tab 数 × 96dp + 8dp)，
     *  tab 少时栏收窄居中、边距自动放大，此值只作为下限。默认 20dp。 */
    val barMarginDp: Float = 20f,
    /** 小白条上方额外间隙（dp），叠加在导航栏 inset 之上。 */
    val barBottomGapDp: Float = 0f,
    /** 玻璃栏高度（dp），内容行 = 栏高 - 8dp。 */
    val barHeightDp: Float = 60f,
    /** 折射模糊半径（dp）。 */
    val barBlurDp: Float = 5f,
    /** 玻璃表面色不透明度（0~1）：叠在模糊内容上的浅/深底色浓度，0 = 纯透明玻璃。
     *  默认 0.25（真机定稿观感）。 */
    val barSurfaceAlpha: Float = 0.25f,
    /** 色彩饱和度（vibrancy）：模糊内容的鲜艳度，默认 1.5（iOS 同款）。 */
    val barSaturation: Float = 1.5f,
    /** 折射量：玻璃边缘与液泡折射位移幅度（shader refractionAmount）的缩放系数，
     *  0 = 无折射平边。默认 1（100%）。 */
    val barRefractionAmountScale: Float = 1f,
    /** 折射高度：折射带宽（边缘向内发生折射的距离，shader refractionHeight）的缩放系数，
     *  0 = 无折射平边。默认 0.50（50%，真机定稿观感）。 */
    val barRefractionHeightScale: Float = 0.50f,
    /** 是否显示 tab 文字标签。 */
    val barShowLabels: Boolean = true,
    /** 背景采样去重（重内容页优化）：仅适配器按需读取（相册 GL 渲染内容页采样昂贵），
     *  商店等默认路径不受影响。视觉异常时可远程配置改为 false 关闭。 */
    val lightweightSampling: Boolean = true,
    /** 静止降帧 + 深度静止档（默认开）：中央为全部适配器会话开启信号驱动的两档
     *  （活动窗口 1s → 30fps 静止档 3s → 1Hz 帧循环睡眠）——退出 withFrameNanos
     *  常驻订阅（静止 60Hz 不降的根因），显示管线降到面板最低档（LTPO 1Hz）对齐
     *  原生功耗。视频 TextureView 在默认信号类内，播放期间永不降档。视觉异常时
     *  远程配置关闭，回退逐位不变旧行为（每帧恒满档兜底）。 */
    val idleFrameSleep: Boolean = true,
    /** 按应用启用开关（包名 → 是否启用）：总开关开启的前提下逐应用生效；
     *  未单独配置的应用默认启用。关闭后重启对应应用还原。 */
    val appEnabled: Map<String, Boolean> = emptyMap(),
) {
    /** 指定应用是否启用（仅按应用开关；总开关由调用方一并判定）。缺省启用。 */
    fun appEnabledFor(packageName: String): Boolean = appEnabled[packageName] ?: true

    /** 样式页"还原默认"：仅把样式页可调的参数恢复为 [DEFAULT]（边距/间隙/栏高/模糊/
     *  表面色/饱和度/折射量/折射高度/文字标签），保留开关、内容延伸、按应用开关等
     *  非样式字段。 */
    fun withBarStyleDefaults(): ModuleConfig = copy(
        barMarginDp = DEFAULT.barMarginDp,
        barBottomGapDp = DEFAULT.barBottomGapDp,
        barHeightDp = DEFAULT.barHeightDp,
        barBlurDp = DEFAULT.barBlurDp,
        barSurfaceAlpha = DEFAULT.barSurfaceAlpha,
        barSaturation = DEFAULT.barSaturation,
        barRefractionAmountScale = DEFAULT.barRefractionAmountScale,
        barRefractionHeightScale = DEFAULT.barRefractionHeightScale,
        barShowLabels = DEFAULT.barShowLabels,
    )
    fun toJson(): String {
        val root = JSONObject()
        root.put("enabled", enabled)
        root.put("contentExtend", contentExtend)
        root.put("barMarginDp", barMarginDp.toDouble())
        root.put("barBottomGapDp", barBottomGapDp.toDouble())
        root.put("barHeightDp", barHeightDp.toDouble())
        root.put("barBlurDp", barBlurDp.toDouble())
        root.put("barSurfaceAlpha", barSurfaceAlpha.toDouble())
        root.put("barSaturation", barSaturation.toDouble())
        root.put("barRefractionAmountScale", barRefractionAmountScale.toDouble())
        root.put("barRefractionHeightScale", barRefractionHeightScale.toDouble())
        root.put("barShowLabels", barShowLabels)
        root.put("lightweightSampling", lightweightSampling)
        root.put("idleFrameSleep", idleFrameSleep)
        if (appEnabled.isNotEmpty()) {
            val apps = JSONObject()
            for ((pkg, on) in appEnabled) apps.put(pkg, on)
            root.put("appEnabled", apps)
        }
        return root.toString()
    }

    companion object {
        const val PREF_NAME = "liquid_glass"
        const val PREF_KEY = "config_json"

        const val CONTENT_EXTEND_OFF = "off"
        const val CONTENT_EXTEND_SOURCE = "source"
        const val CONTENT_EXTEND_APPLIER = "applier"

        val DEFAULT = ModuleConfig()

        fun fromJson(s: String?): ModuleConfig {
            if (s.isNullOrBlank()) return DEFAULT
            return try {
                val root = JSONObject(s)
                ModuleConfig(
                    enabled = root.optBoolean("enabled", true),
                    // 缺键回退必须与字段默认值一致：旧版本写入的配置没有该键，
                    // 回退 off 会让内容延伸 hook 永不安装（信息流停在小白条上方）
                    contentExtend = root.optString("contentExtend", CONTENT_EXTEND_SOURCE),
                barMarginDp = root.optDouble("barMarginDp", 20.0).toFloat(),
                barBottomGapDp = root.optDouble("barBottomGapDp", 0.0).toFloat(),
                barHeightDp = root.optDouble("barHeightDp", 60.0).toFloat(),
                barBlurDp = root.optDouble("barBlurDp", 6.0).toFloat(),
                barSurfaceAlpha = root.optDouble("barSurfaceAlpha", 0.2).toFloat(),
                barSaturation = root.optDouble("barSaturation", 1.5).toFloat(),
                // 折射强度拆分迁移（一次性）：旧配置仅有 barRefractionScale 键时两项新键
                // 同置旧值（两项同乘 v = 旧共享乘数行为逐位不变）；新配置缺键时各取自身
                // 新默认（量 1.0 / 高度 0.35）。
                barRefractionAmountScale = when {
                    root.has("barRefractionAmountScale") ->
                        root.optDouble("barRefractionAmountScale", 1.0).toFloat()
                    root.has("barRefractionScale") ->
                        root.optDouble("barRefractionScale", 1.0).toFloat()
                    else -> 1f
                },
                barRefractionHeightScale = when {
                    root.has("barRefractionHeightScale") ->
                        root.optDouble("barRefractionHeightScale", 0.35).toFloat()
                    root.has("barRefractionScale") ->
                        root.optDouble("barRefractionScale", 1.0).toFloat()
                    else -> 0.35f
                },
                    barShowLabels = root.optBoolean("barShowLabels", true),
                    lightweightSampling = root.optBoolean("lightweightSampling", true),
                    idleFrameSleep = root.optBoolean("idleFrameSleep", true),
                    appEnabled = root.optJSONObject("appEnabled")?.let { apps ->
                        buildMap {
                            for (key in apps.keys()) put(key, apps.optBoolean(key, true))
                        }
                    } ?: emptyMap(),
                )
            } catch (_: Throwable) {
                DEFAULT
            }
        }
    }
}
