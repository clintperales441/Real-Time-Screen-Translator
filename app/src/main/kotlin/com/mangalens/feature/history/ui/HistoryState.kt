package com.mangalens.feature.history.ui

import com.mangalens.feature.translator.domain.Translation

data class HistoryState(
	val isLoading: Boolean = false,
	val items: List<Translation> = emptyList(),
	val lastError: String? = null
)
