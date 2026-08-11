package com.ainovel.app.domain.agent

data class AgentDefinition(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val temperature: Double = 0.8,
    val maxTokens: Int = 4000
)
