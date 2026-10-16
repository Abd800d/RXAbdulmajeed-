package com.almakhedhi.rxreader.core.network.dailymed

import kotlinx.serialization.Serializable

@Serializable
data class DmSplListResponse(
    val data: List<DmSpl> = emptyList(),
)

@Serializable
data class DmSpl(
    val setid: String? = null,
    val title: String? = null,
    val published_date: String? = null,
    val spl_version: String? = null,
) {
    fun labelUrl(): String? =
        setid?.takeIf { it.isNotBlank() }?.let {
            "https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=$it"
        }
}

@Serializable
data class DmMediaResponse(
    val data: DmMediaData? = null,
)

@Serializable
data class DmMediaData(
    val setid: String? = null,
    val title: String? = null,
    val published_date: String? = null,
    val media: List<DmMediaFile> = emptyList(),
)

@Serializable
data class DmMediaFile(
    val name: String? = null,
    val mime_type: String? = null,
    val url: String? = null,
)
