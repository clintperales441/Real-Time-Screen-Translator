package com.mangalens.feature.history.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationDao {
	@Query("SELECT * FROM translations ORDER BY createdAtMs DESC")
	fun getAll(): List<TranslationEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insert(entity: TranslationEntity)
}
