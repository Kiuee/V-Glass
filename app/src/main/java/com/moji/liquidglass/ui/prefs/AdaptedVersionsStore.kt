package com.moji.liquidglass.ui.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moji.liquidglass.hook.adapters.AdaptedVersionRegistry
import com.moji.liquidglass.ui.AdaptedVersionsRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 免适配版本表远程状态：本地缓存 + 每源验证器持久化 + Compose 状态桥。
 * 独立 SharedPreferences 文件，与外观设置（ManagerPrefs）、模块功能配置（ModuleConfig）
 * 互不相干；init 时把缓存的远程表写回注册表（hook 进程不调用本类）。
 * 应用页以 [overlayRevision] 为 remember key 感知覆盖表变更并重组。
 */
object AdaptedVersionsStore {

    /** 手动刷新三态结果。 */
    sealed interface RefreshResult {
        /** 拉到新表并已生效，[updatedAt] 为表内更新时间（可能为空）。 */
        data class Updated(val updatedAt: String) : RefreshResult
        data object Unchanged : RefreshResult
        data class Failed(val reason: String) : RefreshResult
    }

    private const val PREFS_NAME = "adapted_versions"
    private const val KEY_REMOTE_JSON = "remote_json"
    private const val KEY_LAST_UPDATED = "last_updated"
    private const val KEY_VALIDATORS = "validators"

    private lateinit var prefs: SharedPreferences

    /** 远程覆盖表内容代次：每次应用新表 +1。 */
    var overlayRevision by mutableStateOf(0)
        private set

    /** 最近一次成功拉取的表内 updatedAt（空 = 尚未拉到过）。 */
    var lastUpdated by mutableStateOf("")
        private set

    /** 幂等，Application 与 Activity 均可调用；缓存损坏时静默忽略（内置表仍生效）。 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastUpdated = prefs.getString(KEY_LAST_UPDATED, null) ?: ""
        val table = prefs.getString(KEY_REMOTE_JSON, null)?.let { AdaptedVersionRegistry.parseTable(it) }
        if (table != null) {
            AdaptedVersionRegistry.setRemoteOverlay(table)
            overlayRevision++
        }
    }

    /**
     * 手动刷新：条件请求拉取（见 [AdaptedVersionsRemote.fetch]）。
     * 仅「200 且解析成功且与缓存内容不同」才落盘并应用覆盖表；304/内容一致不动本地。
     */
    suspend fun refresh(context: Context): RefreshResult = withContext(Dispatchers.Main.immediate) {
        if (!::prefs.isInitialized) init(context)
        val cachedJson = prefs.getString(KEY_REMOTE_JSON, null)
        val validators = decodeValidators(prefs.getString(KEY_VALIDATORS, null))
        when (val outcome = AdaptedVersionsRemote.fetch(cachedJson, validators)) {
            is AdaptedVersionsRemote.Outcome.Updated -> {
                prefs.edit()
                    .putString(KEY_REMOTE_JSON, AdaptedVersionsRemote.canonicalJson(outcome.table))
                    .putString(KEY_LAST_UPDATED, outcome.table.updatedAt)
                    .putString(KEY_VALIDATORS, encodeValidators(outcome.validators))
                    .apply()
                AdaptedVersionRegistry.setRemoteOverlay(outcome.table)
                overlayRevision++
                lastUpdated = outcome.table.updatedAt
                RefreshResult.Updated(outcome.table.updatedAt)
            }
            is AdaptedVersionsRemote.Outcome.Unchanged -> {
                prefs.edit().putString(KEY_VALIDATORS, encodeValidators(outcome.validators)).apply()
                RefreshResult.Unchanged
            }
            is AdaptedVersionsRemote.Outcome.Failed -> RefreshResult.Failed(outcome.reason)
        }
    }

    private fun encodeValidators(validators: Map<String, AdaptedVersionsRemote.Validator>): String {
        val root = JSONObject()
        for ((url, validator) in validators) {
            root.put(
                url,
                JSONObject()
                    .put("etag", validator.etag)
                    .put("lastModified", validator.lastModified),
            )
        }
        return root.toString()
    }

    private fun decodeValidators(json: String?): Map<String, AdaptedVersionsRemote.Validator> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val root = JSONObject(json)
            val result = LinkedHashMap<String, AdaptedVersionsRemote.Validator>()
            for (url in root.keys()) {
                val entry = root.optJSONObject(url) ?: continue
                result[url] = AdaptedVersionsRemote.Validator(
                    etag = entry.optString("etag"),
                    lastModified = entry.optString("lastModified"),
                )
            }
            result
        } catch (_: JSONException) {
            emptyMap()
        }
    }
}
