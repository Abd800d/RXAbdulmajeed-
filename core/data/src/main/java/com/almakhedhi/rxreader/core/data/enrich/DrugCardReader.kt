package com.almakhedhi.rxreader.core.data.enrich

import com.almakhedhi.rxreader.core.ai.AiReader
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.OfficialDetails
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.datastore.EnrichPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * "Complete with AI" (PLAN §4.4): fills ONLY the drug's blank fields from
 * a structured drug card, consumes one daily AI call, and always marks the
 * row needs-review ([DrugEntity.reviewed] = false).
 */
@Singleton
class DrugCardReader @Inject constructor(
    private val drugs: DrugRepository,
    private val reader: AiReader,
    private val prefs: EnrichPrefs,
    private val json: Json,
) {
    data class CardReport(
        val filled: List<String>,
        val sources: List<String>,
        val confidence: Double,
        val quotaDenied: Boolean = false,
    )

    suspend fun complete(drugId: Long, official: OfficialDetails? = null): Result<CardReport> {
        val drug = drugs.getById(drugId)
            ?: return Result.failure(NoSuchElementException("drug $drugId"))
        if (!prefs.tryConsumeAi(1)) {
            return Result.success(CardReport(emptyList(), emptyList(), 0.0, quotaDenied = true))
        }
        val raw = reader.generateJsonText(SYSTEM_PROMPT, buildPrompt(drug, official))
            .getOrElse { return Result.failure(it) }
        val card = runCatching { json.decodeFromString<DrugCard>(raw) }
            .getOrElse { return Result.failure(it) }
        val (updated, filled) = applyCard(drug, card)
        drugs.updateEnriched(updated.copy(reviewed = false))
        return Result.success(
            CardReport(
                filled = filled,
                sources = card.sources.filter { it.isNotBlank() }.take(6),
                confidence = card.confidence.coerceIn(0.0, 1.0),
            ),
        )
    }

    private fun applyCard(drug: DrugEntity, card: DrugCard): Pair<DrugEntity, List<String>> {
        val filled = mutableListOf<String>()
        var out = drug
        fun field(
            label: String,
            current: String?,
            value: String?,
            assign: DrugEntity.(String?) -> DrugEntity,
        ) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FIELD)
            if (current.isNullOrBlank() && clean != null) {
                out = out.assign(clean)
                filled += label
            }
        }
        field("scientific", drug.scientific, card.scientific) { copy(scientific = it) }
        field("actives", drug.actives, card.actives) { copy(actives = it) }
        field("strength", drug.strength, card.strength) { copy(strength = it) }
        field("dosageForm", drug.dosageForm, card.dosageForm) { copy(dosageForm = it) }
        field("route", drug.route, card.route) { copy(route = it) }
        field("manufacturer", drug.manufacturer, card.manufacturer) { copy(manufacturer = it) }
        field("therapeutic", drug.therapeutic, card.therapeutic) { copy(therapeutic = it) }
        field("indications", drug.indications, card.indications) { copy(indications = it) }
        field("dosageAdults", drug.dosageAdults, card.dosageAdults) { copy(dosageAdults = it) }
        field("contraindications", drug.contraindications, card.contraindications) {
            copy(contraindications = it)
        }
        field("sideEffects", drug.sideEffects, card.sideEffects) { copy(sideEffects = it) }
        field("interactions", drug.interactions, card.interactions) { copy(interactions = it) }
        field("warnings", drug.warnings, card.warnings) { copy(warnings = it) }
        return out to filled
    }

    private fun buildPrompt(drug: DrugEntity, official: OfficialDetails?): String =
        buildString {
            appendLine("Complete the drug card for this product. Fill ONLY fields you know")
            appendLine("with high confidence; use null for anything uncertain.")
            appendLine(
                "Known: tradeEn=${drug.tradeEn} | tradeAr=${drug.tradeAr} | " +
                    "strength=${drug.strength} | form=${drug.dosageForm} | " +
                    "manufacturer=${drug.manufacturer} | barcode=${drug.barcode} | " +
                    "regNo=${drug.regNo}",
            )
            if (official != null) {
                appendLine(
                    "Official sources say: generic=${official.genericName} | " +
                        "brands=${official.brandNames.take(3)} | substance=${official.substance} | " +
                        "route=${official.route} | form=${official.dosageForm} | " +
                        "maker=${official.manufacturer}",
                )
                appendLine("Official indications: ${official.indications?.take(500)}")
            }
            appendLine(
                "Return JSON with keys: scientific, actives, strength, dosageForm, route, " +
                    "manufacturer, therapeutic, indications, dosageAdults, contraindications, " +
                    "sideEffects, interactions, warnings, sources (array of URLs you relied on), " +
                    "confidence (0..1).",
            )
        }

    companion object {
        const val MAX_FIELD = 4000

        const val SYSTEM_PROMPT =
            "You are a clinical pharmacist assistant. Answer with a single JSON object only, " +
                "no markdown. Prefer official sources (DailyMed, FDA labels, RxNorm). " +
                "Keep Arabic trade names in Arabic script when known."
    }
}
