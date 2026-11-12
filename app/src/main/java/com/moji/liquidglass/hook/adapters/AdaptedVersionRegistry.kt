package com.moji.liquidglass.hook.adapters

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * 已适配版本集中追加表：应用更新后经实测**无需改动适配代码**的新版本
 * （锚点未漂移、机制未变），直接把 versionName 登记进仓库内
 * `app/src/main/assets/adapted_versions.json` 即可生效，不必逐个编辑适配器文件；
 * 应用页 chip 的已适配判定与展开列表会自动包含。
 *
 * 数据源两层，按包名合并、按序去重：
 * - 内置表：随 APK 打包的 `assets/adapted_versions.json`（[init] 于管理器进程载入，
 *   是 GitHub 仓库内同一文件的 release 快照，作远程不可达时的兜底）；
 * - 远程覆盖表：应用页手动刷新拉取后写入（[setRemoteOverlay]，持久化见
 *   AdaptedVersionsStore；hook 进程不联网也不读本表，恒为空无碍）。
 * 维护工作流：编辑仓库内 JSON → push → 用户端应用页手动刷新，
 * 详见根 MAINTENANCE.md「免适配版本远程登记」小节。
 *
 * 最终已适配列表 = 适配器内 `baselineVersions`（深度逆向适配的基准版本，即主册 §0.6.3
 * 多版本兼容窗口成员——升版适配必须同时保证窗口内全部基线可用；本表免适配版本不计入窗口）
 * + [extraAdaptedVersions] 对应包名的追加版本，按序去重。
 * 若某次升版需要改适配代码，属于深度适配：更新适配器的 baselineVersions
 * 并同步 MAINTENANCE.md 对应章节的「基准版本」行。
 *
 * 包名必须与适配器的 targetPackage 完全一致，未注册包名的条目不生效。
 */
object AdaptedVersionRegistry {

    /** 一张版本表：表级更新时间 + 包名 → versionName 列表。 */
    data class Table(val updatedAt: String, val packages: Map<String, List<String>>)

    private const val ASSET_PATH = "adapted_versions.json"
    private const val SCHEMA = 1
    private const val MAX_PACKAGES = 256
    private const val MAX_VERSIONS_PER_PACKAGE = 64
    private const val MAX_ENTRY_LENGTH = 64

    @Volatile
    private var initialized = false

    /** 内置表（assets），管理器进程 [init] 后可用。 */
    @Volatile
    private var builtin: Map<String, List<String>> = emptyMap()

    /** 远程覆盖表（仅管理器 UI 进程写入，见 AdaptedVersionsStore）。 */
    @Volatile
    private var remoteOverlay: Map<String, List<String>> = emptyMap()

    /** 幂等，Application 与 Activity 均可调用；assets 缺失/损坏时内置表兜底为空。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        builtin = try {
            val text = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseTable(text)?.packages ?: emptyMap()
        } catch (_: IOException) {
            emptyMap()
        }
    }

    /** 应用远程覆盖表；null = 清空，回退到仅内置表。 */
    fun setRemoteOverlay(table: Table?) {
        remoteOverlay = table?.packages ?: emptyMap()
    }

    /**
     * 包名 → 免适配追加 versionName 合并视图（内置 + 远程覆盖，按序去重）。
     * 唯一代码消费点 AppAdapter.adaptedVersions。
     */
    val extraAdaptedVersions: Map<String, List<String>>
        get() {
            if (remoteOverlay.isEmpty()) return builtin
            val merged = LinkedHashMap(builtin)
            for ((pkg, versions) in remoteOverlay) {
                merged[pkg] = merged[pkg]?.plus(versions)?.distinct() ?: versions
            }
            return merged
        }

    /**
     * 严格解析版本表 JSON（内置 assets 与远程拉取共用）：schema 不符、结构不符、
     * 条目超限或类型不符一律返回 null（拒绝脏数据，调用方保留原表不动）；
     * 合法空表（packages 为空对象）视为有效，供远程回退清空覆盖表。
     */
    fun parseTable(text: String): Table? = try {
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA) return null
        val updatedAt = root.optString("updatedAt")
        val packagesJson = root.optJSONObject("packages") ?: return null
        if (packagesJson.length() > MAX_PACKAGES) return null
        val packages = LinkedHashMap<String, List<String>>()
        for (pkg in packagesJson.keys()) {
            val versionsJson = packagesJson.getJSONArray(pkg)
            if (pkg.isBlank() || pkg.length > MAX_ENTRY_LENGTH) return null
            if (versionsJson.length() > MAX_VERSIONS_PER_PACKAGE) return null
            val versions = ArrayList<String>(versionsJson.length())
            for (i in 0 until versionsJson.length()) {
                val version = versionsJson.getString(i)
                if (version.isBlank() || version.length > MAX_ENTRY_LENGTH) return null
                versions.add(version)
            }
            if (versions.isNotEmpty()) packages[pkg] = versions.distinct()
        }
        Table(updatedAt, packages)
    } catch (_: JSONException) {
        null
    }
}
