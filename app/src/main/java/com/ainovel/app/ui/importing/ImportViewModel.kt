package com.ainovel.app.ui.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class ImportUiState(
    val fileName: String = "",
    val title: String = "",
    val fullText: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val importedNovelId: Long? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val novelRepository: NovelRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "novel.txt"
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                } ?: throw IllegalStateException("无法读取文件")
                if (text.isBlank()) throw IllegalStateException("文件内容为空")
                _state.value = _state.value.copy(
                    fileName = name,
                    title = name.substringBeforeLast('.').ifBlank { name },
                    fullText = text,
                    loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "读取失败"
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun importAndStart() {
        val s = _state.value
        if (s.title.isBlank() || s.fullText.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val id = novelRepository.importNovel(s.title, s.fullText)
                _state.value = _state.value.copy(importedNovelId = id, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "导入失败"
                )
            }
        }
    }
}
