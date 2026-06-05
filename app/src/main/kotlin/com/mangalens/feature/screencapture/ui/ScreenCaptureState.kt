package com.mangalens.feature.screencapture.ui

data class ScreenCaptureState(
    val isCapturing: Boolean = false,
    val lastError: String? = null
)
