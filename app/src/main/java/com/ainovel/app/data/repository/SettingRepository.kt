package com.ainovel.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ainovel.app.domain.model.ApiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("novel_settings", Context.MODE_PRIVATE)
    }

    private val configKey = "api_config_enc"

    private val _config = MutableStateFlow<ApiConfig?>(null)
    val config: Flow<ApiConfig> = _config.map { it ?: loadConfig() }

    @Synchronized
    fun loadConfig(): ApiConfig {
        val raw = prefs.getString(configKey, null)
            ?: prefs.getString("api_config", null)
            ?: return ApiConfig()
        return try {
            val plain = cryptoManager.decrypt(raw)
            if (plain == raw && !raw.startsWith("{")) {
                // 旧的明文存储兜底
                json.decodeFromString(ApiConfig.serializer(), raw)
            } else {
                json.decodeFromString(ApiConfig.serializer(), plain)
            }
        } catch (e: Exception) {
            ApiConfig()
        }
    }

    @Synchronized
    fun saveConfig(config: ApiConfig) {
        val raw = json.encodeToString(ApiConfig.serializer(), config)
        val encrypted = cryptoManager.encrypt(raw)
        prefs.edit { putString(configKey, encrypted) }
        _config.value = config
    }

    @Synchronized
    fun getConfig(): ApiConfig = _config.value ?: loadConfig()

    private val modelsKey = "available_models"

    fun getAvailableModels(): List<String> {
        val raw = prefs.getString(modelsKey, null) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAvailableModels(models: List<String>) {
        val raw = json.encodeToString(ListSerializer(String.serializer()), models)
        prefs.edit { putString(modelsKey, raw) }
    }

    fun exportDataDir(): File = File(context.filesDir, "export").apply { mkdirs() }

    fun clearSettings() {
        prefs.edit {
            remove(configKey)
            remove("api_config")
        }
        _config.value = null
    }
}
