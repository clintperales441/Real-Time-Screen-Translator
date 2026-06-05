package com.mangalens.feature.history.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
	entities = [TranslationEntity::class],
	version = 1,
	exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun translationDao(): TranslationDao
}
