package com.ainovel.app.data.remote

import com.ainovel.app.data.remote.dto.ChatCompletionRequest
import com.ainovel.app.data.remote.dto.ChatCompletionResponse
import com.ainovel.app.data.remote.dto.ImageGenerationRequest
import com.ainovel.app.data.remote.dto.ImageGenerationResponse
import com.ainovel.app.data.remote.dto.VideoGenerationRequest
import com.ainovel.app.data.remote.dto.VideoGenerationResponse
import com.ainovel.app.data.remote.dto.VideoStatusResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ChatApiService {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletion(
        @Body request: ChatCompletionRequest
    ): ResponseBody

    @POST("images/generations")
    suspend fun generateImage(
        @Body request: ImageGenerationRequest
    ): ImageGenerationResponse

    @POST("videos/generations")
    suspend fun generateVideo(
        @Body request: VideoGenerationRequest
    ): VideoGenerationResponse

    @GET("videos/generations/{id}")
    suspend fun getVideoStatus(
        @Path("id") id: String
    ): VideoStatusResponse
}
