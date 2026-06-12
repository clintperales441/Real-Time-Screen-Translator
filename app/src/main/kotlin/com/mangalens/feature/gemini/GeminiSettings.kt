package com.mangalens.feature.gemini

import android.content.Context

object GeminiSettings {
    private const val PREFS = "gemini_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENABLED = "gemini_enabled"

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, key: String) {
        // Only trim surrounding whitespace — aggressive filtering was
        // corrupting the key by removing valid characters
        val cleaned = key.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, cleaned).apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isConfigured(context: Context): Boolean =
        getApiKey(context).length > 10
}