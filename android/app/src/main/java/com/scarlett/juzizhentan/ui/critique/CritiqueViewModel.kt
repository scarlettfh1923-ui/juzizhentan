package com.scarlett.juzizhentan.ui.critique

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.ApiException
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.model.CritiqueRequest
import com.scarlett.juzizhentan.data.model.CritiqueResponse
import com.scarlett.juzizhentan.data.model.TrainingLevel
import kotlinx.coroutines.launch

data class CritiqueUiState(
    val loading: Boolean = true,
    val result: CritiqueResponse? = null,
    val error: String? = null
)

class CritiqueViewModel(
    private val repo: Repository,
    private val level: TrainingLevel?,
    private val selections: Map<String, String>,
    private val seed: String?,
    private val technique: String?
) : ViewModel() {
    var state by mutableStateOf(CritiqueUiState())
        private set

    init { load() }

    fun load() {
        val lvl = level
        if (lvl == null) {
            state = CritiqueUiState(loading = false, error = "没有可讲评的关卡数据，请返回重新训练。")
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val resp = repo.critique(CritiqueRequest(level = lvl, selections = selections, seed_excerpt = seed))
                repo.saveTrainingHistory(lvl, resp.combined_text, technique)
                state = state.copy(loading = false, result = resp)
            } catch (e: ApiException) {
                state = state.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "讲评失败")
            }
        }
    }

    companion object {
        fun factory(
            repo: Repository,
            level: TrainingLevel?,
            selections: Map<String, String>,
            seed: String?,
            technique: String?
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CritiqueViewModel(repo, level, selections, seed, technique) as T
        }
    }
}
