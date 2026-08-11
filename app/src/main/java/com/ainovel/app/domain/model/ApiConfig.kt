package com.ainovel.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val textBaseUrl: String = "https://api.openai.com/v1",
    val textApiKey: String = "",
    val textModel: String = "gpt-4o-mini",
    val textTemperature: Double = 0.8,
    val textMaxTokens: Int = 4000,
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = "",
    val videoBaseUrl: String = "",
    val videoApiKey: String = "",
    val videoModel: String = "",
    val contextWindow: Int = 8192,
    val recentChaptersInContext: Int = 5
) {
    val isTextConfigured: Boolean
        get() = textApiKey.isNotBlank() && textBaseUrl.isNotBlank() && textModel.isNotBlank()

    val isImageConfigured: Boolean
        get() = imageApiKey.isNotBlank() && imageBaseUrl.isNotBlank()

    val isVideoConfigured: Boolean
        get() = videoApiKey.isNotBlank() && videoBaseUrl.isNotBlank()
}

@Serializable
data class ModelConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.8,
    val maxTokens: Int = 4000
)
