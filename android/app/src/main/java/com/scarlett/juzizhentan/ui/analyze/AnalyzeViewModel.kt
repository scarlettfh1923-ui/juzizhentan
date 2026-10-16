package com.scarlett.juzizhentan.ui.analyze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.ApiException
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.model.AnalyzeRequest
import com.scarlett.juzizhentan.data.model.AnalyzeResponse
import com.scarlett.juzizhentan.data.model.QuoteCard
import kotlinx.coroutines.launch

data class AnalyzeUiState(
    val card: QuoteCard? = null,
    val loading: Boolean = false,
    val result: AnalyzeResponse? = null,
    val error: String? = null,
    val showSimplified: Boolean = false
)

class AnalyzeViewModel(
    private val repo: Repository,
    card: QuoteCard?
) : ViewModel() {
    var state by mutableStateOf(AnalyzeUiState(card = card))
        private set

    init { if (card != null) load() }

    fun load() {
        val card = state.card ?: return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val resp = repo.analyze(
                    AnalyzeRequest(
                        excerpt = card.excerpt,
                        work_title = card.work_title,
                        author = card.author,
                        source_url = card.source_url,
                        verification = card.verification
                    )
                )
                state = state.copy(loading = false, result = resp)
            } catch (e: ApiException) {
                state = state.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "分析失败")
            }
        }
    }

    fun toggleSimplified() {
        state = state.copy(showSimplified = !state.showSimplified)
    }

    companion object {
        fun factory(repo: Repository, card: QuoteCard?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnalyzeViewModel(repo, card) as T
        }
    }
}
