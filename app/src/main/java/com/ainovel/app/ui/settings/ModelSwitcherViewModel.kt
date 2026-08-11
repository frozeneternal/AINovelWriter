package com.ainovel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.SettingRepository
import com.ainovel.app.domain.agent.LlmGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSwitcherUiState(
    val currentModel: String = "",
    val models: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ModelSwitcherViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val llmGateway: LlmGateway
) : ViewModel() {

    private val _state = MutableStateFlow(ModelSwitcherUiState())
    val state: StateFlow<ModelSwitcherUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val config = settingRepository.getConfig()
        val models = settingRepository.getAvailableModels()
        _state.value = ModelSwitcherUiState(
            currentModel = config.textModel,
            models = models
        )
    }

    fun loadModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val models = llmGateway.listModels()
                settingRepository.saveAvailableModels(models)
                val config = settingRepository.getConfig()
                val newModel = if (models.isNotEmpty()) models.first() else config.textModel
                if (models.isNotEmpty()) {
                    settingRepository.saveConfig(config.copy(textModel = newModel))
                }
                _state.value = ModelSwitcherUiState(
                    currentModel = newModel,
                    models = models
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "获取模型列表失败"
                )
            }
        }
    }

    fun selectModel(model: String) {
        val config = settingRepository.getConfig()
        settingRepository.saveConfig(config.copy(textModel = model))
        _state.value = _state.value.copy(currentModel = model)
    }
}
