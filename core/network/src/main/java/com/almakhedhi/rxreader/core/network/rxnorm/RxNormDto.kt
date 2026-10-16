package com.almakhedhi.rxreader.core.network.rxnorm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RxApproxResponse(
    val approximateGroup: RxApproxGroup? = null,
)

@Serializable
data class RxApproxGroup(
    val inputTerm: String? = null,
    val candidate: List<RxCandidate> = emptyList(),
)

@Serializable
data class RxCandidate(
    val rxcui: String? = null,
    val name: String? = null,
    val score: JsonElement? = null,
    val rank: JsonElement? = null,
) {
    /** Score arrives as a string ("100") or a number depending on the call. */
    fun scoreValue(): Double =
        (score as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
}

@Serializable
data class RxPropertiesResponse(
    val properties: RxProperties? = null,
)

@Serializable
data class RxProperties(
    val rxcui: String? = null,
    val name: String? = null,
    val synonym: String? = null,
    val tty: String? = null,
)

@Serializable
data class RxRelatedResponse(
    val relatedGroup: RxRelatedGroup? = null,
)

@Serializable
data class RxRelatedGroup(
    val rxcui: String? = null,
    val name: String? = null,
    val conceptGroup: List<RxConceptGroup> = emptyList(),
)

@Serializable
data class RxConceptGroup(
    val tty: String? = null,
    val conceptProperties: List<RxConcept> = emptyList(),
)

@Serializable
data class RxConcept(
    val rxcui: String? = null,
    val name: String? = null,
    val synonym: String? = null,
    val tty: String? = null,
)
