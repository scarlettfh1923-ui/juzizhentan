package com.scarlett.juzizhentan.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.ai.AiClient
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = AiClient.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = AiClient.DEFAULT_MODEL,
    val checking: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
    val saved: Boolean = false,
    val cleared: Boolean = false,
    val showAdvanced: Boolean = false
)

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    var state by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch {
            val cfg = repo.getAiConfig()
            state = state.copy(
                baseUrl = cfg.baseUrl,
                apiKey = cfg.apiKey,
                model = cfg.model
            )
        }
    }

    fun onBaseUrlChange(v: String) {
        state = state.copy(baseUrl = v, saved = false, testResult = null, error = null)
    }

    fun onApiKeyChange(v: String) {
        state = state.copy(apiKey = v, saved = false, testResult = null, error = null)
    }

    fun onModelChange(v: String) {
        state = state.copy(model = v, saved = false, testResult = null, error = null)
    }

    fun toggleAdvanced() {
        state = state.copy(showAdvanced = !state.showAdvanced)
    }

    fun save() {
        viewModelScope.launch {
            repo.saveAiConfig(state.baseUrl, state.apiKey, state.model)
            state = state.copy(saved = true, error = null)
        }
    }

    fun saveAndTest() {
        viewModelScope.launch {
            state = state.copy(checking = true, error = null, testResult = null, saved = false)
            try {
                repo.saveAiConfig(state.baseUrl, state.apiKey, state.model)
                val msg = repo.testConnection()
                state = state.copy(checking = false, testResult = msg, saved = true)
            } catch (e: Exception) {
                state = state.copy(checking = false, error = e.message ?: "连接失败", saved = true)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repo.clearCacheLocal()
            state = state.copy(cleared = true)
        }
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repo) as T
        }
    }
}
