package com.mangalens.feature.screencapture.data

import android.content.Context

object CapturePrefs {
    private const val PREFS_NAME = "capture_prefs"
    private const val KEY_WANTS_CAPTURE = "wants_capture"
    private const val KEY_RESULT_CODE = "result_code"

    fun wantsCapture(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WANTS_CAPTURE, false)

    fun setWantsCapture(context: Context, wantsCapture: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WANTS_CAPTURE, wantsCapture).apply()
    }

    fun saveResultCode(context: Context, resultCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RESULT_CODE, resultCode).apply()
    }

    // Default is Int.MIN_VALUE, never -1, so Activity.RESULT_OK is never mistaken for "not set".
    fun getSavedResultCode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_RESULT_CODE, Int.MIN_VALUE)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}