package com.mangalens.feature.screencapture.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.core.content.ContextCompat
import com.mangalens.feature.screencapture.data.CapturePrefs
import com.mangalens.feature.screencapture.data.ScreenCaptureService
import com.mangalens.feature.screencapture.data.SharedCaptureState
import com.mangalens.feature.screencapture.domain.ScreenCaptureRepository
import com.mangalens.feature.screencapture.domain.StartCaptureUseCase

class ScreenCaptureViewModel(
    private val startCapture: StartCaptureUseCase,
    private val repository: ScreenCaptureRepository
) : ViewModel() {

    val isCapturing: State<Boolean> = SharedCaptureState.isCapturing

    fun onStartCapture(context: Context, resultCode: Int, data: Intent) {
        // Save to in-memory state BEFORE starting the service so the service
        // can use it as a fallback if the Intent extras are ever missing.
        SharedCaptureState.resultCode = resultCode
        SharedCaptureState.captureIntent = data
        CapturePrefs.saveResultCode(context, resultCode)

        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA", data)
            // Do NOT add FLAG_ACTIVITY_NEW_TASK — it causes Android to deliver
            // a recycled back-stack intent, dropping extras silently.
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            SharedCaptureState.setCapturing(false)
            SharedCaptureState.resultCode = Int.MIN_VALUE
            SharedCaptureState.captureIntent = null
            CapturePrefs.clear(context)
        }
    }

    fun onStopCapture(context: Context) {
        CapturePrefs.clear(context)
        SharedCaptureState.resultCode = Int.MIN_VALUE
        SharedCaptureState.captureIntent = null
        SharedCaptureState.setCapturing(false)
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }
}