package com.moji.liquidglass.ui

import com.moji.liquidglass.hook.adapters.AdaptedVersionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 免适配版本表远程拉取器（仅管理器 UI 进程，应用页手动刷新触发）：
 * - 回退链按序尝试，任一源成功即止；
 * - 条件请求：按源携带 If-None-Match / If-Modified-Since，304 = 无变更零正文；
 *   镜像源若无视条件头回 200，正文规范化后与本地缓存判等，一致同样按无变更处理——
 *   无论哪个源，行为都正确，差别仅在省流量的多少；
 * - 严格解析复用 [AdaptedVersionRegistry.parseTable]，脏响应不入缓存。
 */
internal object AdaptedVersionsRemote {

    /** 单源验证器：上次该源成功响应的缓存校验头。 */
    data class Validator(val etag: String = "", val lastModified: String = "") {
        val isPresent: Boolean get() = etag.isNotEmpty() || lastModified.isNotEmpty()
    }

    /** 拉取结果；[validators] 为应持久化的全量验证器表（成功源已更新，全失败原样带回）。 */
    sealed interface Outcome {
        val validators: Map<String, Validator>

        /** 拉到新表（与本地缓存内容不同）。 */
        data class Updated(
            val table: AdaptedVersionRegistry.Table,
            override val validators: Map<String, Validator>,
        ) : Outcome

        /** 304 或正文与本地缓存一致。 */
        data class Unchanged(override val validators: Map<String, Validator>) : Outcome

        /** 全部源失败，[reason] 为末次失败原因（UI 提示用）。 */
        data class Failed(val reason: String, override val validators: Map<String, Validator>) : Outcome
    }

    /** 免适配版本表所在 GitHub 公开仓库（owner/repo），其余拉取源均由该常量派生。 */
    private const val GH_REPO = "Kiuee/V-Glass"
    private const val GH_BRANCH = "master"
    private const val GH_PATH = "app/src/main/assets/adapted_versions.json"
    private const val RAW_URL = "https://raw.githubusercontent.com/$GH_REPO/$GH_BRANCH/$GH_PATH"

    /** 回退链：jsDelivr CDN（国内可达；分支内容缓存最长 ~12h，purge.jsdelivr.net 可手动刷新）
     *  → raw 直链 → 公共 gh-proxy 镜像（raw 前缀代理，零维护）。 */
    private val REMOTE_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/$GH_REPO@$GH_BRANCH/$GH_PATH",
        RAW_URL,
        "https://gh-proxy.com/$RAW_URL",
        "https://ghfast.top/$RAW_URL",
    )

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_BODY_BYTES = 512 * 1024

    /**
     * 拉取入口：[cachedJson] 为本地缓存规范化 JSON（null = 首次拉取），
     * [validators] 为各源存储的验证器表。
     */
    suspend fun fetch(
        cachedJson: String?,
        validators: Map<String, Validator>,
    ): Outcome = withContext(Dispatchers.IO) {
        var lastReason = "无可用拉取源"
        for (rawUrl in REMOTE_URLS) {
            val host = runCatching { URL(rawUrl).host }.getOrDefault(rawUrl)
            var connection: HttpsURLConnection? = null
            try {
                connection = (URL(rawUrl).openConnection() as HttpsURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    validators[rawUrl]?.takeIf { it.isPresent }?.let {
                        if (it.etag.isNotEmpty()) setRequestProperty("If-None-Match", it.etag)
                        if (it.lastModified.isNotEmpty()) setRequestProperty("If-Modified-Since", it.lastModified)
                    }
                }
                when (val code = connection.responseCode) {
                    HttpsURLConnection.HTTP_NOT_MODIFIED -> return@withContext Outcome.Unchanged(validators)
                    in 200..299 -> {
                        val body = readBody(connection)
                        val table = AdaptedVersionRegistry.parseTable(body)
                        if (table == null) {
                            lastReason = "$host 返回内容格式非法"
                        } else {
                            val nextValidators = validators + (rawUrl to validatorOf(connection))
                            if (cachedJson != null && canonicalJson(table) == cachedJson) {
                                return@withContext Outcome.Unchanged(nextValidators)
                            }
                            return@withContext Outcome.Updated(table, nextValidators)
                        }
                    }
                    else -> lastReason = "$host 响应 HTTP $code"
                }
            } catch (e: Exception) {
                lastReason = "$host ${e.javaClass.simpleName}: ${e.message ?: "连接失败"}"
            } finally {
                connection?.disconnect()
            }
        }
        Outcome.Failed(lastReason, validators)
    }

    /** 版本表规范化序列化：内容判等与缓存写入均以此为基准，与源端排版解耦。 */
    internal fun canonicalJson(table: AdaptedVersionRegistry.Table): String {
        val packages = JSONObject()
        for ((pkg, versions) in table.packages) {
            packages.put(pkg, JSONArray(versions))
        }
        return JSONObject()
            .put("schema", 1)
            .put("updatedAt", table.updatedAt)
            .put("packages", packages)
            .toString()
    }

    /** 有上限读正文：超限抛 IllegalStateException、流错误抛 IOException，均由逐源 catch 记为该源失败。 */
    private fun readBody(connection: HttpsURLConnection): String {
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                check(total <= MAX_BODY_BYTES) { "响应超过 ${MAX_BODY_BYTES / 1024}KB 上限" }
                output.write(buffer, 0, read)
            }
            return output.toString("UTF-8")
        }
    }

    private fun validatorOf(connection: HttpsURLConnection): Validator = Validator(
        etag = connection.getHeaderField("ETag").orEmpty(),
        lastModified = connection.getHeaderField("Last-Modified").orEmpty(),
    )
}
