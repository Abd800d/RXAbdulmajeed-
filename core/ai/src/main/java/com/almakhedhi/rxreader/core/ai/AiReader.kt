package com.almakhedhi.rxreader.core.ai

import android.graphics.Bitmap
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult

/** Thrown when analysis is requested before Firebase is configured. */
class AiNotConfiguredException :
    IllegalStateException("Firebase AI is not configured yet")

/** Reads prescription images into a structured [AnalysisResult]. */
interface AiReader {
    suspend fun analyze(images: List<Bitmap>): Result<AnalysisResult>

    /** Free-form JSON generation (drug cards, chat) with the same model. */
    suspend fun generateJsonText(systemPrompt: String, userPrompt: String): Result<String>
}
