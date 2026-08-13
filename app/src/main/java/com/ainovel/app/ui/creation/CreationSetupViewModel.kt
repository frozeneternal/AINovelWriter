package com.ainovel.app.ui.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreationSetupState(
    val title: String = "",
    val genre: String = "玄幻",
    val theme: String = "",
    val style: String = "爽文风",
    val chapterCount: Int = 10,
    val chapterWordCount: Int = 0,
    val mode: Boolean = false,
    val isConfigValid: Boolean = false,
    val error: String? = null,
    val submitting: Boolean = false
)

@HiltViewModel
class CreationSetupViewModel @Inject constructor(
    private val novelRepository: NovelRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreationSetupState())
    val state: StateFlow<CreationSetupState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            isConfigValid = settingRepository.getConfig().isTextConfigured
        )
    }

    fun updateTitle(value: String) {
        _state.value = _state.value.copy(title = value, error = null)
    }

    fun updateGenre(value: String) {
        _state.value = _state.value.copy(genre = value)
    }

    fun updateTheme(value: String) {
        _state.value = _state.value.copy(theme = value)
    }

    fun updateStyle(value: String) {
        _state.value = _state.value.copy(style = value)
    }

    fun updateChapterCount(value: Int) {
        _state.value = _state.value.copy(chapterCount = value.coerceIn(1, 100))
    }

    fun updateChapterWordCount(value: Int) {
        _state.value = _state.value.copy(chapterWordCount = value)
    }

    fun updateMode(auto: Boolean) {
        _state.value = _state.value.copy(mode = auto)
    }

    fun start(onCreated: (Long) -> Unit) {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "请输入书名")
            return
        }
        if (s.theme.isBlank()) {
            _state.value = s.copy(error = "请输入主题/核心创意")
            return
        }
        if (!s.isConfigValid) {
            _state.value = s.copy(error = "请先到设置页配置文本 API")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(submitting = true)
            val novelId = novelRepository.createNovel(
                title = s.title.trim(),
                synopsis = s.theme.trim(),
                genre = s.genre,
                totalChapters = s.chapterCount
            )
            novelRepository.saveCreationPrompt(
                novelId = novelId,
                direction = s.style.trim(),
                wordCount = s.chapterWordCount
            )
            _state.value = _state.value.copy(submitting = false)
            onCreated(novelId)
        }
    }
}
