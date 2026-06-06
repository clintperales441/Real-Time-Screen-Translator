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
		// fromBitmap() accepts any Bitmap config (ARGB_8888 included).
		// fromMediaImage() only accepts YUV_420_888 / JPEG, which caused the
		// "Only JPEG and YUV_420_888 are supported" crash on every frame.
		val image = InputImage.fromBitmap(frame.bitmap, frame.rotation)
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