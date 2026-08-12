package com.ainovel.app.ui.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.agent.PipelineEvent
import com.ainovel.app.domain.agent.PipelinePhase
import com.ainovel.app.domain.agent.PipelineState
import com.ainovel.app.domain.model.CreationMode
import com.ainovel.app.domain.usecase.NovelCreationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreationRunViewModel @Inject constructor(
    private val novelRepository: NovelRepository,
    private val creationUseCase: NovelCreationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val _phaseLabel = MutableStateFlow("")
    val phaseLabel: StateFlow<String> = _phaseLabel.asStateFlow()

    private var novelId: Long = 0L
    private var started = false
    private var continuation = false
    private var eventsJob: Job? = null

    fun startIfNeeded(id: Long, isContinuation: Boolean = false) {
        if (started) return
        novelId = id
        continuation = isContinuation
        started = true

        // 若该书已在后台创作中，先用快照恢复进度，再订阅后续事件
        creationUseCase.currentState(id)?.let { _state.value = it }
        eventsJob = viewModelScope.launch {
            creationUseCase.events(id).collect { handleEvent(it) }
        }

        // 后台管线已在运行：只恢复订阅，不重复启动
        if (creationUseCase.isRunning(id)) return

        viewModelScope.launch {
            // 以 useCase 记录的续写标志为准（从详情页"后台创作中"卡片进入时不丢失续写语义）
            val effectiveContinuation = isContinuation || creationUseCase.isContinuationMode(id)
            if (effectiveContinuation) {
                creationUseCase.startContinuationInBackground(id, 5)
            } else {
                val novel = novelRepository.getNovel(id) ?: return@launch
                val existingChapters = novelRepository.countChapters(id)
                // 全部章节已写完则不重复创作，仅展示已完成状态
                if (novel.totalChapters > 0 && existingChapters >= novel.totalChapters) {
                    return@launch
                }
                val startIndex = (existingChapters + 1).coerceAtMost(novel.totalChapters)
                creationUseCase.startPipelineInBackground(
                    novelId = id,
                    title = novel.title,
                    genre = novel.genre,
                    theme = novel.synopsis,
                    style = "爽文风",
                    totalChapters = novel.totalChapters,
                    startChapterIndex = startIndex
                )
            }
        }
    }

    private fun handleEvent(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.StateChanged -> _state.value = event.state
            is PipelineEvent.Token -> _state.value = _state.value.copy(streamingText = _state.value.streamingText + event.text)
            is PipelineEvent.AgentStarted -> _state.value = _state.value.copy(currentAgent = event.agent, streamingText = "")
            is PipelineEvent.AgentFinished -> _state.value = _state.value.copy(streamingText = "")
            is PipelineEvent.Error -> _state.value = _state.value.copy(error = event.message, phase = PipelinePhase.FAILED)
            is PipelineEvent.Completed -> _state.value = _state.value.copy(phase = PipelinePhase.COMPLETED, message = event.summary)
            else -> Unit
        }
        _phaseLabel.value = when (_state.value.phase) {
            PipelinePhase.WORLDVIEW -> "世界观架构师构建设定"
            PipelinePhase.OUTLINE -> "大纲规划师规划结构"
            PipelinePhase.WRITE_CHAPTER -> "章节作者创作第 ${_state.value.chapterIndex}/${_state.value.totalChapters} 章"
            PipelinePhase.CONTINUITY_CHECK -> "连续性编辑校验"
            PipelinePhase.POLISH -> "润色编辑润色"
            PipelinePhase.COMPLETED -> "创作完成"
            PipelinePhase.FAILED -> "创作失败"
            PipelinePhase.CANCELLED -> "已取消"
            PipelinePhase.IDLE -> "准备中"
        }
    }

    fun stop() {
        creationUseCase.cancel(novelId)
        _state.value = _state.value.copy(phase = PipelinePhase.CANCELLED, message = "已停止")
    }

    fun confirm() {
        creationUseCase.confirmStep(novelId)
    }

    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
    }
}
