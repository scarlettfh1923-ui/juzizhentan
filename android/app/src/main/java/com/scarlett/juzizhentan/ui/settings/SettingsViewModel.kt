package com.scarlett.juzizhentan.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.model.HealthResponse
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = Repository.DEFAULT_BASE_URL,
    val checking: Boolean = false,
    val health: HealthResponse? = null,
    val error: String? = null,
    val cleared: Boolean = false
)

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    var state by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch {
            state = state.copy(baseUrl = repo.getBaseUrl())
        }
    }

    fun onUrlChange(v: String) { state = state.copy(baseUrl = v, cleared = false) }

    fun saveAndCheck() {
        viewModelScope.launch {
            state = state.copy(checking = true, error = null, health = null)
            try {
                repo.setBaseUrl(state.baseUrl)
                val h = repo.health()
                state = state.copy(checking = false, health = h)
            } catch (e: Exception) {
                state = state.copy(checking = false, error = e.message ?: "连接失败")
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
