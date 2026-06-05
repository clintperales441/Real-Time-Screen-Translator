package com.mangalens.feature.ocr.domain

import com.mangalens.feature.screencapture.domain.CapturedFrame

class ProcessFrameUseCase(
	private val repository: OcrRepository
) {
	operator fun invoke(frame: CapturedFrame): List<DetectedText> {
		return repository.detectText(frame)
	}
}
