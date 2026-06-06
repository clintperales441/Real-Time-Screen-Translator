package com.mangalens

import android.app.Application
import com.mangalens.feature.screencapture.data.CapturePrefs
import com.mangalens.feature.screencapture.data.SharedCaptureState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MangaLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bug C fix: Android 15 does not allow resuming a MediaProjection
        // session after process death — the token becomes invalid. Clearing
        // all capture prefs here ensures a cold launch never silently tries
        // to restart the service with a dead token, which produced the
        // repeated capture-permission dialog loop.
        CapturePrefs.clear(this)
        SharedCaptureState.resultCode = Int.MIN_VALUE
        SharedCaptureState.captureIntent = null
    }
}