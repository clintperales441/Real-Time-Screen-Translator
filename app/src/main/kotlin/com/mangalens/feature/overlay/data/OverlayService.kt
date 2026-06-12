package com.mangalens.feature.overlay.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.mangalens.feature.overlay.domain.OverlayItem

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null
    private var lastItemSignature = ""

    companion object {
        private const val TAG = "MangaLensOverlay"
        const val ACTION_SHOW = "com.mangalens.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.mangalens.action.HIDE_OVERLAY"
        private val FONT_SIZES_SP = floatArrayOf(12f, 11f, 10f, 9f, 8f, 7f, 6f)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        rootLayout = FrameLayout(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> updateOverlays(SharedOverlayState.currentItems)
            ACTION_HIDE -> { lastItemSignature = ""; removeOverlays() }
        }
        return START_NOT_STICKY
    }

    private fun updateOverlays(items: List<OverlayItem>) {
        if (items.isEmpty()) { removeOverlays(); return }

        val signature = items.joinToString("|") { "${it.id}:${it.text}" }
        if (signature == lastItemSignature) return
        lastItemSignature = signature

        removeOverlays()

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        items.forEach { item ->
            // --- Width ---
            // Clamp between 110px (minimum readable) and 75% of screen width
            // (prevents one bubble spanning the whole screen)
            val boxW = if (item.width > 0)
                item.width.coerceIn(110, (screenW * 0.75f).toInt())
            else 150

            // --- Find best fitting font size ---
            val fittedSp = pickFontSize(item.text, boxW, item.height, metrics)

            // --- Height ---
            // Measure actual text height at the chosen font size, then use
            // whichever is LARGER: the OCR box height or the measured text height.
            // This fixes sentences being cut off — the box expands to fit the
            // English translation even when it's longer than the Japanese source.
            val measuredH = measureTextHeight(item.text, boxW, fittedSp, metrics)
            val boxH = if (item.height > 0) maxOf(item.height, measuredH + 8)
            else measuredH + 8

            // --- Position ---
            // Clamp to screen bounds so overlays never go off-screen
            val clampedX = item.x.coerceIn(0, (screenW - boxW).coerceAtLeast(0))
            val clampedY = item.y.coerceIn(0, (screenH - boxH).coerceAtLeast(0))

            val textView = TextView(this).apply {
                text = item.text
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(220, 0, 0, 0))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(4, 2, 4, 2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fittedSp)
                isSingleLine = false
                maxLines = Int.MAX_VALUE
                // No ellipsize — text is sized to fit
            }

            val lp = FrameLayout.LayoutParams(boxW, boxH).apply {
                leftMargin = clampedX
                topMargin  = clampedY
            }
            rootLayout?.addView(textView, lp)
        }

        try {
            if (rootLayout?.parent == null) windowManager?.addView(rootLayout, wlp)
            else windowManager?.updateViewLayout(rootLayout, wlp)
            Log.d(TAG, "Overlay updated: ${items.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}")
        }
    }

    /**
     * Finds the largest font size where [text] fits inside [boxW] × [boxH].
     * Uses StaticLayout for accurate wrapping measurement.
     * Returns the smallest size if nothing fits.
     */
    private fun pickFontSize(
        text: String,
        boxW: Int,
        boxH: Int,
        metrics: android.util.DisplayMetrics
    ): Float {
        // If no height constraint, use largest size
        if (boxH <= 0) return FONT_SIZES_SP[0]

        for (sp in FONT_SIZES_SP) {
            val h = measureTextHeight(text, boxW, sp, metrics)
            if (h <= boxH - 4) return sp
        }
        return FONT_SIZES_SP.last()
    }

    /**
     * Measures how tall [text] will be when wrapped inside [boxW] at [fontSp].
     */
    private fun measureTextHeight(
        text: String,
        boxW: Int,
        fontSp: Float,
        metrics: android.util.DisplayMetrics
    ): Int {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSp, metrics)
        val paint = TextPaint().apply {
            textSize = px
            typeface = Typeface.DEFAULT_BOLD
        }
        val innerW = (boxW - 8).coerceAtLeast(1)
        val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, innerW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, innerW, Layout.Alignment.ALIGN_CENTER, 1.15f, 0f, false)
        }
        return layout.height
    }

    private fun removeOverlays() {
        rootLayout?.removeAllViews()
        try {
            if (rootLayout?.parent != null) windowManager?.removeView(rootLayout)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        }
    }

    override fun onDestroy() { removeOverlays(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

object SharedOverlayState {
    var currentItems: List<OverlayItem> = emptyList()
}