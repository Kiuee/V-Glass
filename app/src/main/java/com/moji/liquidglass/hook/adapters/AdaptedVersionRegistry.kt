package com.moji.liquidglass.hook.adapters

/**
 * 已适配版本集中追加表：应用更新后经实测**无需改动适配代码**的新版本
 * （锚点未漂移、机制未变），直接把 versionName 追加到对应包名的列表即可生效，
 * 不必逐个编辑适配器文件；应用页 chip 的已适配判定与展开列表会自动包含。
 *
 * 最终已适配列表 = 适配器内 `baselineVersions`（深度逆向适配的基准版本）
 * + 本表对应包名的追加版本，按序去重。
 * 若某次升版需要改适配代码，属于深度适配：更新适配器的 baselineVersions
 * 并同步 MAINTENANCE.md 对应章节的「基准版本」行。
 *
 * 包名必须与适配器的 targetPackage 完全一致，未注册包名的条目不生效。
 */
object AdaptedVersionRegistry {

    /** 包名 → 免适配追加的 versionName 列表（新版本追加到末尾即可）。 */
    val extraAdaptedVersions: Map<String, List<String>> = mapOf(
        // 示例（删除本行注释后按需填写）：
        // "com.bbk.appstore" to listOf("10.5.10.0", "10.5.20.0"),
        // "com.xingin.xhs" to listOf("9.51.0"),

        "com.vivo.gallery" to listOf("10.2.9.2"),
        "com.sina.weibo" to listOf("16.10.1","16.10.2")

    )
}
