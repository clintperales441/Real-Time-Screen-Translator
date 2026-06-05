package com.mangalens.feature.overlay.ui

import com.mangalens.feature.overlay.domain.OverlayItem

data class OverlayState(
	val isVisible: Boolean = false,
	val items: List<OverlayItem> = emptyList()
)
