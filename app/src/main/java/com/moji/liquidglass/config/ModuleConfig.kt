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
     *  source  — hook g9.h.j() 返回 0（默认；覆盖层架构下触摸路径原生化，实测留白清零生效）
     *  applier — j() 保持真实值，改写最终落地 padding 的应用点（libxposed 102 不可改参，
     *            已改为 proceed 后重入落地，见 ModernFacade.mapIntArg；创建期 padding
     *            由装配后扫描兜底，暂不默认） */
    val contentExtend: String = CONTENT_EXTEND_SOURCE,
    /** 玻璃栏最小安全边距（dp）：栏宽 = min(可用宽 − 2×此值, tab 数 × 96dp + 8dp)，
     *  tab 少时栏收窄居中、边距自动放大，此值只作为下限。默认 16dp。 */
    val barMarginDp: Float = 16f,
    /** 小白条上方额外间隙（dp），叠加在导航栏 inset 之上。 */
    val barBottomGapDp: Float = 0f,
    /** 玻璃栏高度（dp），内容行 = 栏高 - 8dp。 */
    val barHeightDp: Float = 64f,
    /** 折射模糊半径（dp）。 */
    val barBlurDp: Float = 8f,
    /** 是否显示 tab 文字标签。 */
    val barShowLabels: Boolean = true,
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("enabled", enabled)
        root.put("contentExtend", contentExtend)
        root.put("barMarginDp", barMarginDp.toDouble())
        root.put("barBottomGapDp", barBottomGapDp.toDouble())
        root.put("barHeightDp", barHeightDp.toDouble())
        root.put("barBlurDp", barBlurDp.toDouble())
        root.put("barShowLabels", barShowLabels)
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
                    barMarginDp = root.optDouble("barMarginDp", 16.0).toFloat(),
                    barBottomGapDp = root.optDouble("barBottomGapDp", 0.0).toFloat(),
                    barHeightDp = root.optDouble("barHeightDp", 64.0).toFloat(),
                    barBlurDp = root.optDouble("barBlurDp", 8.0).toFloat(),
                    barShowLabels = root.optBoolean("barShowLabels", true),
                )
            } catch (_: Throwable) {
                DEFAULT
            }
        }
    }
}
