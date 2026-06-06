package com.mangalens.feature.screencapture.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mangalens.feature.ocr.data.MlKitOcrDataSource
import com.mangalens.feature.overlay.data.OverlayService
import com.mangalens.feature.overlay.data.SharedOverlayState
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.screencapture.domain.CapturedFrame
import com.mangalens.feature.translator.data.MlKitTranslationSource
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // Dedicated background thread for ImageReader callbacks.
    // Keeps the main thread free and prevents buffer starvation.
    private var imageThread: HandlerThread? = null
    private var imageHandler: Handler? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

    private val ocrDataSource = MlKitOcrDataSource()
    private val translationSource = MlKitTranslationSource()

    // Throttle: only convert a new frame at most once every FRAME_INTERVAL_MS.
    private val FRAME_INTERVAL_MS = 1500L
    @Volatile private var lastFrameMs = 0L

    companion object {
        private const val TAG = "MangaLensCapture"
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "screen_capture_channel"
        const val ACTION_START = "com.mangalens.action.START_CAPTURE"
        private const val EXTRA_RESULT_CODE_DEFAULT = Int.MIN_VALUE
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

        // Start the background thread for image callbacks
        imageThread = HandlerThread("ImageReaderThread").also {
            it.start()
            imageHandler = Handler(it.looper)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Capture Service", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MangaLens Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Initializing..."))

        Log.d(TAG, "onStartCommand received")
        Log.d(TAG, "action=${intent?.action}, hasResultCode=${intent?.hasExtra("RESULT_CODE")}, hasData=${intent?.hasExtra("DATA")}")

        if (intent?.action != ACTION_START) {
            Log.w(TAG, "Ignoring start without ACTION_START")
            stopSelf()
            return START_NOT_STICKY
        }

        var resultCode = intent.getIntExtra("RESULT_CODE", EXTRA_RESULT_CODE_DEFAULT)
        var data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("DATA")
        }

        Log.d(TAG, "From Intent: resultCode=$resultCode, data=$data")

        if (resultCode == EXTRA_RESULT_CODE_DEFAULT || data == null) {
            Log.w(TAG, "Intent extras missing — falling back to SharedCaptureState")
            resultCode = SharedCaptureState.resultCode
            data = SharedCaptureState.captureIntent
            Log.d(TAG, "From SharedCaptureState: resultCode=$resultCode, data=$data")
        }

        val hasValidCode = resultCode != EXTRA_RESULT_CODE_DEFAULT
        if (hasValidCode && data != null) {
            SharedCaptureState.setCapturing(true)
            CapturePrefs.setWantsCapture(applicationContext, true)
            showToast("Service started")
            startCapture(resultCode, data)
            startProcessingLoop()
        } else {
            Log.e(TAG, "FATAL: No capture data. resultCode=$resultCode, data=$data")
            SharedCaptureState.setCapturing(false)
            SharedCaptureState.resultCode = EXTRA_RESULT_CODE_DEFAULT
            SharedCaptureState.captureIntent = null
            CapturePrefs.clear(applicationContext)
            showToast("Error: missing capture data — please try again")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
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
            SharedCaptureState.resultCode = EXTRA_RESULT_CODE_DEFAULT
            SharedCaptureState.captureIntent = null
            CapturePrefs.clear(applicationContext)
            showToast("Failed to start screen capture")
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Required on Android 14+ before createVirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
                SharedCaptureState.setCapturing(false)
                SharedCaptureState.resultCode = EXTRA_RESULT_CODE_DEFAULT
                SharedCaptureState.captureIntent = null
                CapturePrefs.clear(applicationContext)
                virtualDisplay?.release()
                imageReader?.close()
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        // maxImages=2 with a background handler: the background thread processes
        // one image while the next is being written, preventing starvation.
        // The throttle (FRAME_INTERVAL_MS) ensures we discard frames we can't
        // process fast enough rather than queuing them.
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        Log.d(TAG, "Capture started: ${width}x${height}")
        updateNotification("Watching for Japanese text...")

        // Pass imageHandler so callbacks run on the background thread, not main.
        imageReader?.setOnImageAvailableListener({ reader ->
            val nowMs = System.currentTimeMillis()

            // Throttle: acquire and immediately discard frames we don't need
            // to keep the buffer slots free.
            val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
                ?: return@setOnImageAvailableListener

            if (nowMs - lastFrameMs < FRAME_INTERVAL_MS) {
                // Too soon — drop this frame but close it immediately
                image.close()
                return@setOnImageAvailableListener
            }

            lastFrameMs = nowMs

            val bitmap = try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(plane.buffer)
                if (rowPadding == 0) bmp
                else Bitmap.createBitmap(bmp, 0, 0, width, height)
            } catch (e: Exception) {
                Log.e(TAG, "Bitmap conversion failed: ${e.message}")
                null
            } finally {
                image.close() // always release the slot
            }

            if (bitmap != null) {
                SharedCaptureState.updateFrame(
                    CapturedFrame(
                        bitmap = bitmap,
                        width = width,
                        height = height,
                        rotation = 0,
                        timestampMs = nowMs
                    )
                )
                Log.d(TAG, "Frame captured at $nowMs")
            }
        }, imageHandler) // <-- background thread, not null/main
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
                            startService(Intent(this@ScreenCaptureService, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_SHOW
                            })
                        } else {
                            startService(Intent(this@ScreenCaptureService, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_HIDE
                            })
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Processing error: ${e.message}")
                    }
                }
                delay(FRAME_INTERVAL_MS)
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
        imageThread?.quitSafely()
        SharedCaptureState.setCapturing(false)
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        })
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}