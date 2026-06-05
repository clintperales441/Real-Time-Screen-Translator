package com.mangalens.feature.ocr.data

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.screencapture.domain.CapturedFrame

class MlKitOcrDataSource {
	private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

	fun detect(frame: CapturedFrame): List<DetectedText> {
		val image = InputImage.fromMediaImage(frame.image, frame.rotation)
		return try {
			val result = Tasks.await(recognizer.process(image))
			result.textBlocks.flatMap { block ->
				block.lines.map { line ->
					DetectedText(
						text = line.text,
						x = line.boundingBox?.left ?: 0,
						y = line.boundingBox?.top ?: 0,
						width = line.boundingBox?.width() ?: 0,
						height = line.boundingBox?.height() ?: 0
					)
				}
			}
		} catch (e: Exception) {
			emptyList()
		}
	}
}
