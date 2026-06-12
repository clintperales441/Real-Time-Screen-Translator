package com.mangalens.feature.translator.data

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mangalens.feature.translator.domain.Translation as DomainTranslation

class MlKitTranslationSource {
	private val options = TranslatorOptions.Builder()
		.setSourceLanguage(TranslateLanguage.JAPANESE)
		.setTargetLanguage(TranslateLanguage.ENGLISH)
		.build()
	private val translator = Translation.getClient(options)
	private var modelReady = false

	fun ensureModelReady() {
		if (modelReady) return
		try {
			Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
			modelReady = true
		} catch (e: Exception) { /* retry on next call */ }
	}

	fun translate(text: String): DomainTranslation {
		if (text.isBlank()) return DomainTranslation(text, "")

		val cleaned = cleanForTranslation(text)
		if (cleaned.isBlank()) return DomainTranslation(text, "")

		return try {
			ensureModelReady()
			val result = Tasks.await(translator.translate(cleaned))
			DomainTranslation(sourceText = text, translatedText = result)
		} catch (e: Exception) {
			DomainTranslation(sourceText = text, translatedText = "[translation failed]")
		}
	}

	/**
	 * Prepares merged OCR text for the ML Kit translation model.
	 *
	 * Key insight: ML Kit's offline model translates best when given a
	 * single coherent sentence. Fragmented input like:
	 *   "みんな\nと\n一緒に\nスキー\nに行ったんじゃ"
	 * produces garbled output. Joining into:
	 *   "みんなと一緒にスキーに行ったんじゃ"
	 * gives a much better result.
	 *
	 * What we clean:
	 * 1. Lines that are 1 char — always furigana or OCR noise
	 * 2. Lines that are purely Latin/ASCII mixed into Japanese OCR
	 *    (common misread of bubble borders or English loanwords)
	 * 3. Repeated punctuation artifacts from vertical text reading
	 */
	private fun cleanForTranslation(raw: String): String {
		val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }

		val filtered = lines.filter { line ->
			// Drop single characters
			if (line.length == 1) return@filter false

			// Count Japanese vs non-Japanese meaningful chars
			val jpChars = line.count { c ->
				Character.UnicodeBlock.of(c).let { ub ->
					ub == Character.UnicodeBlock.HIRAGANA ||
							ub == Character.UnicodeBlock.KATAKANA ||
							ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
							ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
				}
			}
			val meaningful = line.count { !it.isWhitespace() && !it.isISOControl() }

			// Keep if has any Japanese, or is short punctuation
			jpChars >= 1 || (meaningful <= 3 && line.any { c ->
				c in "。、！？…・「」『』（）【】"
			})
		}

		if (filtered.isEmpty()) return ""

		// Join all lines into one continuous string — no spaces between
		// Japanese words (they don't use spaces) but preserve natural breaks
		// between sentences by detecting sentence-ending punctuation
		val sb = StringBuilder()
		for (i in filtered.indices) {
			val line = filtered[i]
			sb.append(line)
			// Add space only if previous line ends mid-sentence and next
			// line starts with hiragana/katakana (continuation)
			if (i < filtered.size - 1) {
				val endsWithPunct = line.last() in "。！？…」』）】"
				if (!endsWithPunct) {
					// No separator — Japanese flows without spaces
				}
			}
		}

		return sb.toString().trim()
	}
}