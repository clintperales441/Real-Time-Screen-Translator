package com.mangalens.feature.screencapture.data

import android.content.Intent
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.mangalens.feature.screencapture.domain.CapturedFrame

object SharedCaptureState {
    private val _isCapturing = mutableStateOf(false)
    val isCapturing: State<Boolean> = _isCapturing

    // Persist capture data here so it's not lost when the UI is recreated
    var captureIntent: Intent? = null
    var resultCode: Int = -1

    @Volatile
    var latestFrame: CapturedFrame? = null
        private set

    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
    }

    @Synchronized
    fun updateFrame(frame: CapturedFrame) {
        latestFrame?.image?.close()
        latestFrame = frame
    }
}
