package com.mangalens.feature.screencapture.data

import com.mangalens.feature.screencapture.domain.CapturedFrame
import com.mangalens.feature.screencapture.domain.ScreenCaptureRepository

class ScreenCaptureRepositoryImpl(
	private val dataSource: MediaProjectionDataSource
) : ScreenCaptureRepository {
	override fun startCapture(): Boolean {
		return dataSource.start()
	}

	override fun stopCapture() {
		dataSource.stop()
	}

	override fun isCapturing(): Boolean {
		return dataSource.isCapturing()
	}

	override fun getLatestFrame(): CapturedFrame? {
		return dataSource.getLatestFrame()
	}
}
