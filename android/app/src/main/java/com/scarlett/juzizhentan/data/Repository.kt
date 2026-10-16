package com.scarlett.juzizhentan.data

import com.scarlett.juzizhentan.data.local.*
import com.scarlett.juzizhentan.data.model.*
import com.scarlett.juzizhentan.data.remote.ApiService
import com.scarlett.juzizhentan.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import retrofit2.HttpException

class Repository(
    private val db: AppDatabase,
    private var api: ApiService,
    initialBaseUrl: String
) {
    private val favorites = db.favoriteDao()
    private val history = db.trainingHistoryDao()
    private val settings = db.settingsDao()

    var baseUrl: String = initialBaseUrl
        private set

    fun observeFavorites(): Flow<List<FavoriteEntity>> = favorites.observeAll()
    fun observeHistory(): Flow<List<TrainingHistoryEntity>> = history.observeRecent()

    suspend fun getBaseUrl(): String =
        settings.get(KEY_BASE_URL) ?: baseUrl

    suspend fun setBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        settings.put(SettingsEntity(KEY_BASE_URL, cleaned))
        baseUrl = cleaned
        api = NetworkModule.createApi(cleaned)
    }

    suspend fun ensureApi(): ApiService {
        val url = getBaseUrl()
        if (url != baseUrl) {
            baseUrl = url
            api = NetworkModule.createApi(url)
        }
        return api
    }

    suspend fun health(): HealthResponse = ensureApi().health()

    suspend fun searchQuotes(req: QuoteSearchRequest): QuoteSearchResponse =
        wrap { ensureApi().searchQuotes(req) }

    suspend fun analyze(req: AnalyzeRequest): AnalyzeResponse =
        wrap { ensureApi().analyze(req) }

    suspend fun generateTraining(req: TrainingGenerateRequest): TrainingLevel {
        val avoid = history.recentFingerprints()
        return wrap {
            ensureApi().generateTraining(req.copy(avoid_summaries = avoid))
        }
    }

    suspend fun critique(req: CritiqueRequest): CritiqueResponse =
        wrap { ensureApi().critique(req) }

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
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            throw ApiException(detail ?: "服务器错误 HTTP ${e.code()}", e.code())
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "网络异常，请检查后端地址与网络连接", null, e)
        }
    }

    companion object {
        const val KEY_BASE_URL = "base_url"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8765"
    }
}

class ApiException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
