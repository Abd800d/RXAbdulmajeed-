package com.almakhedhi.rxreader.core.ai

import android.content.Context
import android.graphics.Bitmap
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiReader] backed by Firebase AI Logic (Gemini). Returns a failure with
 * [AiNotConfiguredException] when no Firebase project is configured, so the
 * app stays fully usable offline until the owner wires Firebase in.
 */
@Singleton
class FirebaseAiReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiReader {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun analyze(images: List<Bitmap>): Result<AnalysisResult> =
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) throw AiNotConfiguredException()
            require(images.isNotEmpty()) { "No images to analyze" }
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = MODEL_NAME,
                generationConfig = GenerationConfig.builder()
                    .setResponseMimeType("application/json")
                    .build(),
                systemInstruction = Content.Builder().apply {
                    text(RX_SYSTEM_PROMPT)
                }.build(),
            )
            val prompt = Content.Builder().apply {
                images.forEach { image(it) }
                text(RX_USER_PROMPT)
            }.build()
            val response = model.generateContent(prompt)
            val raw = response.text ?: throw IllegalStateException("Empty AI response")
            json.decodeFromString<AnalysisResult>(stripFences(raw))
        }

    private fun stripFences(raw: String): String {
        val text = raw.trim()
        if (!text.startsWith("```")) return text
        return text.substringAfter("\n").substringBeforeLast("```").trim()
    }

    companion object {
        /** TODO: move to Remote Config once the Firebase project exists. */
        const val MODEL_NAME = "gemini-3.8-flash"
    }
}
