package com.mangalens.feature.overlay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OverlayComposable(state: OverlayState) {
	Box(modifier = Modifier.fillMaxSize()) {
		if (state.isVisible && state.items.isNotEmpty()) {
			Text(text = "Overlay items: ${state.items.size}")
		}
	}
}
