package com.ainovel.app

import com.ainovel.app.data.remote.LlmClient
import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.model.ApiConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class LlmClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LlmClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okHttp = OkHttpClient.Builder().build()
        client = LlmClient(okHttp) {
            ApiConfig(
                textBaseUrl = server.url("/v1").toString(),
                textApiKey = "test-key",
                textModel = "test-model"
            )
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun complete_parsesMessageContent() = runTest {
        val body = """
            {
              "id": "1",
              "choices": [{"index": 0, "message": {"role": "assistant", "content": "你好，世界"}, "finish_reason": "stop"}]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        val result = client.complete("system", "user", 0.8, 4000)
        assertThat(result).isEqualTo("你好，世界")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-key")
        val requestBody = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(requestBody["model"]?.jsonPrimitive?.content).isEqualTo("test-model")
    }

    @Test
    fun complete_httpError_throwsWithStatus() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key","code":"invalid_api_key"}}""")
        )
        val exception = runCatching {
            client.complete("system", "user", 0.8, 4000)
        }.exceptionOrNull()
        assertThat(exception?.message).contains("Invalid API key")
    }

    @Test
    fun streamChat_accumulatesDeltas() = runTest {
        val streamBody = """
            data: {"choices":[{"delta":{"content":"你"}}]}
            
            data: {"choices":[{"delta":{"content":"好"}}]}
            
            data: [DONE]
            
        """.trimIndent()
        server.enqueue(MockResponse().setBody(streamBody).setHeader("Content-Type", "text/event-stream"))

        val chunks = client.streamChat("system", "user", 0.8, 4000).toList()
        assertThat(chunks).containsExactly("你", "好")
    }

    @Test
    fun testConnection_success() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"OK"}}]}""")
        )
        val result = client.testConnection()
        assertThat(result).isEqualTo(ConnectionResult.Success)
    }

    @Test
    fun testConnection_emptyKey_returnsFailure() = runTest {
        val emptyClient = LlmClient(OkHttpClient.Builder().build()) {
            ApiConfig(textBaseUrl = "http://localhost", textApiKey = "")
        }
        val result = emptyClient.testConnection()
        assertThat(result).isInstanceOf(ConnectionResult.Failure::class.java)
    }

    @Test
    fun listModels_parsesModelIds() = runTest {
        val body = """
            {
              "object": "list",
              "data": [
                {"id": "deepseek-chat", "object": "model"},
                {"id": "deepseek-reasoner", "object": "model"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

        val models = client.listModels()
        assertThat(models).containsExactly("deepseek-chat", "deepseek-reasoner")

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/models")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-key")
    }

    @Test
    fun listModels_emptyKey_throws() = runTest {
        val emptyClient = LlmClient(OkHttpClient.Builder().build()) {
            ApiConfig(textBaseUrl = "http://localhost", textApiKey = "")
        }
        val exception = runCatching { emptyClient.listModels() }.exceptionOrNull()
        assertThat(exception?.message).contains("API Key")
    }
}
