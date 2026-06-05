package com.mangalens.feature.translator.ui

import com.mangalens.feature.translator.domain.Translation

data class TranslationState(
	val isTranslating: Boolean = false,
	val result: Translation? = null,
	val lastError: String? = null
)
