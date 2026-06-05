package com.mangalens.feature.overlay.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.mangalens.feature.overlay.domain.OverlayItem

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null

    companion object {
        private const val TAG = "MangaLensOverlay"
        const val ACTION_SHOW = "com.mangalens.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.mangalens.action.HIDE_OVERLAY"
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Overlay Service Created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        rootLayout = FrameLayout(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action")
        if (action == ACTION_SHOW) {
            val items = SharedOverlayState.currentItems
            Log.d(TAG, "Showing ${items.size} items")
            updateOverlays(items)
        } else if (action == ACTION_HIDE) {
            Log.d(TAG, "Hiding overlays")
            removeOverlays()
        }
        return START_NOT_STICKY
    }

    private fun updateOverlays(items: List<OverlayItem>) {
        removeOverlays()
        if (items.isEmpty()) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        items.forEach { item ->
            val textView = TextView(this).apply {
                text = item.text
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(200, 0, 0, 0))
                setPadding(12, 6, 12, 6)
                textSize = 14f
                maxWidth = 400
            }

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = item.x
                topMargin = item.y
            }
            rootLayout?.addView(textView, params)
        }

        try {
            if (rootLayout?.parent == null) {
                windowManager?.addView(rootLayout, layoutParams)
                Log.d(TAG, "Root layout added to WindowManager")
            } else {
                windowManager?.updateViewLayout(rootLayout, layoutParams)
                Log.d(TAG, "Root layout updated in WindowManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}")
        }
    }

    private fun removeOverlays() {
        rootLayout?.removeAllViews()
        try {
            if (rootLayout?.parent != null) {
                windowManager?.removeView(rootLayout)
                Log.d(TAG, "Root layout removed from WindowManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Overlay Service Destroyed")
        removeOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object SharedOverlayState {
    var currentItems: List<OverlayItem> = emptyList()
}
