package com.almakhedhi.rxreader.core.data.enrich

import kotlinx.serialization.Serializable

/**
 * Structured drug facts from the AI (PLAN §4.4). The model must return
 * null for anything uncertain — unknown stays null, never invented.
 */
@Serializable
data class DrugCard(
    val scientific: String? = null,
    val actives: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    val manufacturer: String? = null,
    val therapeutic: String? = null,
    val indications: String? = null,
    val dosageAdults: String? = null,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val interactions: String? = null,
    val warnings: String? = null,
    val sources: List<String> = emptyList(),
    val confidence: Double = 0.0,
)
