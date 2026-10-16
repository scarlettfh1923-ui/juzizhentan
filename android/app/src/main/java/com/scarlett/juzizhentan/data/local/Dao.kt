package com.scarlett.juzizhentan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}

@Dao
interface TrainingHistoryDao {
    @Query("SELECT * FROM training_history ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<TrainingHistoryEntity>>

    @Insert
    suspend fun insert(item: TrainingHistoryEntity): Long

    @Query("SELECT fingerprint FROM training_history ORDER BY createdAt DESC LIMIT 20")
    suspend fun recentFingerprints(): List<String>

    @Query("DELETE FROM training_history")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun clear()
}
