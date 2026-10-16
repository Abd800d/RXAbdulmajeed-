package com.almakhedhi.rxreader.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.almakhedhi.rxreader.core.data.enrich.DrugCardReader
import com.almakhedhi.rxreader.core.data.repo.DrugRepository
import com.almakhedhi.rxreader.core.data.repo.EnrichmentRepository
import com.almakhedhi.rxreader.core.data.repo.OfficialDetails
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background enrichment (PLAN §10): official data + images (+ optional AI
 * card) for one or many drugs. Never throws per item — failures are
 * counted in the output data so bulk runs always finish.
 */
@HiltWorker
class EnrichWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drugs: DrugRepository,
    private val enrich: EnrichmentRepository,
    private val cards: DrugCardReader,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(EnrichWork.KEY_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return Result.failure()
        val withImages = inputData.getBoolean(EnrichWork.KEY_IMAGES, true)
        val withAi = inputData.getBoolean(EnrichWork.KEY_AI, false)
        runCatching { enrich.pruneCache() }

        var enriched = 0
        var imageTotal = 0
        var aiDone = 0
        var aiSkipped = false
        ids.forEachIndexed { index, id ->
            setProgress(
                workDataOf(
                    EnrichWork.PROGRESS to index.toFloat() / ids.size,
                    EnrichWork.CURRENT to id,
                ),
            )
            val drug = runCatching { drugs.getById(id) }.getOrNull()
            val name = drug?.let { it.tradeEn ?: it.tradeAr ?: it.scientific }
            if (drug != null && !name.isNullOrBlank()) {
                val official = runCatching {
                    val best = enrich.searchOfficial(name).firstOrNull()
                    enrich.officialDetails(best?.rxcui, best?.name ?: name)
                }.getOrNull()
                if (official != null) {
                    runCatching {
                        val (updated, contributed) = applyOfficial(drug, official)
                        if (contributed) {
                            drugs.updateEnriched(updated.copy(reviewed = true))
                            enriched++
                        }
                    }
                }
                if (withImages) {
                    val found = runCatching {
                        enrich.searchImages(name, official?.setId).take(IMAGES_PER_DRUG)
                    }.getOrDefault(emptyList())
                    for (image in found) {
                        runCatching {
                            enrich.downloadToDrug(id, image)
                            imageTotal++
                        }
                    }
                }
                if (withAi) {
                    cards.complete(id, official).onSuccess { report ->
                        if (report.quotaDenied) {
                            aiSkipped = true
                        } else if (report.filled.isNotEmpty()) {
                            aiDone++
                        }
                    }
                }
            }
        }
        setProgress(workDataOf(EnrichWork.PROGRESS to 1f, EnrichWork.CURRENT to -1L))
        return Result.success(
            workDataOf(
                EnrichWork.OUT_DONE to ids.size,
                EnrichWork.OUT_ENRICHED to enriched,
                EnrichWork.OUT_IMAGES to imageTotal,
                EnrichWork.OUT_AI to aiDone,
                EnrichWork.OUT_AI_SKIPPED to aiSkipped,
            ),
        )
    }

    /** Fills blank fields from official data; IDs always refresh when present. */
    private fun applyOfficial(
        drug: DrugEntity,
        official: OfficialDetails,
    ): Pair<DrugEntity, Boolean> {
        var out = drug
        var contributed = false
        fun field(current: String?, value: String?, assign: DrugEntity.(String?) -> DrugEntity) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FIELD)
            if (current.isNullOrBlank() && clean != null) {
                out = out.assign(clean)
                contributed = true
            }
        }
        field(drug.scientific, official.genericName) { copy(scientific = it) }
        field(drug.manufacturer, official.manufacturer) { copy(manufacturer = it) }
        field(drug.route, official.route) { copy(route = it) }
        field(drug.dosageForm, official.dosageForm) { copy(dosageForm = it) }
        field(drug.indications, official.indications) { copy(indications = it) }
        field(drug.dosageAdults, official.dosage) { copy(dosageAdults = it) }
        field(drug.contraindications, official.contraindications) {
            copy(contraindications = it)
        }
        field(drug.sideEffects, official.adverse) { copy(sideEffects = it) }
        field(drug.interactions, official.interactions) { copy(interactions = it) }
        field(drug.warnings, official.warnings) { copy(warnings = it) }
        field(drug.packSize, official.packaging) { copy(packSize = it) }
        if (!official.rxcui.isNullOrBlank() && out.rxNormId != official.rxcui) {
            out = out.copy(rxNormId = official.rxcui)
            contributed = true
        }
        if (!official.setId.isNullOrBlank() && out.dailymedId != official.setId) {
            out = out.copy(dailymedId = official.setId)
            contributed = true
        }
        return out to contributed
    }

    companion object {
        const val IMAGES_PER_DRUG = 3
        const val MAX_FIELD = 4000
    }
}
