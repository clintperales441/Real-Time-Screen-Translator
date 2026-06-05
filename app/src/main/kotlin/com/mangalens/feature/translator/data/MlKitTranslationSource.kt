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

	fun translate(text: String): DomainTranslation {
		if (text.isBlank()) return DomainTranslation(text, "")

		return try {
			// Ensure model is downloaded
			Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
			val result = Tasks.await(translator.translate(text))
			DomainTranslation(sourceText = text, translatedText = result)
		} catch (e: Exception) {
			DomainTranslation(sourceText = text, translatedText = "Translation failed")
		}
	}
}
