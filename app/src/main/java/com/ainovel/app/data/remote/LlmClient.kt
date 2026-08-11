package com.ainovel.app.data.remote

import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.LlmGateway
import com.ainovel.app.domain.model.ApiConfig
import com.ainovel.app.domain.model.ConfigProvider
import com.ainovel.app.domain.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

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
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(extractErrorMessage(response.code, body))
            }
            parseCompletion(body)
        }
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
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw IOException(extractErrorMessage(response.code, body))
        }

        val source: BufferedSource = response.body?.source() ?: run {
            response.close()
            throw IOException("空响应体")
        }

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                parseDelta(data)?.let { emit(it) }
            }
        } finally {
            response.close()
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
}
