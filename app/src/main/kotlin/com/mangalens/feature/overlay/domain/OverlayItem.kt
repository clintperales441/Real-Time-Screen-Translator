package com.mangalens.feature.overlay.domain

data class OverlayItem(
	val id: String,
	val text: String,
	val x: Int,
	val y: Int,
	val width: Int = 0,
	val height: Int = 0
)