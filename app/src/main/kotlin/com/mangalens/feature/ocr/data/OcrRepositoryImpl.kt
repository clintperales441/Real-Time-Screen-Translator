package com.mangalens.feature.ocr.data

import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.ocr.domain.OcrRepository
import com.mangalens.feature.screencapture.domain.CapturedFrame

class OcrRepositoryImpl(
	private val mlKitDataSource: MlKitOcrDataSource
) : OcrRepository {
	override fun detectText(frame: CapturedFrame): List<DetectedText> {
		return mlKitDataSource.detect(frame)
	}
}
