package com.ainovel.app.data.remote

import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.ContentPolicyException
import com.ainovel.app.domain.agent.LlmGateway
import com.ainovel.app.domain.model.ApiConfig
import com.ainovel.app.domain.model.ConfigProvider
import com.ainovel.app.domain.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LlmClient(
    private val client: OkHttpClient,
    private val configProvider: ConfigProvider
) : LlmGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun streamChat(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> = flow {
        val config = configProvider.get().let {
            ModelConfig(
                baseUrl = it.textBaseUrl.trimEnd('/'),
                apiKey = it.textApiKey,
                model = it.textModel,
                temperature = temperature,
                maxTokens = maxTokens
            )
        }
        if (config.apiKey.isNullOrEmpty()) {
            throw IOException("未配置文本模型 API Key")
        }
        val request = buildChatRequest(config, systemPrompt, userMessage, stream = true)
        emitStream(request, config)
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        val config = configProvider.get().let {
            ModelConfig(
                baseUrl = it.textBaseUrl.trimEnd('/'),
                apiKey = it.textApiKey,
                model = it.textModel,
                temperature = temperature,
                maxTokens = maxTokens
            )
        }
        if (config.apiKey.isNullOrEmpty()) {
            throw IOException("未配置文本模型 API Key")
        }
        val request = buildChatRequest(config, systemPrompt, userMessage, stream = false)
        val call = client.newCall(request)
        val response = awaitResponse(call)
        response.use {
            if (!it.isSuccessful) {
                throw buildError(it.code, runBlockingWithCall(call) { it.body?.string().orEmpty() })
            }
            val body = runBlockingWithCall(call) { it.body?.string().orEmpty() }
            parseCompletion(body)
        }
    }

    /**
     * 可取消的 OkHttp 异步请求：协程取消时同步 [Call.cancel]，中断阻塞网络调用，
     * 使"停止生成/暂停"能立即生效而不是等网络超时。
     */
    private suspend fun awaitResponse(call: Call): Response =
        suspendCancellableCoroutine { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isCancelled) {
                        response.close()
                        return
                    }
                    cont.resume(response)
                }
            })
            cont.invokeOnCancellation { call.cancel() }
        }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            val config = configProvider.get().let {
                ModelConfig(it.textBaseUrl.trimEnd('/'), it.textApiKey, it.textModel)
            }
            if (config.apiKey.isNullOrEmpty()) {
                return@withContext ConnectionResult.Failure("请先填写 API Key")
            }
            val request = buildChatRequest(
                config,
                "你是连接测试助手，请只回复：OK",
                "ping",
                stream = false
            )
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) ConnectionResult.Success
                else ConnectionResult.Failure(
                    extractErrorMessage(response.code, response.body?.string().orEmpty())
                )
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.message ?: "连接失败")
        }
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val config = configProvider.get()
        if (config.textApiKey.isNullOrEmpty()) {
            throw IOException("请先填写 API Key")
        }
        val baseUrl = config.textBaseUrl.trimEnd('/').removeSuffix("/chat/completions")
        val request = Request.Builder()
            .url("$baseUrl/models")
            .addHeader("Authorization", "Bearer ${config.textApiKey}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(extractErrorMessage(response.code, body))
            }
            parseModelList(body)
        }
    }

    private fun parseModelList(body: String): List<String> {
        return try {
            val obj = json.parseToJsonElement(body) as JsonObject
            val data = obj["data"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            data.mapNotNull { element ->
                (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildChatRequest(
        config: ModelConfig,
        systemPrompt: String,
        userMessage: String,
        stream: Boolean
    ): okhttp3.Request {
        val payload = buildJsonObject {
            put("model", config.model)
            put("stream", stream)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    }
                )
            }
        }
        val url = buildUrl(config.baseUrl)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
    }

    private fun buildUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private suspend fun FlowCollector<String>.emitStream(request: okhttp3.Request, config: ModelConfig) {
        val call = client.newCall(request)
        val response = awaitResponse(call)
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw buildError(response.code, body)
        }

        val source: BufferedSource = response.body?.source() ?: run {
            response.close()
            throw IOException("空响应体")
        }

        try {
            while (true) {
                val line = runBlockingWithCall(call) { source.readUtf8Line() } ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                // 流式响应中途可能携带 error 事件（如输出到一半触发违禁词审查），
                // 不能静默跳过，否则输出被截断且无法触发合规重试
                val error = parseStreamError(data)
                if (error != null) throw buildError(response.code, error)
                parseDelta(data)?.let { emit(it) }
            }
        } finally {
            response.close()
        }
    }

    /**
     * 在阻塞式网络读取（readUtf8Line/body.string）期间把协程取消同步绑定到 [Call.cancel]。
     *
     * 关键：阻塞读必须 dispatch 到独立 IO 线程执行，而 continuation 立即挂起。
     * 若直接在 [suspendCancellableCoroutine] 的 block 内同步执行阻塞读，
     * 协程没有真正挂起，取消无法注入，invokeOnCancellation 不会触发。
     * 本实现保证取消瞬间同步触发 [Call.cancel]，中断底层阻塞读，
     * 使"停止生成/暂停"在流式输出或完整响应读取过程中立即生效。
     *
     * 取消引发的 IOException（Socket closed）被转回 [CancellationException]，
     * 避免被上层误判为生成失败。
     */
    private suspend fun <T> runBlockingWithCall(
        call: Call,
        block: () -> T
    ): T = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        Dispatchers.IO.dispatch(Dispatchers.IO) {
            try {
                val result = block()
                if (cont.isCancelled) {
                    cont.resumeWithException(CancellationException())
                } else {
                    cont.resume(result)
                }
            } catch (e: Throwable) {
                if (cont.isCancelled) {
                    cont.resumeWithException(CancellationException())
                } else {
                    cont.resumeWithException(e)
                }
            }
        }
    }

    private fun parseDelta(data: String): String? {
        return try {
            val obj = json.parseToJsonElement(data) as JsonObject
            val choices = obj["choices"]?.let { (it as? kotlinx.serialization.json.JsonArray) }
            val choice = choices?.firstOrNull() ?: return null
            val delta = (choice as JsonObject)["delta"] as? JsonObject ?: return null
            delta["content"]?.let { it.jsonPrimitive.contentOrNull }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析流式响应中的 error 事件（SSE data 载荷内含 "error" 字段）。
     * 返回完整的 error 载荷字符串供 [buildError] 识别违禁词审查；
     * 非 error 事件返回 null。
     */
    private fun parseStreamError(data: String): String? {
        return try {
            val obj = json.parseToJsonElement(data) as JsonObject
            val error = obj["error"] as? JsonObject ?: return null
            buildJsonObject {
                put("error", error)
            }.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCompletion(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body) as JsonObject
            val choices = obj["choices"] as kotlinx.serialization.json.JsonArray
            val message = choices.firstOrNull()?.let { (it as JsonObject)["message"] as? JsonObject }
                ?: return ""
            message["content"]?.let { it.jsonPrimitive.contentOrNull } ?: ""
        } catch (e: Exception) {
            body
        }
    }

    private fun extractErrorMessage(code: Int, body: String): String {
        return try {
            val obj = json.parseToJsonElement(body) as JsonObject
            val error = obj["error"] as? JsonObject
            error?.get("message")?.jsonPrimitive?.contentOrNull
                ?: error?.get("code")?.jsonPrimitive?.contentOrNull
                ?: "HTTP $code"
        } catch (e: Exception) {
            "HTTP $code"
        }
    }

    /**
     * 区分内容合规违规错误与普通错误。
     * 命中违禁词/敏感内容审查（OpenAI 兼容 content_policy_violation，或
     * 常见中文"敏感/违规/不合规"措辞）时抛 [ContentPolicyException]，
     * 其余错误仍抛 [IOException]，供上层自动追加合规指令后重试。
     */
    private fun buildError(code: Int, body: String): Exception {
        val message = extractErrorMessage(code, body)
        return if (isContentPolicyViolation(body)) {
            ContentPolicyException(message)
        } else {
            IOException(message)
        }
    }

    private fun isContentPolicyViolation(body: String): Boolean {
        val lower = body.lowercase()
        val policyCodes = listOf(
            "content_policy_violation",
            "content_policy",
            "content_filter",
            "policy_violation",
            "inappropriate_content",
            "unsafe_content",
            "moderation"
        )
        if (policyCodes.any { lower.contains(it) }) return true
        val policyKeywords = listOf(
            "敏感词", "敏感内容", "违禁", "违规内容", "不合规内容",
            "sensitive content", "inappropriate", "unsafe content",
            "violated our policy", "safety system"
        )
        return policyKeywords.any { lower.contains(it) }
    }
}
