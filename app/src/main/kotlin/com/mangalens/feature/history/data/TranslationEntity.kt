package com.mangalens.feature.history.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val sourceText: String,
	val translatedText: String,
	val createdAtMs: Long
)
