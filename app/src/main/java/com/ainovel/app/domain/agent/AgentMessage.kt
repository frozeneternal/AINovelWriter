package com.ainovel.app.domain.agent

import com.ainovel.app.domain.model.Role

data class AgentMessage(
    val role: Role,
    val content: String
) {
    companion object {
        fun system(content: String) = AgentMessage(Role.SYSTEM, content)
        fun user(content: String) = AgentMessage(Role.USER, content)
        fun assistant(content: String) = AgentMessage(Role.ASSISTANT, content)
    }
}
