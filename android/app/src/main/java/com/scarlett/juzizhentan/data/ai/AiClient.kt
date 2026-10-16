package com.scarlett.juzizhentan.data.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiNotConfiguredException(message: String) : Exception(message)
class AiRequestException(message: String, val httpCode: Int? = null) : Exception(message)

data class AiConfig(
    val baseUrl: String = AiClient.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiClient.DEFAULT_MODEL
) {
    val configured: Boolean get() = apiKey.isNotBlank()
}

class AiClient(
    private val http: OkHttpClient = defaultClient()
) {
    fun chatJson(
        config: AiConfig,
        system: String,
        user: String,
        temperature: Double = 0.7,
        maxTokens: Int = 4096
    ): JSONObject {
        if (!config.configured) {
            throw AiNotConfiguredException("请先在设置页填写 AI API Key（仅存本机）。")
        }
        val base = config.baseUrl.trim().trimEnd('/')
        val url = "$base/chat/completions"
        val truncatedUser = if (user.length > 12000) user.take(12000) else user
        val body = JSONObject()
            .put("model", config.model.ifBlank { DEFAULT_MODEL })
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", truncatedUser))
            )
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("response_format", JSONObject().put("type", "json_object"))

        var lastErr: Exception? = null
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val hint = when (resp.code) {
                            401, 403 -> "API Key 无效或无权限（HTTP ${resp.code}）"
                            429 -> "请求过于频繁，请稍后再试（HTTP 429）"
                            else -> "AI 接口错误 HTTP ${resp.code}: ${text.take(200)}"
                        }
                        throw AiRequestException(hint, resp.code)
                    }
                    val root = JSONObject(text)
                    val content = root
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return extractJsonObject(content)
                }
            } catch (e: AiRequestException) {
                lastErr = e
                if (e.httpCode == 401 || e.httpCode == 403) throw e
            } catch (e: Exception) {
                lastErr = e
            }
            if (attempt < 2) Thread.sleep(400L * (attempt + 1))
        }
        val msg = when (val e = lastErr) {
            is java.net.UnknownHostException -> "无法连接 AI 服务，请检查网络"
            is java.net.SocketTimeoutException -> "AI 请求超时，请稍后重试"
            is javax.net.ssl.SSLException -> "安全连接失败，请检查 Base URL"
            else -> "AI 调用失败: ${e?.message ?: "未知错误"}"
        }
        throw AiRequestException(msg)
    }

    fun testConnection(config: AiConfig): String {
        if (!config.configured) {
            throw AiNotConfiguredException("请先填写 API Key")
        }
        val obj = chatJson(
            config,
            system = "你是连通性检测助手。只输出合法 JSON。",
            user = "请只返回 {\"ok\":true,\"model\":\"你的模型名\"}",
            temperature = 0.0,
            maxTokens = 64
        )
        val ok = obj.optBoolean("ok", false)
        val model = obj.optString("model").ifBlank { config.model }
        if (!ok) throw AiRequestException("连通性检测未通过，请检查 Key 与模型名")
        return "连接成功 · 模型 $model"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

        fun extractJsonObject(raw: String): JSONObject {
            var text = raw.trim()
            if (text.startsWith("```")) {
                text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
                if (text.endsWith("```")) text = text.removeSuffix("```").trim()
            }
            return try {
                JSONObject(text)
            } catch (_: Exception) {
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) JSONObject(text.substring(start, end + 1))
                else throw AiRequestException("AI 返回的 JSON 无法解析")
            }
        }
    }
}
