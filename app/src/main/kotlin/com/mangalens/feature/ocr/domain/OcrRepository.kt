package com.mangalens.feature.ocr.domain

import com.mangalens.feature.screencapture.domain.CapturedFrame

interface OcrRepository {
	fun detectText(frame: CapturedFrame): List<DetectedText>
}
