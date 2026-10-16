package com.almakhedhi.rxreader.core.ai

import android.graphics.Bitmap
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase AI Logic first (when a Firebase project is configured), else the
 * direct Gemini Developer API (when the owner baked in an AI Studio key),
 * else a failure explaining that AI is not configured yet.
 */
@Singleton
class DefaultAiReader @Inject constructor(
    private val firebase: FirebaseAiReader,
    private val direct: DirectGeminiReader,
) : AiReader {

    override suspend fun analyze(images: List<Bitmap>): Result<AnalysisResult> {
        val primary = firebase.analyze(images)
        if (primary.isSuccess) return primary
        if (primary.exceptionOrNull() is AiNotConfiguredException && direct.isConfigured) {
            return direct.analyze(images)
        }
        return primary
    }

    override suspend fun generateJsonText(
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> {
        val primary = firebase.generateJsonText(systemPrompt, userPrompt)
        if (primary.isSuccess) return primary
        if (primary.exceptionOrNull() is AiNotConfiguredException && direct.isConfigured) {
            return direct.generateJsonText(systemPrompt, userPrompt)
        }
        return primary
    }
}
