package com.mangalens.feature.ocr.domain

data class DetectedText(
	val text: String,
	val x: Int,
	val y: Int,
	val width: Int,
	val height: Int
)
