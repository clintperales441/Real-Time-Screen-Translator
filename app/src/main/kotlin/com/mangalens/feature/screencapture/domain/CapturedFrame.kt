package com.mangalens.feature.screencapture.domain

import android.media.Image

data class CapturedFrame(
	val image: Image,
	val width: Int,
	val height: Int,
	val rotation: Int,
	val timestampMs: Long
)
