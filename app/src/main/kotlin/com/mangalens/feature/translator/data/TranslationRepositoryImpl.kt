package com.mangalens.feature.translator.data

import com.mangalens.feature.translator.domain.Translation
import com.mangalens.feature.translator.domain.TranslationRepository

class TranslationRepositoryImpl(
	private val dataSource: MlKitTranslationSource
) : TranslationRepository {
	override fun translate(text: String): Translation {
		return dataSource.translate(text)
	}
}
