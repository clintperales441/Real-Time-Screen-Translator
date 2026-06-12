package com.mangalens.feature.screencapture.data

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Detects whether a manga page has changed significantly between two frames.
 *
 * Instead of comparing every pixel (slow), we sample a grid of points and
 * compute the average brightness difference. A manga page scroll produces a
 * large difference; minor OCR re-renders produce almost none.
 *
 * Threshold tuning:
 *   - 0.08 = 8% average brightness change across sample points
 *   - Scrolling one panel: typically 0.15–0.40
 *   - Screen re-render with same content: typically 0.00–0.03
 *   - Page zoom / tap UI: typically 0.05–0.10
 */
object FrameDiffDetector {

    private const val SAMPLE_COLS = 16
    private const val SAMPLE_ROWS = 24
    private const val CHANGE_THRESHOLD = 0.08f

    private var lastSamples: FloatArray? = null

    fun hasPageChanged(bitmap: Bitmap): Boolean {
        val current = sample(bitmap)
        val previous = lastSamples

        lastSamples = current

        if (previous == null) return true // first frame always triggers

        var totalDiff = 0f
        for (i in current.indices) {
            totalDiff += Math.abs(current[i] - previous[i])
        }
        val avgDiff = totalDiff / current.size
        return avgDiff > CHANGE_THRESHOLD
    }

    fun reset() {
        lastSamples = null
    }

    private fun sample(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val samples = FloatArray(SAMPLE_COLS * SAMPLE_ROWS)
        var idx = 0
        for (row in 0 until SAMPLE_ROWS) {
            for (col in 0 until SAMPLE_COLS) {
                val x = (w * (col + 0.5f) / SAMPLE_COLS).toInt().coerceIn(0, w - 1)
                val y = (h * (row + 0.5f) / SAMPLE_ROWS).toInt().coerceIn(0, h - 1)
                val pixel = bitmap.getPixel(x, y)
                // Luminance: weighted average of RGB channels
                samples[idx++] = (
                        0.299f * Color.red(pixel) +
                                0.587f * Color.green(pixel) +
                                0.114f * Color.blue(pixel)
                        ) / 255f
            }
        }
        return samples
    }
}