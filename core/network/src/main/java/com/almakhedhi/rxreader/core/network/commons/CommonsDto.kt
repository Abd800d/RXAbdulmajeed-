package com.almakhedhi.rxreader.core.network.commons

import kotlinx.serialization.Serializable

@Serializable
data class CommonsResponse(
    val query: CommonsQuery? = null,
)

@Serializable
data class CommonsQuery(
    val pages: Map<String, CommonsPage> = emptyMap(),
)

@Serializable
data class CommonsPage(
    val title: String? = null,
    val imageinfo: List<CommonsImage> = emptyList(),
)

@Serializable
data class CommonsImage(
    val url: String? = null,
    val thumburl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val extmetadata: Map<String, CommonsMetaValue> = emptyMap(),
) {
    fun license(): String? =
        extmetadata["LicenseShortName"]?.value
            ?.replace(Regex("<[^>]*>"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun artist(): String? =
        (extmetadata["Artist"]?.value ?: extmetadata["Credit"]?.value)
            ?.replace(Regex("<[^>]*>"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}

@Serializable
data class CommonsMetaValue(
    val value: String? = null,
)
