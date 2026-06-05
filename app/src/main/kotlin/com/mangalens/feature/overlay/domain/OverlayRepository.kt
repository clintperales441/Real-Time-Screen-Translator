package com.mangalens.feature.overlay.domain

interface OverlayRepository {
	fun show(items: List<OverlayItem>)
	fun hide()
	fun isVisible(): Boolean
}
