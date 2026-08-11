package com.ainovel.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.8,
    @SerialName("max_tokens") val maxTokens: Int = 4000,
    val stream: Boolean = false
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChoiceDto> = emptyList()
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: MessageDto? = null,
    val delta: MessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class MessageDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024"
)

@Serializable
data class ImageGenerationResponse(
    val created: Long? = null,
    val data: List<ImageDataDto> = emptyList()
)

@Serializable
data class ImageDataDto(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)

@Serializable
data class VideoGenerationRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class VideoGenerationResponse(
    val id: String? = null,
    val status: String? = null,
    val output: String? = null
)

@Serializable
data class VideoStatusResponse(
    val id: String? = null,
    val status: String? = null,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetailDto? = null
)

@Serializable
data class ErrorDetailDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
