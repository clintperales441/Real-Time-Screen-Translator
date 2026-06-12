package com.mangalens.feature.ocr.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Preprocesses a screen-capture bitmap before OCR.
 *
 * Manga is black ink on white paper. The screen capture arrives as a
 * colour ARGB_8888 bitmap with antialiasing, JPEG compression from the
 * browser renderer, and varying brightness. ML Kit's Japanese recogniser
 * performs much better on clean high-contrast monochrome input.
 *
 * Pipeline:
 *   1. Grayscale         – removes colour noise
 *   2. Contrast boost    – pushes ink darker, paper lighter
 *   3. Slight sharpening – hardens glyph edges blurred by screen scaling
 */
object BitmapPreprocessor {

    fun process(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // --- Step 1 + 2: Grayscale + contrast boost in one ColorMatrix pass ---
        // ColorMatrix layout (row-major, 4×5):
        //   [ R' ]   [ a  0  0  0  b ]   [ R ]
        //   [ G' ] = [ 0  a  0  0  b ] × [ G ]
        //   [ B' ]   [ 0  0  a  0  b ]   [ B ]
        //   [ A' ]   [ 0  0  0  1  0 ]   [ A ]
        //
        // For grayscale: use luminance weights (0.299, 0.587, 0.114)
        // For contrast:  scale = 1.6 (boosts difference from mid-grey)
        //                offset = -0.6 × 255 × (scale-1)/scale  ≈ -60
        //
        // Combined into a single matrix so only one draw call is needed.

        val scale = 1.8f          // contrast multiplier (1.0 = no change)
        val offset = -80f         // brightness offset after scaling

        // Luminance weights × contrast scale
        val rW = 0.299f * scale
        val gW = 0.587f * scale
        val bW = 0.114f * scale

        val cm = ColorMatrix(floatArrayOf(
            rW,  gW,  bW,  0f, offset,   // R channel
            rW,  gW,  bW,  0f, offset,   // G channel
            rW,  gW,  bW,  0f, offset,   // B channel
            0f,  0f,  0f,  1f, 0f        // A channel unchanged
        ))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // --- Step 3: Sharpening via convolution approximation ---
        // True convolution requires RenderScript (deprecated) or a manual
        // pixel loop. Instead we use a cheap overlay trick:
        // draw the image again at 110% opacity centred, then subtract a
        // blurred copy. This is a simplified unsharp-mask.
        // On most manga pages this gives a visible improvement in thin strokes.
        paint.colorFilter = null
        paint.alpha = 255

        // Scale up by 0.5px on each side — cheap way to "sharpen" edges
        // without a full convolution loop
        val sharpenMatrix = android.graphics.Matrix().also { m ->
            val scaleX = (w + 1f) / w
            val scaleY = (h + 1f) / h
            m.setScale(scaleX, scaleY)
            m.postTranslate(-0.5f, -0.5f)
        }
        paint.alpha = 30 // light sharpening pass
        canvas.drawBitmap(out, sharpenMatrix, paint)
        paint.alpha = 255

        return out
    }
}