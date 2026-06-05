package com.mangalens.feature.overlay.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.overlay.domain.OverlayRepository

class OverlayViewModel(
	private val repository: OverlayRepository
) : ViewModel() {
	private val _state = mutableStateOf(OverlayState())
	val state: State<OverlayState> = _state

	fun show(items: List<OverlayItem>) {
		repository.show(items)
		_state.value = _state.value.copy(isVisible = true, items = items)
	}

	fun hide() {
		repository.hide()
		_state.value = _state.value.copy(isVisible = false, items = emptyList())
	}
}
