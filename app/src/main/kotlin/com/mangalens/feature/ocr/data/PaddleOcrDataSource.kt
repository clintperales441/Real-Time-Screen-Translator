package com.mangalens.feature.ocr.data

import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.screencapture.domain.CapturedFrame

class PaddleOcrDataSource {
	fun detect(frame: CapturedFrame): List<DetectedText> {
		return emptyList()
	}
}
