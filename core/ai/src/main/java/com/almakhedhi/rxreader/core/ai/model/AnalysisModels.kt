package com.almakhedhi.rxreader.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured prescription reading. Mirrors the JSON schema in PLAN §23 (أ)
 * so the model is decoded field-for-field, no manual mapping.
 */
@Serializable
data class AnalysisResult(
    @SerialName("language_detected")
    val languageDetected: String = "",
    @SerialName("prescription_meta")
    val meta: PrescriptionMeta = PrescriptionMeta(),
    @SerialName("items")
    val medications: List<AnalyzedDrug> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class PrescriptionMeta(
    @SerialName("doctor_name")
    val doctorName: String? = null,
    val clinic: String? = null,
    val date: String? = null,
)

@Serializable
data class AnalyzedDrug(
    @SerialName("line_index")
    val lineIndex: Int = 0,
    @SerialName("written_text")
    val writtenText: String = "",
    @SerialName("trade_name")
    val tradeName: String? = null,
    @SerialName("scientific_name")
    val scientificName: String? = null,
    val strength: String? = null,
    @SerialName("dosage_form")
    val dosageForm: String = "other",
    val route: String? = null,
    val dose: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    @SerialName("instructions_ar")
    val instructionsAr: String = "",
    @SerialName("instructions_en")
    val instructionsEn: String = "",
    val confidence: Double = 0.0,
    val alternatives: List<DrugAlternative> = emptyList(),
    @SerialName("box_2d")
    val box2d: List<Int> = emptyList(),
    val illegible: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class DrugAlternative(
    @SerialName("trade_name")
    val tradeName: String = "",
    @SerialName("scientific_name")
    val scientificName: String = "",
    val confidence: Double = 0.0,
)
