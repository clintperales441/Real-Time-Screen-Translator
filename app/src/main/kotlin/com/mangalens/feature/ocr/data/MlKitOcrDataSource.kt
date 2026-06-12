package com.mangalens.feature.ocr.data

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.screencapture.domain.CapturedFrame

class MlKitOcrDataSource {
	private val recognizer = TextRecognition.getClient(
		JapaneseTextRecognizerOptions.Builder().build()
	)

	fun detect(frame: CapturedFrame): List<DetectedText> {
		val processed = BitmapPreprocessor.process(frame.bitmap)
		val image = InputImage.fromBitmap(processed, frame.rotation)

		return try {
			val result = Tasks.await(recognizer.process(image))

			val candidates = result.textBlocks.mapNotNull { block ->
				val box = block.boundingBox ?: return@mapNotNull null
				val text = cleanOcrText(block.text)

				if (text.isBlank()) return@mapNotNull null

				// Must contain Japanese
				val hasJapanese = text.any { c ->
					Character.UnicodeBlock.of(c).let { ub ->
						ub == Character.UnicodeBlock.HIRAGANA ||
								ub == Character.UnicodeBlock.KATAKANA ||
								ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
								ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
								ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
					}
				}
				if (!hasJapanese) return@mapNotNull null
				if (box.width() * box.height() < 800) return@mapNotNull null

				DetectedText(text, box.left, box.top, box.width(), box.height())
			}.toMutableList()

			// Merge only blocks that are clearly inside the same bubble:
			// horizontally overlapping OR very close vertically (same column)
			mergeSameBubbleBlocks(candidates)

		} catch (e: Exception) {
			emptyList()
		} finally {
			processed.recycle()
		}
	}

	/**
	 * Cleans raw OCR text from a single block before merging/translation.
	 * Removes common misreads that corrupt the translation input.
	 */
	private fun cleanOcrText(raw: String): String {
		return raw
			.lines()
			.map { it.trim() }
			.filter { line ->
				if (line.isBlank()) return@filter false
				// Drop lines that are purely Latin/ASCII mixed into Japanese blocks
				// (OCR misreads of bubble borders, furigana etc.)
				val japaneseChars = line.count { c ->
					Character.UnicodeBlock.of(c).let { ub ->
						ub == Character.UnicodeBlock.HIRAGANA ||
								ub == Character.UnicodeBlock.KATAKANA ||
								ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
					}
				}
				val totalMeaningful = line.count { !it.isWhitespace() }
				// Keep if at least 40% of chars are Japanese, or line is long enough
				totalMeaningful >= 3 && (japaneseChars.toFloat() / totalMeaningful >= 0.4f ||
						japaneseChars >= 2)
			}
			.joinToString("\n")
			.trim()
	}

	/**
	 * Smarter merge: only joins blocks that belong to the same speech bubble.
	 *
	 * Two blocks are merged if:
	 * A) They horizontally overlap (same vertical column of text), OR
	 * B) They are within 35px vertically AND within the wider of the two widths
	 *    horizontally (same bubble, different lines)
	 *
	 * This prevents merging text from adjacent panels or different bubbles
	 * that happen to be close but are separated by panel borders.
	 */
	private fun mergeSameBubbleBlocks(blocks: List<DetectedText>): List<DetectedText> {
		val result = blocks.toMutableList()
		var merged = true
		while (merged) {
			merged = false
			outer@ for (i in result.indices) {
				for (j in result.indices) {
					if (i == j) continue
					val a = result[i]
					val b = result[j]
					if (shouldMerge(a, b)) {
						val newX = minOf(a.x, b.x)
						val newY = minOf(a.y, b.y)
						val newR = maxOf(a.x + a.width, b.x + b.width)
						val newB = maxOf(a.y + a.height, b.y + b.height)
						result[i] = DetectedText(
							text   = "${a.text}\n${b.text}",
							x      = newX, y = newY,
							width  = newR - newX,
							height = newB - newY
						)
						result.removeAt(j)
						merged = true
						break@outer
					}
				}
			}
		}
		return result
	}

	private fun shouldMerge(a: DetectedText, b: DetectedText): Boolean {
		val aL = a.x;         val aT = a.y
		val aR = a.x + a.width; val aB = a.y + a.height
		val bL = b.x;         val bT = b.y
		val bR = b.x + b.width; val bB = b.y + b.height

		// Check horizontal overlap (same vertical text column)
		val hOverlap = aL < bR && aR > bL
		// Check vertical proximity
		val vGap = if (aB <= bT) bT - aB else if (bB <= aT) aT - bB else 0
		val vClose = vGap <= 35

		// Check if b is horizontally contained within a's width (or vice versa)
		// with some tolerance — indicates same bubble
		val maxW = maxOf(a.width, b.width)
		val hContained = minOf(aR, bR) - maxOf(aL, bL) >= maxW * 0.3f

		return (hOverlap && vClose) || (hContained && vClose)
	}
}