package com.almakhedhi.rxreader.core.network.openfda

import kotlinx.serialization.Serializable

@Serializable
data class FdaLabelResponse(
    val results: List<FdaLabel> = emptyList(),
)

@Serializable
data class FdaLabel(
    val openfda: FdaOpenfda? = null,
    val indications_and_usage: List<String>? = null,
    val dosage_and_administration: List<String>? = null,
    val contraindications: List<String>? = null,
    val adverse_reactions: List<String>? = null,
    val drug_interactions: List<String>? = null,
    val warnings: List<String>? = null,
    val pregnancy: List<String>? = null,
    val packaging: List<String>? = null,
    val description: List<String>? = null,
) {
    fun first(values: List<String>?): String? =
        values?.firstOrNull { it.isNotBlank() }?.trim()?.take(4000)
}

@Serializable
data class FdaOpenfda(
    val brand_name: List<String>? = null,
    val generic_name: List<String>? = null,
    val substance_name: List<String>? = null,
    val route: List<String>? = null,
    val dosage_form: List<String>? = null,
    val manufacturer_name: List<String>? = null,
    val rxcui: List<String>? = null,
)
