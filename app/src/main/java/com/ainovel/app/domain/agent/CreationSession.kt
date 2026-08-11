package com.ainovel.app.domain.agent

import com.ainovel.app.domain.model.CreationMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PipelinePhase {
    IDLE,
    WORLDVIEW,
    OUTLINE,
    WRITE_CHAPTER,
    CONTINUITY_CHECK,
    POLISH,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class PipelineState(
    val phase: PipelinePhase = PipelinePhase.IDLE,
    val currentAgent: AgentDefinition? = null,
    val agentIndex: Int = 0,
    val agentCount: Int = 0,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val message: String = "",
    val streamingText: String = "",
    val waitingConfirm: Boolean = false,
    val error: String? = null
)

sealed interface PipelineEvent {
    data class StateChanged(val state: PipelineState) : PipelineEvent
    data class Token(val text: String) : PipelineEvent
    data class AgentStarted(val agent: AgentDefinition) : PipelineEvent
    data class AgentFinished(val agent: AgentDefinition, val output: String) : PipelineEvent
    data class ChapterGenerated(val chapterIndex: Int, val title: String, val content: String) : PipelineEvent
    data class ContinuityResult(val issues: List<String>, val correctedText: String) : PipelineEvent
    data class StepConfirmRequested(val agent: AgentDefinition, val output: String) : PipelineEvent
    data class Error(val message: String) : PipelineEvent
    data class Completed(val summary: String) : PipelineEvent
}

class CreationSession(
    val novelId: Long,
    val mode: CreationMode
) {
    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PipelineEvent> = _events.asSharedFlow()

    private var _isActive: Boolean = false
    val isActive: Boolean get() = _isActive

    fun markActive() {
        _isActive = true
    }

    fun update(transform: (PipelineState) -> PipelineState) {
        _state.value = transform(_state.value)
    }

    suspend fun emit(event: PipelineEvent) {
        _events.emit(event)
    }

    fun reset() {
        _state.value = PipelineState()
        _isActive = false
    }
}
