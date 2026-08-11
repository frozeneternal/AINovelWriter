package com.ainovel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.app.data.repository.SettingRepository
import com.ainovel.app.domain.agent.ConnectionResult
import com.ainovel.app.domain.agent.LlmGateway
import com.ainovel.app.domain.model.ApiConfig
import com.ainovel.app.domain.model.ModelTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: ApiConfig = ApiConfig(),
    val testing: Boolean = false,
    val testResult: String? = null,
    val saved: Boolean = false,
    val loadingModels: Boolean = false,
    val models: List<String> = emptyList(),
    val modelsError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val llmGateway: LlmGateway
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(config = settingRepository.getConfig()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun updateTextBaseUrl(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(textBaseUrl = value),
            testResult = null
        )
    }

    fun applyTemplate(template: ModelTemplate) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(
                textBaseUrl = template.textBaseUrl,
                textModel = template.textModel,
                imageBaseUrl = template.imageBaseUrl ?: _state.value.config.imageBaseUrl,
                imageModel = template.imageModel ?: _state.value.config.imageModel
            ),
            testResult = null
        )
    }

    fun updateTextApiKey(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(textApiKey = value),
            testResult = null
        )
    }

    fun updateTextModel(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(textModel = value),
            testResult = null
        )
    }

    fun updateTemperature(value: Double) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(textTemperature = value),
            testResult = null
        )
    }

    fun updateImageBaseUrl(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(imageBaseUrl = value),
            testResult = null
        )
    }

    fun updateImageApiKey(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(imageApiKey = value),
            testResult = null
        )
    }

    fun updateImageModel(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(imageModel = value),
            testResult = null
        )
    }

    fun updateVideoBaseUrl(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(videoBaseUrl = value),
            testResult = null
        )
    }

    fun updateVideoApiKey(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(videoApiKey = value),
            testResult = null
        )
    }

    fun updateVideoModel(value: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(videoModel = value),
            testResult = null
        )
    }

    fun loadModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingModels = true, modelsError = null)
            try {
                settingRepository.saveConfig(_state.value.config)
                val models = llmGateway.listModels()
                settingRepository.saveAvailableModels(models)
                _state.value = _state.value.copy(
                    loadingModels = false,
                    models = models,
                    config = if (models.isNotEmpty()) {
                        _state.value.config.copy(textModel = models.first())
                    } else {
                        _state.value.config
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingModels = false,
                    modelsError = e.message ?: "获取模型列表失败"
                )
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true, testResult = null)
            val result = llmGateway.testConnection()
            _state.value = _state.value.copy(
                testing = false,
                testResult = when (result) {
                    is ConnectionResult.Success -> "连接成功"
                    is ConnectionResult.Failure -> "连接失败：${result.message}"
                }
            )
        }
    }

    fun save() {
        settingRepository.saveConfig(_state.value.config)
        _state.value = _state.value.copy(saved = true)
    }

    fun clearSaved() {
        _state.value = _state.value.copy(saved = false)
    }
}
