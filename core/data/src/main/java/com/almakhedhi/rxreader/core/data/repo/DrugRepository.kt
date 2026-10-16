package com.almakhedhi.rxreader.core.data.repo

import androidx.room.withTransaction
import com.almakhedhi.rxreader.core.data.model.DrugDraft
import com.almakhedhi.rxreader.core.data.model.DrugMatch
import com.almakhedhi.rxreader.core.data.model.MatchVia
import com.almakhedhi.rxreader.core.database.RxDatabase
import com.almakhedhi.rxreader.core.database.dao.DrugDao
import com.almakhedhi.rxreader.core.database.dao.DrugAliasDao
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.dao.DrugImageDao
import com.almakhedhi.rxreader.core.database.dao.DrugQuery
import com.almakhedhi.rxreader.core.database.dao.searchFts
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import com.almakhedhi.rxreader.core.database.entity.SearchText
import com.almakhedhi.rxreader.core.matching.ArabicNormalizer
import com.almakhedhi.rxreader.core.matching.Fuzzy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Drug catalog: browse, FTS search, match pipeline (exact -> alias ->
 * FTS prefix -> fuzzy, PLAN §7), aliases with learning, and trash.
 */
@Singleton
class DrugRepository @Inject constructor(
    private val db: RxDatabase,
    private val drugDao: DrugDao,
    private val aliasDao: DrugAliasDao,
    private val imageDao: DrugImageDao,
    private val images: ImageRepository,
) {
    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val FUZZY_THRESHOLD = 0.82
    }

    // Browse.

    suspend fun listPage(filter: DrugFilter, limit: Int, offset: Int): List<DrugEntity> =
        drugDao.listRaw(DrugQuery.list(filter, limit, offset))

    suspend fun count(filter: DrugFilter): Int =
        drugDao.countRaw(DrugQuery.count(filter))

    /** Invalidation signal: every write bumps some row's updatedAt. */
    fun observeMaxUpdated(): Flow<Long?> = drugDao.observeMaxUpdated()

    data class FilterValues(
        val therapeutics: List<String>,
        val forms: List<String>,
        val manufacturers: List<String>,
    )

    suspend fun filterValues(): FilterValues =
        FilterValues(
            drugDao.distinctTherapeutics(),
            drugDao.distinctForms(),
            drugDao.distinctManufacturers(),
        )

    suspend fun getById(id: Long): DrugEntity? = drugDao.getById(id)

    fun observeById(id: Long): Flow<DrugEntity?> = drugDao.observeById(id)

    suspend fun getByIds(ids: List<Long>): List<DrugEntity> =
        if (ids.isEmpty()) emptyList() else drugDao.getByIds(ids)

    // Search.

    /**
     * Prefix FTS over the denormalized search text with in-memory
     * post-filtering. Falls back to LIKE when FTS rejects the query.
     */
    suspend fun search(query: String, filter: DrugFilter? = null, limit: Int = 200): List<DrugEntity> {
        val match = toFtsQuery(query) ?: return emptyList()
        val hits = try {
            drugDao.searchFts(match, limit)
        } catch (_: Exception) {
            val token = query.replace("%", "").replace("_", "").trim().take(60)
            if (token.isEmpty()) return emptyList()
            drugDao.searchLike(token, limit)
        }
        if (filter == null) return hits
        return hits.filter { matchesFilter(it, filter) }
    }

    private fun toFtsQuery(query: String): String? {
        val tokens = ArabicNormalizer.normalize(query).split(' ')
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun matchesFilter(drug: DrugEntity, filter: DrugFilter): Boolean {
        filter.therapeutic?.let { if (drug.therapeutic != it) return false }
        filter.form?.let { if (drug.dosageForm != it) return false }
        filter.manufacturer?.let { if (drug.manufacturer != it) return false }
        filter.reviewed?.let { if (drug.reviewed != it) return false }
        if (filter.incompleteOnly && !isIncomplete(drug)) return false
        return true
    }

    private fun isIncomplete(drug: DrugEntity): Boolean =
        drug.scientific.isNullOrBlank() || drug.strength.isNullOrBlank() ||
            drug.price == null || drug.manufacturer.isNullOrBlank()

    // Match pipeline.

    /**
     * Exact -> alias -> FTS prefix -> fuzzy (Jaro-Winkler over the FTS
     * shortlist). RxNorm verification arrives with Phase 4.
     */
    suspend fun findMatches(rawQuery: String, limit: Int = 5): List<DrugMatch> {
        val norm = ArabicNormalizer.normalize(rawQuery)
        if (norm.length < 2) return emptyList()
        val best = LinkedHashMap<Long, DrugMatch>()
        fun keep(drug: DrugEntity, score: Double, via: MatchVia) {
            val current = best[drug.id]
            if (current == null || score > current.score) {
                best[drug.id] = DrugMatch(drug, score, via)
            }
        }
        drugDao.findByTradeNorm(norm).forEach { keep(it, 1.0, MatchVia.EXACT) }
        val aliasIds = aliasDao.findDrugIdsByAlias(norm)
        getByIds(aliasIds).forEach { keep(it, 0.98, MatchVia.ALIAS) }
        val ftsHits = try {
            toFtsQuery(rawQuery)?.let { drugDao.searchFts(it, 50) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        ftsHits.forEachIndexed { index, drug ->
            keep(drug, 0.9 - index * 0.002, MatchVia.FTS)
        }
        (ftsHits).forEach { drug ->
            val score = Fuzzy.jaroWinkler(norm, drug.tradeNorm)
            if (score >= FUZZY_THRESHOLD) keep(drug, score, MatchVia.FUZZY)
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    // Write.

    /** Inserts or updates; returns the row id. Blanks stay blank. */
    suspend fun save(draft: DrugDraft): Long {
        val now = System.currentTimeMillis()
        val existing = if (draft.id > 0) drugDao.getById(draft.id) else null
        val tradeNorm = SearchText.tradeNormOf(draft.tradeEn, draft.tradeAr, draft.scientific)
        val entity = DrugEntity(
            id = draft.id,
            tradeEn = draft.tradeEn?.trim()?.takeIf { it.isNotEmpty() },
            tradeAr = draft.tradeAr?.trim()?.takeIf { it.isNotEmpty() },
            scientific = draft.scientific?.trim()?.takeIf { it.isNotEmpty() },
            actives = draft.actives?.trim()?.takeIf { it.isNotEmpty() },
            strength = draft.strength?.trim()?.takeIf { it.isNotEmpty() },
            dosageForm = draft.dosageForm?.trim()?.takeIf { it.isNotEmpty() },
            route = draft.route?.trim()?.takeIf { it.isNotEmpty() },
            manufacturer = draft.manufacturer?.trim()?.takeIf { it.isNotEmpty() },
            country = draft.country?.trim()?.takeIf { it.isNotEmpty() },
            agent = draft.agent?.trim()?.takeIf { it.isNotEmpty() },
            regNo = draft.regNo?.trim()?.takeIf { it.isNotEmpty() },
            barcode = draft.barcode?.trim()?.takeIf { it.isNotEmpty() },
            packSize = draft.packSize?.trim()?.takeIf { it.isNotEmpty() },
            price = draft.price,
            priceDate = draft.priceDate?.trim()?.takeIf { it.isNotEmpty() },
            dispensing = draft.dispensing?.trim()?.takeIf { it.isNotEmpty() },
            therapeutic = draft.therapeutic?.trim()?.takeIf { it.isNotEmpty() },
            atc = draft.atc?.trim()?.takeIf { it.isNotEmpty() },
            indications = draft.indications?.trim()?.takeIf { it.isNotEmpty() },
            dosageAdults = draft.dosageAdults?.trim()?.takeIf { it.isNotEmpty() },
            dosageChildren = draft.dosageChildren?.trim()?.takeIf { it.isNotEmpty() },
            contraindications = draft.contraindications?.trim()?.takeIf { it.isNotEmpty() },
            sideEffects = draft.sideEffects?.trim()?.takeIf { it.isNotEmpty() },
            interactions = draft.interactions?.trim()?.takeIf { it.isNotEmpty() },
            pregnancy = draft.pregnancy?.trim()?.takeIf { it.isNotEmpty() },
            warnings = draft.warnings?.trim()?.takeIf { it.isNotEmpty() },
            storage = draft.storage?.trim()?.takeIf { it.isNotEmpty() },
            rxNormId = draft.rxNormId?.trim()?.takeIf { it.isNotEmpty() },
            dailymedId = draft.dailymedId?.trim()?.takeIf { it.isNotEmpty() },
            source = existing?.source ?: "manual",
            reviewed = draft.reviewed,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            sync = existing?.sync ?: "none",
            tradeNorm = tradeNorm,
            searchNorm = "",
        )
        val id = db.withTransaction {
            val rowId = drugDao.upsert(entity)
            val realId = if (entity.id > 0) entity.id else rowId
            val aliasNorms = aliasDao.listForDrug(realId).map { it.aliasNorm }
            drugDao.upsert(
                entity.copy(
                    id = realId,
                    searchNorm = SearchText.searchNormOf(
                        entity.tradeEn,
                        entity.tradeAr,
                        entity.scientific,
                        entity.manufacturer,
                        entity.barcode,
                        aliasNorms,
                    ),
                ),
            )
            realId
        }
        return id
    }

    /** Entity-level update that refreshes trade/search norms (enrichment path). */
    suspend fun updateEnriched(entity: DrugEntity) {
        val now = System.currentTimeMillis()
        val tradeNorm = SearchText.tradeNormOf(entity.tradeEn, entity.tradeAr, entity.scientific)
        db.withTransaction {
            val aliasNorms = aliasDao.listForDrug(entity.id).map { it.aliasNorm }
            drugDao.upsert(
                entity.copy(
                    tradeNorm = tradeNorm,
                    updatedAt = now,
                    searchNorm = SearchText.searchNormOf(
                        entity.tradeEn,
                        entity.tradeAr,
                        entity.scientific,
                        entity.manufacturer,
                        entity.barcode,
                        aliasNorms,
                    ),
                ),
            )
        }
    }

    suspend fun softDelete(id: Long) {
        drugDao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restore(id: Long) {
        drugDao.restore(id, System.currentTimeMillis())
    }

    suspend fun deleteForever(id: Long) {
        images.deleteByDrug(id)
        drugDao.deleteForever(id)
    }

    suspend fun emptyTrash(): Int {
        val ids = drugDao.trashIds()
        ids.forEach { deleteForever(it) }
        return ids.size
    }

    suspend fun purgeExpiredTrash(): Int {
        val ids = drugDao.trashedIdsBefore(System.currentTimeMillis() - TRASH_RETENTION_MS)
        ids.forEach { deleteForever(it) }
        return ids.size
    }

    fun observeTrash(): Flow<List<DrugEntity>> = drugDao.observeTrash()

    fun observeTrashCount(): Flow<Int> = drugDao.observeTrashCount()

    // Aliases.

    fun observeAliases(drugId: Long): Flow<List<DrugAliasEntity>> =
        aliasDao.observeForDrug(drugId)

    /**
     * Learns an alternative name; returns false when blank, redundant, or
     * already known. Refreshes the drug's FTS text on change.
     */
    suspend fun addAlias(drugId: Long, raw: String, source: String = "manual"): Boolean {
        val norm = ArabicNormalizer.normalize(raw)
        if (norm.isEmpty()) return false
        val drug = drugDao.getById(drugId) ?: return false
        if (norm == drug.tradeNorm) return false
        val now = System.currentTimeMillis()
        val rowId = aliasDao.insert(
            DrugAliasEntity(
                drugId = drugId,
                aliasNorm = norm,
                aliasRaw = raw.trim(),
                source = source,
                createdAt = now,
            ),
        )
        if (rowId < 0) return false
        refreshSearchNorm(drug, now)
        return true
    }

    suspend fun removeAlias(aliasId: Long, drugId: Long) {
        aliasDao.delete(aliasId)
        drugDao.getById(drugId)?.let { refreshSearchNorm(it, System.currentTimeMillis()) }
    }

    private suspend fun refreshSearchNorm(drug: DrugEntity, now: Long) {
        val aliasNorms = aliasDao.listForDrug(drug.id).map { it.aliasNorm }
        drugDao.upsert(
            drug.copy(
                searchNorm = SearchText.searchNormOf(
                    drug.tradeNorm,
                    drug.tradeAr,
                    drug.scientific,
                    drug.manufacturer,
                    drug.barcode,
                    aliasNorms,
                ),
                updatedAt = now,
            ),
        )
    }

    // Images (attach UI arrives with Phase 4; storage is ready now).

    fun observeImages(drugId: Long): Flow<List<DrugImageEntity>> =
        imageDao.observeForDrug(drugId)

    suspend fun imageCount(drugId: Long): Int = imageDao.countForDrug(drugId)
}
