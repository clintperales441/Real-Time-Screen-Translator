package com.mangalens.feature.screencapture.domain

class StartCaptureUseCase(
	private val repository: ScreenCaptureRepository
) {
	operator fun invoke(): Boolean {
		return repository.startCapture()
	}
}
