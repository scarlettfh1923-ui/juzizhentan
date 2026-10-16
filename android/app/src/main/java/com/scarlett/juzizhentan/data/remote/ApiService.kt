package com.scarlett.juzizhentan.data.remote

import com.scarlett.juzizhentan.data.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/quotes/search")
    suspend fun searchQuotes(@Body body: QuoteSearchRequest): QuoteSearchResponse

    @POST("api/quotes/analyze")
    suspend fun analyze(@Body body: AnalyzeRequest): AnalyzeResponse

    @POST("api/training/generate")
    suspend fun generateTraining(@Body body: TrainingGenerateRequest): TrainingLevel

    @POST("api/training/critique")
    suspend fun critique(@Body body: CritiqueRequest): CritiqueResponse
}
