package com.mangalens.feature.history.domain

import com.mangalens.feature.translator.domain.Translation

class SaveTranslationUseCase(
	private val repository: HistoryRepository
) {
	operator fun invoke(translation: Translation) {
		repository.saveTranslation(translation)
	}
}
