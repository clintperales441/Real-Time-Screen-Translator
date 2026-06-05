package com.mangalens.feature.history.domain

import com.mangalens.feature.translator.domain.Translation

interface HistoryRepository {
	fun getHistory(): List<Translation>
	fun saveTranslation(translation: Translation)
}
