package com.ainovel.app

import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.LlmGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmGateway : LlmGateway {
    var completeHandler: (String, String, Double, Int) -> String = { _, user, _, _ ->
        "【生成结果】$user"
    }
    var failForSystemPrompt: String? = null

    override suspend fun streamChat(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> {
        failForSystemPrompt?.let {
            if (systemPrompt.contains(it)) throw IllegalStateException("模拟失败")
        }
        val full = completeHandler(systemPrompt, userMessage, temperature, maxTokens)
        // 按字符分块模拟流式输出
        return flow {
            full.chunked(3).forEach { chunk -> emit(chunk) }
        }
    }

    override suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): String {
        failForSystemPrompt?.let {
            if (systemPrompt.contains(it)) throw IllegalStateException("模拟失败")
        }
        return completeHandler(systemPrompt, userMessage, temperature, maxTokens)
    }

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.Success

    override suspend fun listModels(): List<String> = emptyList()
}
