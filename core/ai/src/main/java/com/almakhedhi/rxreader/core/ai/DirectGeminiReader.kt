package com.almakhedhi.rxreader.core.ai

import android.graphics.Bitmap
import android.util.Base64
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import com.almakhedhi.rxreader.core.network.gemini.GBlob
import com.almakhedhi.rxreader.core.network.gemini.GContent
import com.almakhedhi.rxreader.core.network.gemini.GGenConfig
import com.almakhedhi.rxreader.core.network.gemini.GGenerateRequest
import com.almakhedhi.rxreader.core.network.gemini.GPart
import com.almakhedhi.rxreader.core.network.gemini.GeminiApi
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Gemini Developer API over the native REST endpoint — works with just an
 * AI Studio key (no Firebase project). The key travels in the documented
 * x-goog-api-key header, which accepts both legacy AIza keys and current
 * AQ. auth keys.
 *
 * Key priority: runtime key pasted in Settings first, baked BuildConfig key
 * second. Neither ever leaves the device except to Google's API.
 */
@Singleton
class DirectGeminiReader @Inject constructor(
    private val api: GeminiApi,
    private val prefs: EnrichPrefs,
    @Named("gemini-api-key") private val bakedKey: String,
) : AiReader {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun isConfigured(): Boolean = effectiveKey().isNotBlank()

    private suspend fun effectiveKey(): String {
        val runtime = prefs.settings.first().geminiApiKey.trim()
        return runtime.ifBlank { bakedKey.trim() }
    }

    override suspend fun analyze(images: List<Bitmap>): Result<AnalysisResult> =
        runCatching {
            val key = checkConfigured()
            require(images.isNotEmpty()) { "No images to analyze" }
            val parts = images.take(MAX_IMAGES).map { it.toInlinePart() } +
                GPart(text = RX_USER_PROMPT)
            val body = GGenerateRequest(
                system_instruction = GContent(parts = listOf(GPart(text = RX_SYSTEM_PROMPT))),
                contents = listOf(GContent(role = "user", parts = parts)),
                generationConfig = GGenConfig(response_mime_type = "application/json"),
            )
            val raw = generate(key, body)
            json.decodeFromString<AnalysisResult>(stripFences(raw))
        }

    override suspend fun generateJsonText(
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> =
        runCatching {
            val key = checkConfigured()
            val body = GGenerateRequest(
                system_instruction = GContent(parts = listOf(GPart(text = systemPrompt))),
                contents = listOf(
                    GContent(role = "user", parts = listOf(GPart(text = userPrompt))),
                ),
                generationConfig = GGenConfig(response_mime_type = "application/json"),
            )
            stripFences(generate(key, body))
        }

    private suspend fun generate(key: String, body: GGenerateRequest): String {
        val response = api.generateContent(FirebaseAiReader.MODEL_NAME, key, body)
        return response.firstText()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Empty AI response")
    }

    private suspend fun checkConfigured(): String {
        val key = effectiveKey()
        if (key.isBlank()) throw AiNotConfiguredException()
        return key
    }

    private fun Bitmap.toInlinePart(): GPart {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return GPart(
            inline_data = GBlob(
                mime_type = "image/jpeg",
                data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
            ),
        )
    }

    private fun stripFences(raw: String): String {
        val text = raw.trim()
        if (!text.startsWith("```")) return text
        return text.substringAfter("\n").substringBeforeLast("```").trim()
    }

    companion object {
        const val MAX_IMAGES = 4
        const val JPEG_QUALITY = 85
    }
}
