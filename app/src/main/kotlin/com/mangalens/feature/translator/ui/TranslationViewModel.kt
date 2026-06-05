package com.mangalens.feature.translator.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mangalens.feature.translator.domain.TranslateTextUseCase

class TranslationViewModel(
	private val translateText: TranslateTextUseCase
) : ViewModel() {
	private val _state = mutableStateOf(TranslationState())
	val state: State<TranslationState> = _state

	fun translate(text: String) {
		_state.value = _state.value.copy(isTranslating = true, lastError = null)
		val result = try {
			translateText(text)
		} catch (error: Throwable) {
			_state.value = _state.value.copy(lastError = error.message)
			null
		}
		_state.value = _state.value.copy(isTranslating = false, result = result)
	}
}
