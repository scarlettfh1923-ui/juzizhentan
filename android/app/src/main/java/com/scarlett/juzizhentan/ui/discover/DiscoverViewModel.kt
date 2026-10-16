package com.scarlett.juzizhentan.ui.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.ApiException
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.model.QuoteCard
import com.scarlett.juzizhentan.data.model.QuoteSearchRequest
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<QuoteCard> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

class DiscoverViewModel(private val repo: Repository) : ViewModel() {
    var state by mutableStateOf(DiscoverUiState())
        private set

    fun onQueryChange(q: String) { state = state.copy(query = q) }

    fun search(technique: String? = null, theme: String? = null) {
        val q = state.query.ifBlank { theme ?: technique ?: "" }
        if (q.isBlank()) {
            state = state.copy(error = "请输入作品、作者、主题或技巧关键词")
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, message = null)
            try {
                val resp = repo.searchQuotes(QuoteSearchRequest(query = q, technique = technique, theme = theme))
                state = state.copy(
                    loading = false,
                    results = resp.results,
                    message = resp.message,
                    query = q
                )
            } catch (e: ApiException) {
                state = state.copy(loading = false, error = e.message, results = emptyList())
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "搜索失败", results = emptyList())
            }
        }
    }

    fun toggleFavorite(card: QuoteCard) {
        viewModelScope.launch {
            if (repo.isFavorite(card.id)) repo.removeFavorite(card.id) else repo.saveFavorite(card)
        }
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DiscoverViewModel(repo) as T
        }
    }
}
