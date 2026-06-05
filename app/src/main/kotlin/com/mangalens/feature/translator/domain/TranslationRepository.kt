package com.mangalens.feature.translator.domain

interface TranslationRepository {
	fun translate(text: String): Translation
}
