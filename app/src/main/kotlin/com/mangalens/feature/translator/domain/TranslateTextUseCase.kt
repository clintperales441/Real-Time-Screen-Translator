package com.mangalens.feature.translator.domain

class TranslateTextUseCase(
	private val repository: TranslationRepository
) {
	operator fun invoke(text: String): Translation {
		return repository.translate(text)
	}
}
