package com.scarlett.juzizhentan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val excerpt: String,
    val workTitle: String?,
    val author: String?,
    val sourceUrl: String?,
    val verification: String,
    val techniqueTagsJson: String,
    val recommendReason: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "training_history")
data class TrainingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val theme: String,
    val trainingGoal: String,
    val difficulty: String,
    val technique: String?,
    val combinedText: String,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
