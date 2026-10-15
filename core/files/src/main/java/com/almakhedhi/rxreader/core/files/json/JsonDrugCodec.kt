package com.almakhedhi.rxreader.core.files.json

import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned JSON drug file (export/import schema). */
@Serializable
data class JsonDrugFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String? = null,
    val count: Int = 0,
    val drugs: List<JsonDrug> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Every field optional: sparse files import fine. */
@Serializable
data class JsonDrug(
    val tradeEn: String? = null,
    val tradeAr: String? = null,
    val scientific: String? = null,
    val actives: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val agent: String? = null,
    val regNo: String? = null,
    val barcode: String? = null,
    val packSize: String? = null,
    val price: Double? = null,
    val priceDate: String? = null,
    val dispensing: String? = null,
    val therapeutic: String? = null,
    val atc: String? = null,
    val indications: String? = null,
    val dosageAdults: String? = null,
    val dosageChildren: String? = null,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val interactions: String? = null,
    val pregnancy: String? = null,
    val warnings: String? = null,
    val storage: String? = null,
    val rxNormId: String? = null,
    val dailymedId: String? = null,
    val reviewed: Boolean = false,
    val aliases: List<String> = emptyList(),
)

object JsonDrugCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        prettyPrint = true
        explicitNulls = false
    }

    private val compact = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /** Parses a file; also accepts a bare JSON array of drugs. */
    fun parse(text: String): List<JsonDrug> {
        val trimmed = text.trim()
        return if (trimmed.startsWith("[")) {
            compact.decodeFromString<List<JsonDrug>>(trimmed)
        } else {
            compact.decodeFromString<JsonDrugFile>(trimmed).drugs
        }
    }

    fun schemaVersionOf(text: String): Int? =
        runCatching {
            compact.decodeFromString<JsonDrugFile>(text.trim()).schemaVersion
        }.getOrNull()

    fun stringify(file: JsonDrugFile): String = json.encodeToString(file)

    fun encodeDrug(drug: JsonDrug): String = compact.encodeToString(drug)

    /** Canonical field map shared with the row pipeline (aliases excluded). */
    fun JsonDrug.toFieldMap(): Map<String, String> =
        buildMap {
            tradeEn?.takeIf { it.isNotBlank() }?.let { put("tradeEn", it) }
            tradeAr?.takeIf { it.isNotBlank() }?.let { put("tradeAr", it) }
            scientific?.takeIf { it.isNotBlank() }?.let { put("scientific", it) }
            actives?.takeIf { it.isNotBlank() }?.let { put("actives", it) }
            strength?.takeIf { it.isNotBlank() }?.let { put("strength", it) }
            dosageForm?.takeIf { it.isNotBlank() }?.let { put("dosageForm", it) }
            route?.takeIf { it.isNotBlank() }?.let { put("route", it) }
            manufacturer?.takeIf { it.isNotBlank() }?.let { put("manufacturer", it) }
            country?.takeIf { it.isNotBlank() }?.let { put("country", it) }
            agent?.takeIf { it.isNotBlank() }?.let { put("agent", it) }
            regNo?.takeIf { it.isNotBlank() }?.let { put("regNo", it) }
            barcode?.takeIf { it.isNotBlank() }?.let { put("barcode", it) }
            packSize?.takeIf { it.isNotBlank() }?.let { put("packSize", it) }
            price?.let { put("price", it.toString()) }
            priceDate?.takeIf { it.isNotBlank() }?.let { put("priceDate", it) }
            dispensing?.takeIf { it.isNotBlank() }?.let { put("dispensing", it) }
            therapeutic?.takeIf { it.isNotBlank() }?.let { put("therapeutic", it) }
            atc?.takeIf { it.isNotBlank() }?.let { put("atc", it) }
            indications?.takeIf { it.isNotBlank() }?.let { put("indications", it) }
            dosageAdults?.takeIf { it.isNotBlank() }?.let { put("dosageAdults", it) }
            dosageChildren?.takeIf { it.isNotBlank() }?.let { put("dosageChildren", it) }
            contraindications?.takeIf { it.isNotBlank() }?.let { put("contraindications", it) }
            sideEffects?.takeIf { it.isNotBlank() }?.let { put("sideEffects", it) }
            interactions?.takeIf { it.isNotBlank() }?.let { put("interactions", it) }
            pregnancy?.takeIf { it.isNotBlank() }?.let { put("pregnancy", it) }
            warnings?.takeIf { it.isNotBlank() }?.let { put("warnings", it) }
            storage?.takeIf { it.isNotBlank() }?.let { put("storage", it) }
            rxNormId?.takeIf { it.isNotBlank() }?.let { put("rxNormId", it) }
            dailymedId?.takeIf { it.isNotBlank() }?.let { put("dailymedId", it) }
            if (reviewed) put("reviewed", "true")
        }

    init {
        // Keep the canonical field list and JSON keys in sync by construction.
        check(DrugColumns.NAME_FIELDS.all { it in DrugColumns.byId })
    }
}
