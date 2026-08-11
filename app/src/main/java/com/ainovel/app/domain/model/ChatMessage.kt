package com.ainovel.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String
)

enum class Role {
    SYSTEM,
    USER,
    ASSISTANT
}

enum class CreationMode {
    AUTO,
    CONFIRM_STEP
}
