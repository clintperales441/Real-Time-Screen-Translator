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
        // Save to global state BEFORE starting service
        SharedCaptureState.resultCode = resultCode
        SharedCaptureState.captureIntent = data
        
        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            // Include data in intent as primary source
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA", data)
            // Add flags to ensure it reaches the service correctly
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onStopCapture(context: Context) {
        CapturePrefs.setWantsCapture(context, false)
        SharedCaptureState.resultCode = -1
        SharedCaptureState.captureIntent = null
        SharedCaptureState.setCapturing(false)
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }
}
