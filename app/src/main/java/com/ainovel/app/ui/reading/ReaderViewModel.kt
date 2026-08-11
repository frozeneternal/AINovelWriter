package com.ainovel.app.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.data.repository.AssetRepository
import com.ainovel.app.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val chapters: List<ChapterEntity> = emptyList(),
    val currentIndex: Int = 0,
    val editing: Boolean = false,
    val draftContent: String = "",
    val snackbar: String? = null,
    val generatingIllustration: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val novelRepository: NovelRepository,
    private val assetRepository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var novelId: Long = 0L

    fun init(novelId: Long, startIndex: Int) {
        if (this.novelId != 0L) return
        this.novelId = novelId
        viewModelScope.launch {
            novelRepository.observeChapters(novelId).collect { chapters ->
                _uiState.value = _uiState.value.copy(chapters = chapters, currentIndex = startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
            }
        }
    }

    fun navigateTo(index: Int) {
        _uiState.value = _uiState.value.copy(
            currentIndex = index.coerceIn(0, (_uiState.value.chapters.size - 1).coerceAtLeast(0)),
            editing = false
        )
    }

    fun startEdit() {
        val chapter = currentChapter() ?: return
        _uiState.value = _uiState.value.copy(editing = true, draftContent = chapter.content)
    }

    fun updateDraft(content: String) {
        _uiState.value = _uiState.value.copy(draftContent = content)
    }

    fun saveEdit() {
        val chapter = currentChapter() ?: return
        viewModelScope.launch {
            novelRepository.updateChapterDraft(
                chapterId = chapter.id,
                title = chapter.title,
                content = _uiState.value.draftContent
            )
            _uiState.value = _uiState.value.copy(editing = false, snackbar = "修改已保存")
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editing = false)
    }

    fun generateIllustration() {
        val chapter = currentChapter() ?: return
        viewModelScope.launch {
            val novel = novelRepository.getNovel(novelId)
            val prompt = buildString {
                append("根据以下小说章节内容创作一幅插画。")
                append("\n小说：${novel?.title ?: ""}")
                append("\n章节：${chapter.title}")
                append("\n内容摘要：${chapter.content.take(800)}")
                append("\n风格：精美、电影感、氛围浓厚")
            }
            _uiState.value = _uiState.value.copy(generatingIllustration = true)
            try {
                val asset = assetRepository.generateIllustration(novelId, chapter.id, prompt)
                _uiState.value = _uiState.value.copy(
                    generatingIllustration = false,
                    snackbar = if (asset != null) "插画已生成" else "插画生成失败"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    generatingIllustration = false,
                    snackbar = e.message ?: "插画生成失败"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbar = null)
    }

    private fun currentChapter(): ChapterEntity? {
        val chapters = _uiState.value.chapters
        return chapters.getOrNull(_uiState.value.currentIndex)
    }
}
