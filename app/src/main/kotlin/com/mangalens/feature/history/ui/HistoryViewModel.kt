package com.mangalens.feature.history.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mangalens.feature.history.domain.GetHistoryUseCase

class HistoryViewModel(
	private val getHistory: GetHistoryUseCase
) : ViewModel() {
	private val _state = mutableStateOf(HistoryState())
	val state: State<HistoryState> = _state

	fun load() {
		_state.value = _state.value.copy(isLoading = true, lastError = null)
		val items = try {
			getHistory()
		} catch (error: Throwable) {
			_state.value = _state.value.copy(lastError = error.message)
			emptyList()
		}
		_state.value = _state.value.copy(isLoading = false, items = items)
	}
}
