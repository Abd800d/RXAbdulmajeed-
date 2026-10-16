package com.almakhedhi.rxreader.core.network.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GGenerateRequest(
    val system_instruction: GContent? = null,
    val contents: List<GContent>,
    val generationConfig: GGenConfig? = null,
)

@Serializable
data class GContent(
    val role: String? = null,
    val parts: List<GPart>,
)

@Serializable
data class GPart(
    val text: String? = null,
    val inline_data: GBlob? = null,
)

@Serializable
data class GBlob(
    val mime_type: String,
    val data: String,
)

@Serializable
data class GGenConfig(
    val response_mime_type: String? = null,
)

@Serializable
data class GGenerateResponse(
    val candidates: List<GCandidate> = emptyList(),
) {
    fun firstText(): String? =
        candidates.firstOrNull()?.content?.parts
            ?.firstOrNull { !it.text.isNullOrBlank() }
            ?.text?.trim()
}

@Serializable
data class GCandidate(
    val content: GContent? = null,
)
