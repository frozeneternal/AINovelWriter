package com.ainovel.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.analysis.AnalysisEvent
import com.ainovel.app.domain.analysis.AnalysisPhase
import com.ainovel.app.domain.usecase.NovelAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisRunState(
    val phase: AnalysisPhase = AnalysisPhase.IDLE,
    val message: String = "",
    val chapterCount: Int = 0,
    val error: String? = null,
    val lastOutput: String = "",
    val completed: Boolean = false
)

@HiltViewModel
class AnalysisRunViewModel @Inject constructor(
    private val analysisUseCase: NovelAnalysisUseCase,
    private val novelRepository: NovelRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalysisRunState())
    val state: StateFlow<AnalysisRunState> = _state.asStateFlow()

    fun start(novelId: Long) {
        viewModelScope.launch {
            val fullText = novelRepository.getImportedText(novelId)?.fullText
            if (fullText == null) {
                _state.value = _state.value.copy(error = "未找到原文，请重新导入")
                return@launch
            }
            _state.value = AnalysisRunState(phase = AnalysisPhase.SPLIT_CHAPTERS, message = "章节切分中…")
            analysisUseCase.analyzeNovel(novelId, fullText).collect { event ->
                when (event) {
                    is AnalysisEvent.PhaseStarted -> _state.value = _state.value.copy(
                        phase = event.phase,
                        message = when (event.phase) {
                            AnalysisPhase.CHARACTERS -> "人物提取中…"
                            AnalysisPhase.WORLDVIEW -> "世界观提取中…"
                            AnalysisPhase.PLOT_STYLE -> "情节与手法分析中…"
                            else -> ""
                        },
                        error = null
                    )
                    is AnalysisEvent.ChaptersSplit -> _state.value = _state.value.copy(
                        chapterCount = event.chapterCount
                    )
                    is AnalysisEvent.PhaseFinished -> _state.value = _state.value.copy(
                        lastOutput = event.output
                    )
                    is AnalysisEvent.Error -> _state.value = _state.value.copy(
                        error = event.message,
                        phase = com.ainovel.app.domain.analysis.AnalysisPhase.FAILED
                    )
                    is AnalysisEvent.Completed -> _state.value = _state.value.copy(
                        phase = AnalysisPhase.COMPLETED,
                        message = event.summary,
                        completed = true
                    )
                }
            }
        }
    }
}
