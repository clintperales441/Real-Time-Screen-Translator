package com.mangalens.feature.ocr.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mangalens.core.common.FrameRateLimiter
import com.mangalens.feature.ocr.domain.ProcessFrameUseCase
import com.mangalens.feature.screencapture.domain.CapturedFrame

class OcrViewModel(
	private val processFrame: ProcessFrameUseCase
) : ViewModel() {
	private val _state = mutableStateOf(OcrState())
	val state: State<OcrState> = _state
	private val limiter = FrameRateLimiter(minIntervalMs = 500)

	fun onFrameCaptured(frame: CapturedFrame) {
		if (!limiter.shouldRun(System.currentTimeMillis())) {
			return
		}
		_state.value = _state.value.copy(isProcessing = true, lastError = null)
		val results = try {
			processFrame(frame)
		} catch (error: Throwable) {
			_state.value = _state.value.copy(lastError = error.message)
			emptyList()
		}
		_state.value = _state.value.copy(isProcessing = false, results = results)
	}
}
