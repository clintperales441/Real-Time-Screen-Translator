package com.mangalens.feature.history.domain

import com.mangalens.feature.translator.domain.Translation

class GetHistoryUseCase(
	private val repository: HistoryRepository
) {
	operator fun invoke(): List<Translation> {
		return repository.getHistory()
	}
}
