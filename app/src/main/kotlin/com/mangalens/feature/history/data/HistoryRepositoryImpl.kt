package com.mangalens.feature.history.data

import com.mangalens.feature.history.domain.HistoryRepository
import com.mangalens.feature.translator.domain.Translation

class HistoryRepositoryImpl(
	private val dao: TranslationDao
) : HistoryRepository {
	override fun getHistory(): List<Translation> {
		return dao.getAll().map { entity ->
			Translation(
				sourceText = entity.sourceText,
				translatedText = entity.translatedText
			)
		}
	}

	override fun saveTranslation(translation: Translation) {
		dao.insert(
			TranslationEntity(
				sourceText = translation.sourceText,
				translatedText = translation.translatedText,
				createdAtMs = System.currentTimeMillis()
			)
		)
	}
}
