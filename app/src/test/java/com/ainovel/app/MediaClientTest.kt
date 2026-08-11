package com.ainovel.app

import com.ainovel.app.data.remote.MediaClient
import com.ainovel.app.domain.model.ApiConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class MediaClientTest {

    private lateinit var server: MockWebServer
    private lateinit var mediaClient: MediaClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        mediaClient = MediaClient(OkHttpClient.Builder().build()) {
            ApiConfig(
                imageBaseUrl = server.url("/v1").toString(),
                imageApiKey = "img-key",
                imageModel = "dall-e-3",
                videoBaseUrl = server.url("/v1").toString(),
                videoApiKey = "vid-key",
                videoModel = "kling"
            )
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generateImage_withB64Json_decodesBytes() = runTest {
        val fakeBytes = java.util.Base64.getEncoder().encode(byteArrayOf(1, 2, 3, 4))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"b64_json":"${fakeBytes.decodeToString()}"}]}""")
        )
        val image = mediaClient.generateImage("一只猫")
        assertThat(image.bytes).isNotNull()
        assertThat(image.bytes!!.map { it.toInt() }).containsExactly(1, 2, 3, 4).inOrder()
    }

    @Test
    fun generateImage_withUrl_returnsUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"url":"https://example.com/img.png"}]}""")
        )
        val image = mediaClient.generateImage("一只狗")
        assertThat(image.url).isEqualTo("https://example.com/img.png")
    }

    @Test
    fun generateImage_withoutConfig_throws() = runTest {
        val noConfigClient = MediaClient(OkHttpClient.Builder().build()) {
            ApiConfig()
        }
        val exception = runCatching { noConfigClient.generateImage("测试") }.exceptionOrNull()
        assertThat(exception?.message).contains("未配置图片生成")
    }

    @Test
    fun generateVideo_submitsAndReturnsTaskId() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"task-123","status":"queued"}""")
        )
        val taskId = mediaClient.generateVideo("一段视频")
        assertThat(taskId).isEqualTo("task-123")
    }
}
