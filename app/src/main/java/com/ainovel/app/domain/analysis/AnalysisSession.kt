package com.ainovel.app.domain.analysis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnalysisState(
    val phase: AnalysisPhase = AnalysisPhase.IDLE,
    val message: String = "",
    val chapterCount: Int = 0,
    val error: String? = null
)

sealed interface AnalysisEvent {
    data class PhaseStarted(val phase: AnalysisPhase) : AnalysisEvent
    data class PhaseFinished(val phase: AnalysisPhase, val output: String) : AnalysisEvent
    data class ChaptersSplit(val chapterCount: Int) : AnalysisEvent
    data class Error(val message: String) : AnalysisEvent
    data class Completed(val summary: String) : AnalysisEvent
}

class AnalysisSession(val novelId: Long) {
    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    fun update(transform: (AnalysisState) -> AnalysisState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = AnalysisState()
    }
}
