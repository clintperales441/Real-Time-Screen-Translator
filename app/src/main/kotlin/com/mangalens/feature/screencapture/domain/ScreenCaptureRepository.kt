package com.mangalens.feature.screencapture.domain

interface ScreenCaptureRepository {
	fun startCapture(): Boolean
	fun stopCapture()
	fun isCapturing(): Boolean
	fun getLatestFrame(): CapturedFrame?
}
