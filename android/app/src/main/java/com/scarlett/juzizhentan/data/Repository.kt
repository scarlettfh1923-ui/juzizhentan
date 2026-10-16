package com.scarlett.juzizhentan.data

import com.scarlett.juzizhentan.data.ai.AiClient
import com.scarlett.juzizhentan.data.ai.AiConfig
import com.scarlett.juzizhentan.data.ai.AiNotConfiguredException
import com.scarlett.juzizhentan.data.ai.AiRequestException
import com.scarlett.juzizhentan.data.local.*
import com.scarlett.juzizhentan.data.model.*
import com.scarlett.juzizhentan.data.search.DuckDuckGoClient
import com.scarlett.juzizhentan.data.service.QuoteService
import com.scarlett.juzizhentan.data.service.TrainingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class Repository(
    private val db: AppDatabase,
    private val aiClient: AiClient = AiClient(),
    private val searchClient: DuckDuckGoClient = DuckDuckGoClient()
) {
    private val favorites = db.favoriteDao()
    private val history = db.trainingHistoryDao()
    private val settings = db.settingsDao()
    private val quotes = QuoteService(aiClient, searchClient)
    private val training = TrainingService(aiClient)

    fun observeFavorites(): Flow<List<FavoriteEntity>> = favorites.observeAll()
    fun observeHistory(): Flow<List<TrainingHistoryEntity>> = history.observeRecent()

    suspend fun getAiConfig(): AiConfig {
        val base = settings.get(KEY_AI_BASE_URL)?.trim()?.takeIf { it.isNotBlank() }
            ?: AiClient.DEFAULT_BASE_URL
        val key = settings.get(KEY_AI_API_KEY).orEmpty()
        val model = settings.get(KEY_AI_MODEL)?.trim()?.takeIf { it.isNotBlank() }
            ?: AiClient.DEFAULT_MODEL
        return AiConfig(baseUrl = base.trimEnd('/'), apiKey = key, model = model)
    }

    suspend fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        val cleanedBase = baseUrl.trim().trimEnd('/').ifBlank { AiClient.DEFAULT_BASE_URL }
        val cleanedModel = model.trim().ifBlank { AiClient.DEFAULT_MODEL }
        settings.put(SettingsEntity(KEY_AI_BASE_URL, cleanedBase))
        settings.put(SettingsEntity(KEY_AI_API_KEY, apiKey.trim()))
        settings.put(SettingsEntity(KEY_AI_MODEL, cleanedModel))
        // 清除旧后端地址默认值，避免误连 10.0.2.2
        val old = settings.get(KEY_BACKEND_URL)
        if (old != null && (old.contains("10.0.2.2") || old.isBlank())) {
            settings.put(SettingsEntity(KEY_BACKEND_URL, ""))
        }
    }

    suspend fun isAiConfigured(): Boolean = getAiConfig().configured

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        wrap { aiClient.testConnection(getAiConfig()) }
    }

    suspend fun searchQuotes(req: QuoteSearchRequest): QuoteSearchResponse =
        withContext(Dispatchers.IO) {
            wrap { quotes.searchQuotes(getAiConfig(), req) }
        }

    suspend fun analyze(req: AnalyzeRequest): AnalyzeResponse =
        withContext(Dispatchers.IO) {
            wrap { quotes.analyze(getAiConfig(), req) }
        }

    suspend fun generateTraining(req: TrainingGenerateRequest): TrainingLevel =
        withContext(Dispatchers.IO) {
            val avoid = history.recentFingerprints()
            wrap {
                training.generate(getAiConfig(), req.copy(avoid_summaries = avoid))
            }
        }

    suspend fun critique(req: CritiqueRequest): CritiqueResponse =
        withContext(Dispatchers.IO) {
            wrap { training.critique(getAiConfig(), req) }
        }

    suspend fun saveFavorite(card: QuoteCard) {
        favorites.upsert(
            FavoriteEntity(
                id = card.id,
                excerpt = card.excerpt,
                workTitle = card.work_title,
                author = card.author,
                sourceUrl = card.source_url,
                verification = card.verification,
                techniqueTagsJson = JSONArray(card.technique_tags).toString(),
                recommendReason = card.recommend_reason
            )
        )
    }

    suspend fun removeFavorite(id: String) = favorites.delete(id)
    suspend fun isFavorite(id: String) = favorites.exists(id)

    suspend fun saveTrainingHistory(
        level: TrainingLevel,
        combined: String,
        technique: String?
    ) {
        history.insert(
            TrainingHistoryEntity(
                theme = level.theme,
                trainingGoal = level.training_goal,
                difficulty = level.difficulty,
                technique = technique,
                combinedText = combined,
                fingerprint = level.summary_fingerprint.ifBlank { level.theme }
            )
        )
    }

    suspend fun clearCacheLocal() {
        history.clear()
    }

    private suspend fun <T> wrap(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: AiNotConfiguredException) {
            throw ApiException(e.message ?: "请先在设置页填写 AI API Key", 401, e)
        } catch (e: AiRequestException) {
            throw ApiException(e.message ?: "AI 请求失败", e.httpCode, e)
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw ApiException("网络不可用，请检查手机网络连接", null, e)
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException("请求超时，请稍后重试", null, e)
        } catch (e: Exception) {
            throw ApiException(e.message ?: "请求失败", null, e)
        }
    }

    companion object {
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_BACKEND_URL = "base_url" // legacy, unused
    }
}

class ApiException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
