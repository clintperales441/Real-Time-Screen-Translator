package com.mangalens.feature.screencapture.data

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.mangalens.feature.screencapture.domain.CapturedFrame

object SharedCaptureState {
    private val _isCapturing = mutableStateOf(false)
    val isCapturing: State<Boolean> = _isCapturing

    // Int.MIN_VALUE is the "not set" sentinel. Never use -1 because
    // Activity.RESULT_OK == -1 on Android.
    var resultCode: Int = Int.MIN_VALUE
    var captureIntent: Intent? = null

    @Volatile
    var latestFrame: CapturedFrame? = null
        private set

    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
    }

    // No need to close anything — Bitmap is managed by the GC.
    @Synchronized
    fun updateFrame(frame: CapturedFrame) {
        latestFrame = frame
    }
}