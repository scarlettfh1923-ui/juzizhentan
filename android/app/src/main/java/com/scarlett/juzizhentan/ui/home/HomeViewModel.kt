package com.scarlett.juzizhentan.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.local.FavoriteEntity
import com.scarlett.juzizhentan.data.local.TrainingHistoryEntity
import com.scarlett.juzizhentan.data.model.QuoteCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

class HomeViewModel(private val repo: Repository) : ViewModel() {
    val favorites = repo.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var aiConfigured by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            aiConfigured = repo.isAiConfigured()
        }
    }

    fun refreshAiStatus() {
        viewModelScope.launch {
            aiConfigured = repo.isAiConfigured()
        }
    }

    fun favoriteToCard(e: FavoriteEntity): QuoteCard {
        val tags = runCatching {
            val arr = JSONArray(e.techniqueTagsJson)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
        return QuoteCard(
            id = e.id,
            excerpt = e.excerpt,
            work_title = e.workTitle,
            author = e.author,
            technique_tags = tags,
            recommend_reason = e.recommendReason,
            verification = e.verification,
            source_url = e.sourceUrl
        )
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
        }
    }
}
