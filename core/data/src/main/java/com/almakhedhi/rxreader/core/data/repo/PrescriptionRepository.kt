package com.almakhedhi.rxreader.core.data.repo

import android.content.Context
import android.graphics.Bitmap
import com.almakhedhi.rxreader.core.ai.model.AnalyzedDrug
import com.almakhedhi.rxreader.core.ai.model.AnalysisResult
import com.almakhedhi.rxreader.core.database.dao.PrescriptionDao
import com.almakhedhi.rxreader.core.database.entity.PrescriptionEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionItemEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionSummary
import com.almakhedhi.rxreader.core.database.entity.PrescriptionWithItems
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Prescription history: persists confirmed analyses with their source
 * image, matches every line against the catalog, and learns aliases
 * from the pharmacist-verified text.
 */
@Singleton
class PrescriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prescriptionDao: PrescriptionDao,
    private val drugRepository: DrugRepository,
) {
    /** Copies the analyzed bitmap into app files; returns the file name. */
    suspend fun saveImage(bitmap: Bitmap): String {
        val dir = File(context.filesDir, "prescriptions").apply { mkdirs() }
        val name = "rx-${UUID.randomUUID()}.jpg"
        File(dir, name).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return name
    }

    fun imageFile(name: String): File = File(File(context.filesDir, "prescriptions"), name)

    /**
     * Saves a confirmed result: matches lines, stores match ids, and
     * learns each verified written line as an alias of its match.
     */
    suspend fun saveConfirmed(result: AnalysisResult, imageFile: String?): Long {
        val now = System.currentTimeMillis()
        val id = prescriptionDao.insert(
            PrescriptionEntity(
                createdAt = now,
                imageFile = imageFile,
                language = result.languageDetected,
                doctor = result.meta.doctorName,
                clinic = result.meta.clinic,
                dateStr = result.meta.date,
                warnings = result.warnings.joinToString("\n"),
            ),
        )
        val items = result.medications.map { drug ->
            val match = drugRepository.findMatches(
                drug.tradeName?.takeIf { it.isNotBlank() } ?: drug.writtenText,
                1,
            ).firstOrNull()
            drug.toEntity(id, match?.drug?.id, match?.score ?: 0.0)
        }
        prescriptionDao.insertItems(items)
        // Alias learning from pharmacist-verified lines.
        items.forEach { item ->
            val drugId = item.matchedDrugId
            if (drugId != null && drugId > 0 && item.written.isNotBlank()) {
                runCatching {
                    drugRepository.addAlias(drugId, item.written, "learned")
                }
            }
        }
        return id
    }

    fun observeSummaries(): Flow<List<PrescriptionSummary>> =
        prescriptionDao.observeSummaries()

    fun observeRecent(limit: Int): Flow<List<PrescriptionSummary>> =
        prescriptionDao.observeRecent(limit)

    suspend fun getDetail(id: Long): PrescriptionWithItems? =
        prescriptionDao.getWithItems(id)

    suspend fun delete(id: Long) {
        val existing = prescriptionDao.getById(id)
        prescriptionDao.delete(id)
        existing?.imageFile?.let { runCatching { imageFile(it).delete() } }
    }

    private fun AnalyzedDrug.toEntity(
        prescriptionId: Long,
        matchedDrugId: Long?,
        matchScore: Double,
    ): PrescriptionItemEntity =
        PrescriptionItemEntity(
            prescriptionId = prescriptionId,
            lineIndex = lineIndex,
            written = writtenText,
            trade = tradeName,
            scientific = scientificName,
            strength = strength,
            form = dosageForm,
            route = route,
            dose = dose,
            frequency = frequency,
            duration = duration,
            instructionsAr = instructionsAr,
            instructionsEn = instructionsEn,
            confidence = confidence,
            illegible = illegible,
            notes = notes,
            boxJson = box2d.joinToString(",", "[", "]"),
            alternatives = alternatives.take(3).joinToString(", ") {
                "${it.tradeName} (${(it.confidence * 100).toInt()}%)"
            },
            matchedDrugId = matchedDrugId,
            matchScore = matchScore,
        )
}
