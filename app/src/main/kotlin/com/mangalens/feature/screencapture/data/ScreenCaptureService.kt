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
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mangalens.feature.gemini.GeminiMangaTranslator
import com.mangalens.feature.gemini.GeminiSettings
import com.mangalens.feature.gemini.OcrBlock
import com.mangalens.feature.ocr.data.MlKitOcrDataSource
import com.mangalens.feature.ocr.domain.DetectedText
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

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    private var lastResultSignature = ""
    private val stableCount = mutableMapOf<String, Int>()
    private val stableText  = mutableMapOf<String, String>()
    private var overlayFrozen = false

    // Gemini translator — created lazily when API key is available
    private var geminiTranslator: GeminiMangaTranslator? = null

    private val ocrDataSource = MlKitOcrDataSource()
    private val mlKitTranslator = MlKitTranslationSource()

    companion object {
        private const val TAG = "MangaLensCapture"
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "screen_capture_channel"
        const val ACTION_START = "com.mangalens.action.START_CAPTURE"
        private const val EXTRA_RESULT_CODE_DEFAULT = Int.MIN_VALUE
        private const val FROZEN_POLL_MS = 800L
        private const val SCAN_INTERVAL_MS = 2000L
        private const val STABLE_THRESHOLD = 3
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

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Initializing..."))

        if (intent?.action != ACTION_START) {
            stopSelf(); return START_NOT_STICKY
        }

        var resultCode = intent.getIntExtra("RESULT_CODE", EXTRA_RESULT_CODE_DEFAULT)
        var data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra("DATA")
        }

        if (resultCode == EXTRA_RESULT_CODE_DEFAULT || data == null) {
            resultCode = SharedCaptureState.resultCode
            data = SharedCaptureState.captureIntent
        }

        if (resultCode != EXTRA_RESULT_CODE_DEFAULT && data != null) {
            SharedCaptureState.setCapturing(true)
            CapturePrefs.setWantsCapture(applicationContext, true)

            // Init Gemini if API key is set
            val apiKey = GeminiSettings.getApiKey(applicationContext)
            val geminiEnabled = GeminiSettings.isEnabled(applicationContext)
            Log.d(TAG, "API key length=${apiKey.length} geminiEnabled=$geminiEnabled")

            if (apiKey.isNotBlank() && geminiEnabled) {
                geminiTranslator = GeminiMangaTranslator(apiKey)
                Log.d(TAG, "✓ Gemini translator initialized")
                showToast("MangaLens started (Gemini AI mode)")
            } else {
                if (apiKey.isNotBlank() && !geminiEnabled) {
                    Log.d(TAG, "Gemini disabled by toggle — using ML Kit")
                    showToast("MangaLens started (ML Kit mode)")
                } else {
                    Log.w(TAG, "No API key — using ML Kit")
                    showToast("MangaLens started (ML Kit mode)")
                }
                serviceScope.launch { mlKitTranslator.ensureModelReady() }
            }

            startCaptureLoop(resultCode, data)
        } else {
            Log.e(TAG, "FATAL: No capture data")
            cleanupState()
            showToast("Error: please try again")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCaptureLoop(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = try {
            mpManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed: ${e.message}"); null
        }

        if (mediaProjection == null) { cleanupState(); stopSelf(); return }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        mediaProjection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { cleanupState(); stopSelf() }
        }, Handler(Looper.getMainLooper()))

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "MangaLensCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        updateNotification("Scanning for Japanese text...")

        captureJob = serviceScope.launch {
            while (isActive) {
                try {
                    val bitmap = readLatestFrame()
                    if (bitmap == null) { delay(SCAN_INTERVAL_MS); continue }

                    // Frozen: only check for page change
                    if (overlayFrozen) {
                        if (!FrameDiffDetector.hasPageChanged(bitmap)) {
                            delay(FROZEN_POLL_MS); continue
                        }
                        Log.d(TAG, "Page change — unfreezing")
                        overlayFrozen = false
                        lastResultSignature = ""
                        stableCount.clear(); stableText.clear()
                        updateNotification("Scanning...")
                        startService(Intent(this@ScreenCaptureService, OverlayService::class.java)
                            .apply { action = OverlayService.ACTION_HIDE })
                    } else {
                        FrameDiffDetector.hasPageChanged(bitmap)
                    }

                    val frame = CapturedFrame(bitmap, width, height, 0, System.currentTimeMillis())

                    val overlays: List<OverlayItem> = if (geminiTranslator != null) {
                        translateWithGemini(bitmap, frame)
                    } else {
                        translateWithMlKit(frame)
                    }

                    if (overlays.isNotEmpty()) {
                        val sig = overlays.joinToString("|") { "${it.id}:${it.text}" }
                        if (sig != lastResultSignature) {
                            lastResultSignature = sig
                            SharedOverlayState.currentItems = overlays
                            startService(Intent(this@ScreenCaptureService, OverlayService::class.java)
                                .apply { action = OverlayService.ACTION_SHOW })
                            val mode = if (geminiTranslator != null) "Gemini" else "ML Kit"
                            updateNotification("${overlays.size} translations ($mode) — scroll to update")
                        }
                        overlayFrozen = true
                    } else if (lastResultSignature.isNotEmpty()) {
                        lastResultSignature = ""
                        startService(Intent(this@ScreenCaptureService, OverlayService::class.java)
                            .apply { action = OverlayService.ACTION_HIDE })
                        updateNotification("Scanning...")
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Capture loop error: ${e.message}")
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    // --- Gemini path ---
    // ML Kit OCR provides bounding box positions.
    // Gemini provides the actual translation.
    // No stabilization needed here — Gemini handles noisy OCR text well.
    private suspend fun translateWithGemini(
        bitmap: Bitmap,
        frame: CapturedFrame
    ): List<OverlayItem> {
        val ocrResults = ocrDataSource.detect(frame)
        Log.d(TAG, "Gemini path: ${ocrResults.size} OCR blocks found")

        if (ocrResults.isEmpty()) return emptyList()

        val ocrBlocksForGemini = ocrResults.map { d ->
            OcrBlock(d.text, d.x, d.y, d.width, d.height)
        }

        Log.d(TAG, "Calling Gemini API with ${ocrBlocksForGemini.size} blocks")
        val result = geminiTranslator!!.translate(
            bitmap, frame.width, frame.height, ocrBlocksForGemini
        )
        Log.d(TAG, "Gemini returned ${result.size} overlay items")
        return result
    }

    // --- ML Kit fallback path (unchanged from before) ---
    private suspend fun translateWithMlKit(frame: CapturedFrame): List<OverlayItem> {
        val ocrResults = ocrDataSource.detect(frame)
        val currentKeys = mutableSetOf<String>()
        val stableBlocks = mutableListOf<DetectedText>()

        for (block in ocrResults) {
            val key = "${block.x / 80}_${block.y / 80}"
            currentKeys.add(key)
            if (stableText[key] == block.text) {
                stableCount[key] = (stableCount[key] ?: 0) + 1
            } else {
                stableText[key] = block.text; stableCount[key] = 1
            }
            val threshold = if (block.width * block.height > 5000) 2 else STABLE_THRESHOLD
            if ((stableCount[key] ?: 0) >= threshold) stableBlocks.add(block)
        }
        (stableCount.keys - currentKeys).forEach { stableCount.remove(it); stableText.remove(it) }

        if (stableBlocks.isEmpty()) return emptyList()

        return coroutineScope {
            stableBlocks.map { detected ->
                async(Dispatchers.IO) {
                    val t = mlKitTranslator.translate(detected.text)
                    if (t.translatedText.isNotBlank() && t.translatedText != "[translation failed]") {
                        OverlayItem(
                            detected.text.hashCode().toString(), t.translatedText,
                            detected.x, detected.y, detected.width, detected.height
                        )
                    } else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun readLatestFrame(): Bitmap? {
        val image = try { imageReader?.acquireLatestImage() } catch (e: Exception) { null }
            ?: return null
        return try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val bmp = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            if (rowPadding == 0) bmp
            else { val c = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height); bmp.recycle(); c }
        } catch (e: Exception) { null }
        finally { image.close() }
    }

    private fun cleanupState() {
        SharedCaptureState.setCapturing(false)
        SharedCaptureState.resultCode = EXTRA_RESULT_CODE_DEFAULT
        SharedCaptureState.captureIntent = null
        CapturePrefs.clear(applicationContext)
        FrameDiffDetector.reset()
    }

    override fun onDestroy() {
        captureJob?.cancel(); serviceScope.cancel()
        virtualDisplay?.release(); imageReader?.close(); mediaProjection?.stop()
        cleanupState()
        try { startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_HIDE }) }
        catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}