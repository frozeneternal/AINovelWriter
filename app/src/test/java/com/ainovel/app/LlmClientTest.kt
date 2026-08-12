package com.ainovel.app

import com.ainovel.app.data.remote.LlmClient
import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.ContentPolicyException
import com.ainovel.app.domain.model.ApiConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        // 取消类测试的 server 端请求可能仍在 setBodyDelay 的 sleep 中，
        // shutdown() 默认 5 秒超时等不到其完成会抛 "Gave up waiting for queue to shut down"，
        // 这是 MockWebServer 的已知时序限制，与断言无关，忽略即可。
        runCatching { server.shutdown() }
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
        assertThat(exception).isInstanceOf(java.io.IOException::class.java)
        assertThat(exception).isNotInstanceOf(ContentPolicyException::class.java)
    }

    @Test
    fun complete_contentPolicyViolation_throwsContentPolicyException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"message":"Your request was rejected by our safety system","code":"content_policy_violation"}}"""
                )
        )
        val exception = runCatching {
            client.complete("system", "user", 0.8, 4000)
        }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ContentPolicyException::class.java)
    }

    @Test
    fun complete_chinesePolicyKeyword_throwsContentPolicyException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"内容含违禁词，已拒绝生成","code":"invalid_request_error"}}""")
        )
        val exception = runCatching {
            client.complete("system", "user", 0.8, 4000)
        }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ContentPolicyException::class.java)
    }

    @Test
    fun streamChat_contentPolicyViolation_throwsContentPolicyException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"sensitive content detected","code":"content_policy_violation"}}""")
        )
        val exception = runCatching {
            client.streamChat("system", "user", 0.8, 4000).toList()
        }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ContentPolicyException::class.java)
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
    fun streamChat_midStreamErrorEvent_throwsContentPolicyException() = runTest {
        // 流式输出中途服务端返回 error 事件（输出到一半触发违禁词审查），
        // 不得静默吞掉：应抛 ContentPolicyException 供上层合规重试
        val streamBody = """
            data: {"choices":[{"delta":{"content":"前"}}]}
            
            data: {"error":{"message":"检测到违禁词，已中断生成","code":"content_policy_violation"}}
            
        """.trimIndent()
        server.enqueue(MockResponse().setBody(streamBody).setHeader("Content-Type", "text/event-stream"))

        val exception = runCatching {
            client.streamChat("system", "user", 0.8, 4000).toList()
        }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ContentPolicyException::class.java)
        assertThat(exception?.message).contains("违禁")
    }

    @Test
    fun streamChat_midStreamErrorEvent_genericError_throwsIOException() = runTest {
        val streamBody = """
            data: {"choices":[{"delta":{"content":"前"}}]}
            
            data: {"error":{"message":"rate limit exceeded","code":"rate_limit"}}
            
        """.trimIndent()
        server.enqueue(MockResponse().setBody(streamBody).setHeader("Content-Type", "text/event-stream"))

        val exception = runCatching {
            client.streamChat("system", "user", 0.8, 4000).toList()
        }.exceptionOrNull()
        assertThat(exception).isInstanceOf(java.io.IOException::class.java)
        assertThat(exception).isNotInstanceOf(ContentPolicyException::class.java)
        assertThat(exception?.message).contains("rate limit")
    }

    @Test
    fun streamChat_cancellation_interruptsBlockingCall() = runBlocking {
        // 服务端永不返回响应，客户端在等待期间取消
        server.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)
        )
        val job = launch {
            runCatching { client.streamChat("system", "user", 0.8, 4000).toList() }
        }
        delay(200)
        job.cancel()
        // 若 call.cancel 生效，join 应在毫秒级返回；5 秒内未返回则断言失败
        kotlinx.coroutines.withTimeout(5000) { job.join() }
        assertThat(job.isCompleted).isTrue()
    }

    @Test
    fun streamChat_cancellation_duringStreaming_interruptsCall() = runBlocking {
        // 响应头立即返回，但 body 延迟 30 秒发送：模拟流式输出过程中点击停止
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n\n")
                .setBodyDelay(3_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        val job = launch {
            runCatching { client.streamChat("system", "user", 0.8, 4000).toList() }
        }
        delay(300)
        job.cancel()
        // 读取阶段也须立即取消：若 runBlockingWithCall 未生效，join 将等 30 秒 body 延迟
        kotlinx.coroutines.withTimeout(5000) { job.join() }
        assertThat(job.isCompleted).isTrue()
    }

    @Test
    fun complete_cancellation_duringBodyRead_interruptsCall() = runBlocking {
        // 响应头立即返回，但 body 延迟 30 秒发送：模拟非流式读取中点停止
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"你好"}}]}""")
                .setBodyDelay(3_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        val job = launch {
            runCatching { client.complete("system", "user", 0.8, 4000) }
        }
        delay(300)
        job.cancel()
        kotlinx.coroutines.withTimeout(5000) { job.join() }
        assertThat(job.isCompleted).isTrue()
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
