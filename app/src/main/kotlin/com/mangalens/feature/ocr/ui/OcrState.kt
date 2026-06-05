package com.mangalens.feature.ocr.ui

import com.mangalens.feature.ocr.domain.DetectedText

data class OcrState(
	val isProcessing: Boolean = false,
	val results: List<DetectedText> = emptyList(),
	val lastError: String? = null
)
