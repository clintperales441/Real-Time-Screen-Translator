package com.mangalens.feature.screencapture.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mangalens.feature.ocr.data.MlKitOcrDataSource
import com.mangalens.feature.overlay.data.OverlayService
import com.mangalens.feature.overlay.data.SharedOverlayState
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.screencapture.data.CapturePrefs
import com.mangalens.feature.screencapture.domain.CapturedFrame
import com.mangalens.feature.translator.data.MlKitTranslationSource
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null
    
    private val ocrDataSource = MlKitOcrDataSource()
    private val translationSource = MlKitTranslationSource()

    companion object {
        private const val TAG = "MangaLensCapture"
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "screen_capture_channel"
        const val ACTION_START = "com.mangalens.action.START_CAPTURE"
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "MangaLens: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Screen Capture Service", 
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getInitialNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MangaLens is active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Call startForeground IMMEDIATELY to prevent the ANR/Crash
        startForeground(NOTIF_ID, getInitialNotification("Initializing..."))
        
        Log.d(TAG, "onStartCommand received")
        Log.d(TAG, "start action=${intent?.action}, hasResultCode=${intent?.hasExtra("RESULT_CODE") == true}, hasData=${intent?.hasExtra("DATA") == true}")

        if (intent?.action != ACTION_START) {
            Log.w(TAG, "Ignoring start without ACTION_START")
            stopSelf()
            return START_NOT_STICKY
        }

        var resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        var data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        }

        // Fallback to shared state
        if (resultCode == -1 || data == null) {
            Log.w(TAG, "Intent data missing, checking SharedCaptureState")
            resultCode = SharedCaptureState.resultCode
            data = SharedCaptureState.captureIntent
        }

        if (resultCode != -1 && data != null) {
            SharedCaptureState.setCapturing(true)
            CapturePrefs.setWantsCapture(applicationContext, true)
            showToast("Service Started")
            startCapture(resultCode, data)
            startProcessingLoop()
        } else {
            Log.e(TAG, "FATAL: No capture data found in Intent or SharedState. resultCode=$resultCode")
            SharedCaptureState.setCapturing(false)
            SharedCaptureState.resultCode = -1
            SharedCaptureState.captureIntent = null
            CapturePrefs.setWantsCapture(applicationContext, false)
            showToast("Error: Missing capture data")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MangaLens Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        try {
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed: ${e.message}")
        }

        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection")
            SharedCaptureState.setCapturing(false)
            SharedCaptureState.resultCode = -1
            SharedCaptureState.captureIntent = null
            CapturePrefs.setWantsCapture(applicationContext, false)
            showToast("Failed to start screen capture")
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        
        Log.d(TAG, "Capture started: ${width}x${height}")
        updateNotification("Watching for Japanese text...")

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            
            if (image != null) {
                val frame = CapturedFrame(
                    image = image,
                    width = width,
                    height = height,
                    rotation = 0,
                    timestampMs = System.currentTimeMillis()
                )
                SharedCaptureState.updateFrame(frame)
            }
        }, null)
    }
    
    private fun startProcessingLoop() {
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            while (isActive) {
                val frame = SharedCaptureState.latestFrame
                if (frame != null) {
                    try {
                        val ocrResults = ocrDataSource.detect(frame)
                        if (ocrResults.isNotEmpty()) {
                            updateNotification("Found ${ocrResults.size} text blocks")
                            val overlays = ocrResults.map { detected ->
                                val translation = translationSource.translate(detected.text)
                                OverlayItem(
                                    id = detected.text.hashCode().toString(),
                                    text = translation.translatedText,
                                    x = detected.x,
                                    y = detected.y
                                )
                            }
                            
                            SharedOverlayState.currentItems = overlays
                            val intent = Intent(this@ScreenCaptureService, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_SHOW
                            }
                            startService(intent)
                        } else {
                            val intent = Intent(this@ScreenCaptureService, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_HIDE
                            }
                            startService(intent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Processing error: ${e.message}")
                    }
                }
                delay(1500)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        processingJob?.cancel()
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        SharedCaptureState.setCapturing(false)
        
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        }
        startService(intent)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
