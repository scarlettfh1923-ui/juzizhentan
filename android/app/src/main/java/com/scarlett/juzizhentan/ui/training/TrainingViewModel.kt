package com.scarlett.juzizhentan.ui.training

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scarlett.juzizhentan.data.ApiException
import com.scarlett.juzizhentan.data.Repository
import com.scarlett.juzizhentan.data.model.TrainingGenerateRequest
import com.scarlett.juzizhentan.data.model.TrainingLevel
import kotlinx.coroutines.launch

data class TrainingUiState(
    val choosing: Boolean = true,
    val technique: String? = null,
    val theme: String? = null,
    val difficulty: String = "medium",
    val seed: String? = null,
    val loading: Boolean = false,
    val level: TrainingLevel? = null,
    val slotIndex: Int = 0,
    val selections: Map<String, String> = emptyMap(),
    val error: String? = null
)

class TrainingViewModel(
    private val repo: Repository,
    initialTechnique: String?,
    initialTheme: String?,
    seed: String?,
    random: Boolean
) : ViewModel() {
    var state by mutableStateOf(
        TrainingUiState(
            choosing = !random && initialTechnique.isNullOrBlank() && seed.isNullOrBlank(),
            technique = initialTechnique,
            theme = initialTheme,
            seed = seed
        )
    )
        private set

    init {
        if (random || !seed.isNullOrBlank() || !initialTechnique.isNullOrBlank()) {
            generate()
        }
    }

    fun setTechnique(t: String?) { state = state.copy(technique = t) }
    fun setTheme(t: String?) { state = state.copy(theme = t) }
    fun setDifficulty(d: String) { state = state.copy(difficulty = d) }

    fun start() {
        state = state.copy(choosing = false)
        generate()
    }

    fun generate() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, level = null, slotIndex = 0, selections = emptyMap())
            try {
                val level = repo.generateTraining(
                    TrainingGenerateRequest(
                        technique = state.technique,
                        theme = state.theme,
                        difficulty = state.difficulty,
                        seed_excerpt = state.seed
                    )
                )
                state = state.copy(loading = false, level = level)
            } catch (e: ApiException) {
                state = state.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "生成失败")
            }
        }
    }

    fun select(slotId: String, optionId: String) {
        val next = state.selections.toMutableMap()
        if (next[slotId] == optionId) next.remove(slotId) else next[slotId] = optionId
        state = state.copy(selections = next)
    }

    fun nextSlot() {
        val level = state.level ?: return
        if (state.slotIndex < level.slots.lastIndex) {
            state = state.copy(slotIndex = state.slotIndex + 1)
        }
    }

    fun prevSlot() {
        if (state.slotIndex > 0) state = state.copy(slotIndex = state.slotIndex - 1)
    }

    fun previewText(): String {
        val level = state.level ?: return ""
        return level.slots.mapNotNull { slot ->
            val oid = state.selections[slot.id] ?: return@mapNotNull null
            slot.options.find { it.id == oid }?.text
        }.joinToString("")
    }

    fun allSelected(): Boolean {
        val level = state.level ?: return false
        return level.slots.all { state.selections.containsKey(it.id) }
    }

    companion object {
        fun factory(
            repo: Repository,
            technique: String?,
            theme: String?,
            seed: String?,
            random: Boolean
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrainingViewModel(repo, technique, theme, seed, random) as T
        }
    }
}
