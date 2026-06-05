package com.mangalens.feature.screencapture.data

import com.mangalens.feature.screencapture.domain.CapturedFrame

class MediaProjectionDataSource {
	private var capturing = false

	fun start(): Boolean {
		capturing = true
		return capturing
	}

	fun stop() {
		capturing = false
	}

	fun isCapturing(): Boolean {
		return capturing
	}

	fun getLatestFrame(): CapturedFrame? {
		return SharedCaptureState.latestFrame
	}
}
