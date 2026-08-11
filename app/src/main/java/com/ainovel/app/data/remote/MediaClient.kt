package com.ainovel.app.data.remote

import com.ainovel.app.data.remote.dto.VideoStatusResponse
import com.ainovel.app.domain.model.ApiConfig
import com.ainovel.app.domain.model.ConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

class MediaClient(
    private val client: OkHttpClient,
    private val configProvider: ConfigProvider
) {

    private val json = Json { ignoreUnknownKeys = true }

    data class GeneratedImage(
        val url: String?,
        val bytes: ByteArray?
    )

    suspend fun generateImage(prompt: String): GeneratedImage = withContext(Dispatchers.IO) {
        val config = configProvider.get()
        if (!config.isImageConfigured) {
            throw IOException("未配置图片生成 API")
        }
        val baseUrl = config.imageBaseUrl.trimEnd('/')
        val payload = buildJson {
            "model" to config.imageModel.ifBlank { "dall-e-3" }
            "prompt" to prompt
            "n" to 1
            "size" to "1024x1024"
        }.toString()
        val body = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/images/generations")
            .addHeader("Authorization", "Bearer ${config.imageApiKey}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("图片生成失败: HTTP ${response.code} ${extractError(responseBody)}")
            }
            parseImageResponse(responseBody)
        }
    }

    suspend fun generateVideo(prompt: String): String = withContext(Dispatchers.IO) {
        val config = configProvider.get()
        if (!config.isVideoConfigured) {
            throw IOException("未配置视频生成 API")
        }
        val baseUrl = config.videoBaseUrl.trimEnd('/')
        val payload = buildJson {
            "model" to config.videoModel.ifBlank { "default" }
            "prompt" to prompt
        }.toString()
        val body = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/videos/generations")
            .addHeader("Authorization", "Bearer ${config.videoApiKey}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("视频生成失败: HTTP ${response.code} ${extractError(responseBody)}")
            }
            parseVideoSubmission(responseBody)
        }
    }

    suspend fun pollVideoStatus(taskId: String, timeoutMillis: Long = 120_000): String = withContext(Dispatchers.IO) {
        val config = configProvider.get()
        if (!config.isVideoConfigured) {
            throw IOException("未配置视频生成 API")
        }
        val baseUrl = config.videoBaseUrl.trimEnd('/')
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val request = Request.Builder()
                .url("$baseUrl/videos/generations/$taskId")
                .addHeader("Authorization", "Bearer ${config.videoApiKey}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val status = parseVideoStatus(responseBody)
                    if (status.status == "succeeded" && !status.output.isNullOrBlank()) {
                        return@withContext status.output
                    }
                    if (status.status == "failed") {
                        throw IOException("视频生成失败: ${status.error ?: "未知错误"}")
                    }
                }
            }
            delay(5_000)
        }
        throw IOException("视频生成超时")
    }

    private fun parseImageResponse(body: String): GeneratedImage {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val data = obj["data"]?.jsonArray?.firstOrNull()?.jsonObject
            val url = data?.get("url")?.jsonPrimitive?.contentOrNull
            val b64 = data?.get("b64_json")?.jsonPrimitive?.contentOrNull
            val bytes = b64?.let { Base64.getDecoder().decode(it) }
            GeneratedImage(url, bytes)
        } catch (e: Exception) {
            GeneratedImage(null, null)
        }
    }

    private fun parseVideoSubmission(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull
            if (id.isNullOrBlank()) {
                val output = obj["output"]?.jsonPrimitive?.contentOrNull
                if (!output.isNullOrBlank()) return output
            }
            id ?: throw IOException("视频任务提交失败：无法解析响应")
        } catch (e: Exception) {
            throw IOException("视频任务提交失败：无法解析响应")
        }
    }

    private fun parseVideoStatus(body: String): VideoStatusResponse {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            VideoStatusResponse(
                id = obj["id"]?.jsonPrimitive?.contentOrNull,
                status = obj["status"]?.jsonPrimitive?.contentOrNull,
                output = obj["output"]?.jsonPrimitive?.contentOrNull,
                error = obj["error"]?.jsonPrimitive?.contentOrNull
            )
        } catch (e: Exception) {
            VideoStatusResponse()
        }
    }

    private fun extractError(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

private class JsonObjectBuilder {
    private val entries = mutableListOf<String>()

    infix fun String.to(value: Any?) {
        entries += "\"$this\": ${valueToJson(value)}"
    }

    fun build(): String = "{${entries.joinToString(",")}}"

    private fun valueToJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"$value\""
    }
}

private fun buildJson(block: JsonObjectBuilder.() -> Unit): String {
    val b = JsonObjectBuilder()
    b.block()
    return b.build()
}
