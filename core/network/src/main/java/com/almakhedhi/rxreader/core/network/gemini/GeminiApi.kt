package com.almakhedhi.rxreader.core.network.gemini

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Native Gemini Developer API (no Firebase project needed). Auth via the
 * documented x-goog-api-key header, which accepts both legacy AIza keys
 * and current AQ. auth keys.
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GGenerateRequest,
    ): GGenerateResponse
}
