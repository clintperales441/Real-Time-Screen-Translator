package com.mangalens.feature.screencapture.data

import android.content.Context

object CapturePrefs {
    private const val PREFS_NAME = "capture_prefs"
    private const val KEY_WANTS_CAPTURE = "wants_capture"

    fun wantsCapture(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WANTS_CAPTURE, false)
    }

    fun setWantsCapture(context: Context, wantsCapture: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WANTS_CAPTURE, wantsCapture)
            .apply()
    }
}
