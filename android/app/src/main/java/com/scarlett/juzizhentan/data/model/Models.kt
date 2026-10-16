package com.scarlett.juzizhentan.data.model

import com.squareup.moshi.JsonClass

enum class VerificationStatus {
    VERIFIED, PARTIAL, UNVERIFIED, AI_ORIGINAL
}

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String = "ok",
    val ai_configured: Boolean = false,
    val search_configured: Boolean = false,
    val search_provider: String = "none",
    val model: String = ""
)

@JsonClass(generateAdapter = true)
data class QuoteSearchRequest(
    val query: String,
    val technique: String? = null,
    val theme: String? = null,
    val limit: Int = 8
)

@JsonClass(generateAdapter = true)
data class QuoteCard(
    val id: String,
    val excerpt: String,
    val work_title: String? = null,
    val author: String? = null,
    val technique_tags: List<String> = emptyList(),
    val recommend_reason: String = "",
    val verification: String = "UNVERIFIED",
    val source_url: String? = null,
    val source_title: String? = null,
    val note: String? = null
) {
    fun verificationEnum(): VerificationStatus =
        runCatching { VerificationStatus.valueOf(verification) }.getOrDefault(VerificationStatus.UNVERIFIED)
}

@JsonClass(generateAdapter = true)
data class QuoteSearchResponse(
    val query: String,
    val results: List<QuoteCard> = emptyList(),
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AnalyzeRequest(
    val excerpt: String,
    val work_title: String? = null,
    val author: String? = null,
    val source_url: String? = null,
    val verification: String = "UNVERIFIED"
)

@JsonClass(generateAdapter = true)
data class AnalysisDimension(
    val key: String,
    val title: String,
    val content: String,
    val evidence: String? = null,
    val certainty: String = "inferred"
)

@JsonClass(generateAdapter = true)
data class AnalyzeResponse(
    val excerpt: String,
    val dimensions: List<AnalysisDimension> = emptyList(),
    val transferable_mechanism: String = "",
    val simplified_version: String? = null,
    val simplified_diff: String? = null
)

@JsonClass(generateAdapter = true)
data class TrainingGenerateRequest(
    val technique: String? = null,
    val theme: String? = null,
    val difficulty: String? = "medium",
    val seed_excerpt: String? = null,
    val avoid_summaries: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CandidateOption(
    val id: String,
    val text: String,
    val function: String = "",
    val suitability: String = "",
    val potential_issue: String = ""
)

@JsonClass(generateAdapter = true)
data class MaterialSlot(
    val id: String,
    val label: String,
    val role: String = "",
    val options: List<CandidateOption> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TrainingLevel(
    val theme: String,
    val training_goal: String,
    val difficulty: String,
    val scenario: String,
    val slots: List<MaterialSlot> = emptyList(),
    val reference_combination: Map<String, String> = emptyMap(),
    val reference_rationale: String = "",
    val common_pitfalls: List<String> = emptyList(),
    val next_difficulty_hint: String = "",
    val summary_fingerprint: String = ""
)

@JsonClass(generateAdapter = true)
data class CritiqueRequest(
    val level: TrainingLevel,
    val selections: Map<String, String>,
    val seed_excerpt: String? = null
)

@JsonClass(generateAdapter = true)
data class SlotComment(
    val slot_id: String? = null,
    val option_id: String? = null,
    val comment: String? = null
)

@JsonClass(generateAdapter = true)
data class CritiqueResponse(
    val combined_text: String = "",
    val overall_effect: String = "",
    val slot_comments: List<SlotComment> = emptyList(),
    val strengths: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
    val swap_suggestions: List<String> = emptyList(),
    val optimized_combination: String = "",
    val mechanism_contrast: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val detail: String? = null)
