package com.mangalens.feature.screencapture.domain

import android.graphics.Bitmap

// Uses Bitmap instead of android.media.Image so the hardware buffer is
// released immediately after capture and ML Kit can process any pixel format.
data class CapturedFrame(
	val bitmap: Bitmap,
	val width: Int,
	val height: Int,
	val rotation: Int,
	val timestampMs: Long
)