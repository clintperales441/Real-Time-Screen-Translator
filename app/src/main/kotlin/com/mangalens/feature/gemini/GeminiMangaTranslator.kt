package com.mangalens.feature.gemini

import android.graphics.Bitmap
import android.util.Log
import com.mangalens.feature.overlay.domain.OverlayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class GeminiMangaTranslator(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiTranslator"
        private const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        // Minimum milliseconds between API calls — free tier is 15 req/min
        // so 5000ms (5s) gives safe headroom
        private const val MIN_CALL_INTERVAL_MS = 5000L
    }

    // Track last call time to enforce rate limiting
    private var lastCallMs = 0L

    /**
     * Translates a numbered list of Japanese manga texts into English.
     * ML Kit OCR provides the text + positions; Gemini provides quality translation.
     */
    suspend fun translate(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        ocrBlocks: List<OcrBlock>
    ): List<OverlayItem> = withContext(Dispatchers.IO) {

        if (ocrBlocks.isEmpty()) return@withContext emptyList()

        // Only trim surrounding whitespace — don't filter chars since
        // the aggressive filter was potentially stripping valid key characters
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            Log.e(TAG, "API key is empty")
            return@withContext emptyList()
        }
        Log.d(TAG, "Using API key: ${cleanKey.take(6)}... (length ${cleanKey.length})")

        // Rate limit: enforce minimum interval between API calls
        val nowMs = System.currentTimeMillis()
        val msSinceLast = nowMs - lastCallMs
        if (msSinceLast < MIN_CALL_INTERVAL_MS) {
            Log.d(TAG, "Rate limit: skipping call, ${MIN_CALL_INTERVAL_MS - msSinceLast}ms remaining")
            return@withContext emptyList()
        }
        lastCallMs = nowMs

        try {
            val textList = ocrBlocks.mapIndexed { i, block ->
                "${i + 1}. ${block.text.replace("\n", " ").trim()}"
            }.joinToString("\n")

            val prompt = """Translate these Japanese manga speech bubbles to natural English.

CRITICAL: Output ONLY English translations. Never output Japanese characters.
Number each translation to match the input numbers exactly.
Use natural, conversational English. Complete every sentence fully.
For sound effects (ドキ、バン etc.) write [sfx] and skip.

Input:
$textList

Output (English only, one per line):"""

            val base64Image = bitmapToBase64(bitmap)

            val requestBodyStr = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 512)
                })
            }.toString()

            Log.d(TAG, "Calling Gemini 2.0 Flash")
            val request = Request.Builder()
                .url("$API_URL?key=$cleanKey")
                .post(requestBodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API ${response.code}: ${responseBody.take(200)}")
                // On 429 (rate limit), reset lastCallMs so next eligible call
                // waits the full interval before retrying
                if (response.code == 429) {
                    lastCallMs = System.currentTimeMillis()
                    Log.w(TAG, "Rate limited — will wait ${MIN_CALL_INTERVAL_MS}ms before retry")
                }
                return@withContext emptyList()
            }

            val rawText = extractText(responseBody)
            Log.d(TAG, "Gemini response:\n$rawText")

            parseNumberedTranslations(rawText, ocrBlocks)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parses numbered translation output like:
     *   1. I went skiing with everyone.
     *   2. Are you okay?
     * Matches back to OCR blocks by index for accurate positioning.
     */
    private fun parseNumberedTranslations(
        raw: String,
        ocrBlocks: List<OcrBlock>
    ): List<OverlayItem> {
        val result = mutableListOf<OverlayItem>()
        val lines = raw.lines()

        for (line in lines) {
            val trimmed = line.trim()
            // Match "1. translation text" or "1) translation text"
            val match = Regex("""^(\d+)[.)]\s*(.+)$""").find(trimmed) ?: continue
            val idx = match.groupValues[1].toIntOrNull()?.minus(1) ?: continue
            val translation = match.groupValues[2].trim()

            if (idx < 0 || idx >= ocrBlocks.size) continue
            if (translation.isBlank() || translation == "[SFX]") continue

            val block = ocrBlocks[idx]
            result.add(OverlayItem(
                id = "${block.x}_${block.y}",
                text = translation,
                x = block.x,
                y = block.y,
                width = block.width,
                height = block.height
            ))
        }

        Log.d(TAG, "Matched ${result.size}/${ocrBlocks.size} translations")
        return result
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1024
        val scale = minOf(
            maxDim.toFloat() / bitmap.width,
            maxDim.toFloat() / bitmap.height,
            1f
        )
        val scaled = if (scale < 1f)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        else bitmap

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        if (scaled != bitmap) scaled.recycle()
        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }

    private fun extractText(responseBody: String): String {
        return try {
            JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: ${e.message}\n${responseBody.take(300)}")
            ""
        }
    }
}

data class OcrBlock(
    val text: String,
    val x: Int, val y: Int,
    val width: Int, val height: Int
)