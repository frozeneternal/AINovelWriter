package com.ainovel.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.local.entity.NovelEntity
import com.ainovel.app.data.repository.AssetRepository
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.usecase.NovelCreationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val novel: NovelEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val generatingCover: Boolean = false,
    val generatingVideo: Boolean = false,
    val snackbarMessage: String? = null,
    val creationRunning: Boolean = false,
    val creationPaused: Boolean = false
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val novelRepository: NovelRepository,
    private val assetRepository: AssetRepository,
    private val creationUseCase: NovelCreationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var novelId: Long = 0L

    fun init(novelId: Long) {
        if (this.novelId != 0L) return
        this.novelId = novelId

        viewModelScope.launch {
            novelRepository.observeNovel(novelId).collect { novel ->
                _uiState.value = _uiState.value.copy(novel = novel)
            }
        }
        viewModelScope.launch {
            novelRepository.observeChapters(novelId).collect { chapters ->
                _uiState.value = _uiState.value.copy(chapters = chapters)
            }
        }
        viewModelScope.launch {
            creationUseCase.observeRunning(novelId).collect { running ->
                _uiState.value = _uiState.value.copy(creationRunning = running)
            }
        }
        viewModelScope.launch {
            creationUseCase.observePaused(novelId).collect { paused ->
                _uiState.value = _uiState.value.copy(creationPaused = paused)
            }
        }
    }

    fun pauseCreation() {
        creationUseCase.pause(novelId)
    }

    fun resumeCreation() {
        creationUseCase.resume(novelId)
    }

    fun generateCover() {
        val novel = _uiState.value.novel ?: return
        val prompt = buildString {
            append("为小说《${novel.title}》创作一幅精美封面插画。")
            append("题材：${novel.genre}。")
            append("故事简介：${novel.synopsis}。")
            append("风格：大气、有质感、适合作为小说封面，竖版构图。")
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(generatingCover = true)
            try {
                val asset = assetRepository.generateCover(novelId, prompt)
                if (asset != null) {
                    novelRepository.getNovel(novelId)?.let { n ->
                        novelRepository.updateNovel(n.copy(coverPath = asset.localPath))
                    }
                    _uiState.value = _uiState.value.copy(
                        generatingCover = false,
                        snackbarMessage = "封面生成完成"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        generatingCover = false,
                        snackbarMessage = "封面生成失败，请检查图片 API 配置"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    generatingCover = false,
                    snackbarMessage = e.message ?: "封面生成失败"
                )
            }
        }
    }

    fun generateVideo() {
        val novel = _uiState.value.novel ?: return
        val prompt = buildString {
            append("根据小说《${novel.title}》创作宣传视频。")
            append("简介：${novel.synopsis}")
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(generatingVideo = true)
            try {
                val asset = assetRepository.generateVideo(novelId, prompt)
                _uiState.value = _uiState.value.copy(
                    generatingVideo = false,
                    snackbarMessage = if (asset != null) "视频生成完成" else "视频生成失败"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    generatingVideo = false,
                    snackbarMessage = e.message ?: "视频生成失败"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
