package com.ainovel.app

import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.ContentPolicyException
import com.ainovel.app.domain.agent.LlmGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmGateway : LlmGateway {
    var completeHandler: (String, String, Double, Int) -> String = { _, user, _, _ ->
        "【生成结果】$user"
    }
    var failForSystemPrompt: String? = null
    var contentPolicyFailForSystemPrompt: String? = null
    var contentPolicyFailRemaining: Int = 1
    var contentPolicyErrorMessage: String = "内容因安全审核被拒绝"
    var refusalForSystemPrompt: String? = null
    var refusalRemaining: Int = 1
    var refusalResponse: String = "抱歉，我无法涉足这番构想，也无法生成此类内容。若您另有合乎规范的虚构世界构思，我仍乐于执笔，共赴一座崭新的故事。"
    val recordedUserMessages: MutableList<String> = mutableListOf()
    val recordedSystemPrompts: MutableList<String> = mutableListOf()

    private fun maybeFailContentPolicy(systemPrompt: String) {
        contentPolicyFailForSystemPrompt?.let {
            if (systemPrompt.contains(it)) {
                if (contentPolicyFailRemaining > 0) {
                    contentPolicyFailRemaining--
                    throw ContentPolicyException(contentPolicyErrorMessage)
                }
            }
        }
    }

    /** 模拟模型返回"拒绝生成"话术（HTTP 200 但正文是拒绝措辞），用于验证 orchestrator 的拒绝话术检测 */
    private fun maybeReturnRefusal(systemPrompt: String): String? {
        refusalForSystemPrompt?.let {
            if (systemPrompt.contains(it)) {
                if (refusalRemaining > 0) {
                    refusalRemaining--
                    return refusalResponse
                }
            }
        }
        return null
    }

    override suspend fun streamChat(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> {
        recordedSystemPrompts += systemPrompt
        recordedUserMessages += userMessage
        failForSystemPrompt?.let {
            if (systemPrompt.contains(it)) throw IllegalStateException("模拟失败")
        }
        maybeFailContentPolicy(systemPrompt)
        maybeReturnRefusal(systemPrompt)?.let { refusal ->
            return flow {
                refusal.chunked(3).forEach { chunk -> emit(chunk) }
            }
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
        recordedSystemPrompts += systemPrompt
        recordedUserMessages += userMessage
        failForSystemPrompt?.let {
            if (systemPrompt.contains(it)) throw IllegalStateException("模拟失败")
        }
        maybeFailContentPolicy(systemPrompt)
        maybeReturnRefusal(systemPrompt)?.let { return it }
        return completeHandler(systemPrompt, userMessage, temperature, maxTokens)
    }

    override suspend fun testConnection(): ConnectionResult = ConnectionResult.Success

    override suspend fun listModels(): List<String> = emptyList()
}
