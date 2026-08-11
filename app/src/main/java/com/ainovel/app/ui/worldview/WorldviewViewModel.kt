package com.ainovel.app.ui.worldview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.local.entity.WorldviewEntity
import com.ainovel.app.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorldviewViewModel @Inject constructor(
    private val novelRepository: NovelRepository
) : ViewModel() {

    private val _worldview = MutableStateFlow<WorldviewEntity?>(null)
    val worldview: StateFlow<WorldviewEntity?> = _worldview.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun init(novelId: Long) {
        viewModelScope.launch {
            novelRepository.observeWorldview(novelId).collect { _worldview.value = it }
        }
    }

    fun saveStyleProfile(novelId: Long, text: String) {
        viewModelScope.launch {
            _saving.value = true
            novelRepository.saveAnalysisFields(novelId) { it.copy(styleProfile = text) }
            _saving.value = false
        }
    }
}
