package com.ainovel.app.domain.agent

import kotlinx.coroutines.flow.Flow

interface LlmGateway {
    suspend fun streamChat(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): Flow<String>

    suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int
    ): String

    suspend fun testConnection(): ConnectionResult

    suspend fun listModels(): List<String>
}

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(val message: String) : ConnectionResult
}
