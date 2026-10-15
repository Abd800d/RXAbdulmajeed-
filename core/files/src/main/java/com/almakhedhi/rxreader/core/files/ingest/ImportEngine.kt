package com.almakhedhi.rxreader.core.files.ingest

import androidx.room.withTransaction
import com.almakhedhi.rxreader.core.database.RxDatabase
import com.almakhedhi.rxreader.core.database.dao.DrugDao
import com.almakhedhi.rxreader.core.database.dao.DrugAliasDao
import com.almakhedhi.rxreader.core.database.dao.ImportJobDao
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import com.almakhedhi.rxreader.core.database.entity.SearchText
import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrug
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec.toFieldMap
import com.almakhedhi.rxreader.core.files.xlsx.Xlsx
import com.almakhedhi.rxreader.core.matching.ArabicNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Row validation, duplicate detection, and batched upsert for imports.
 * Duplicate key priority (PLAN §9): barcode -> registration no ->
 * normalized name + strength. Blank file cells never overwrite data.
 */
@Singleton
class ImportEngine @Inject constructor(
    private val db: RxDatabase,
    private val drugDao: DrugDao,
    private val aliasDao: DrugAliasDao,
    private val jobDao: ImportJobDao,
) {
    enum class Policy { SKIP, UPDATE, MERGE }

    /** One parsed source row with its original (1-based) file row number. */
    data class DataRow(val number: Int, val cells: List<String?>)

    data class RowProblem(val rowNumber: Int, val reason: String)

    data class Validation(
        val totalRows: Int,
        val emptyRows: Int,
        val invalidSample: List<RowProblem>,
        val invalidCount: Int,
        val duplicateCount: Int,
        val newCount: Int,
    )

    data class Progress(
        val processed: Int,
        val total: Int,
        val added: Int,
        val updated: Int,
        val skipped: Int,
        val errors: Int,
    )

    data class Report(
        val jobId: Long,
        val total: Int,
        val added: Int,
        val updated: Int,
        val skipped: Int,
        val errors: Int,
        val errorCsv: String,
        val durationMs: Long,
    )

    private data class KeySets(
        val byBarcode: MutableMap<String, Long>,
        val byRegNo: MutableMap<String, Long>,
        val byNameStrength: MutableMap<Pair<String, String>, Long>,
    )

    /**
     * Dry-run over parsed rows. Duplicate counts include in-file repeats,
     * which are always skipped no matter the policy.
     */
    suspend fun validate(
        rows: List<DataRow>,
        mapping: Map<Int, String>,
    ): Validation {
        val keys = loadKeySets()
        var empty = 0
        var duplicates = 0
        var fresh = 0
        val invalidSample = mutableListOf<RowProblem>()
        var invalidCount = 0
        rows.forEach { row ->
            val fields = extract(row, mapping)
            if (fields.isEmpty()) {
                empty++
                return@forEach
            }
            val problem = validateFields(fields)
            if (problem != null) {
                invalidCount++
                if (invalidSample.size < 100) {
                    invalidSample.add(RowProblem(row.number, problem))
                }
                return@forEach
            }
            if (matchKey(fields, keys) != null) duplicates++ else fresh++
            rememberKeys(fields, -1L, keys)
        }
        return Validation(rows.size, empty, invalidSample, invalidCount, duplicates, fresh)
    }

    /** Map-based validation for JSON (synthetic row numbers: index + 2). */
    suspend fun validateMaps(maps: List<Map<String, String>>): Validation {
        val keys = loadKeySets()
        var empty = 0
        var duplicates = 0
        var fresh = 0
        val invalidSample = mutableListOf<RowProblem>()
        var invalidCount = 0
        maps.forEachIndexed { index, fields ->
            val number = index + 2
            if (fields.isEmpty()) {
                empty++
                return@forEachIndexed
            }
            val problem = validateFields(fields)
            if (problem != null) {
                invalidCount++
                if (invalidSample.size < 100) {
                    invalidSample.add(RowProblem(number, problem))
                }
                return@forEachIndexed
            }
            if (matchKey(fields, keys) != null) duplicates++ else fresh++
            rememberKeys(fields, -1L, keys)
        }
        return Validation(maps.size, empty, invalidSample, invalidCount, duplicates, fresh)
    }

    suspend fun runImport(
        fileName: String,
        format: String,
        rows: List<DataRow>,
        mapping: Map<Int, String>,
        policy: Policy,
        onProgress: (Progress) -> Unit,
    ): Report {
        val started = System.currentTimeMillis()
        val jobId = jobDao.insert(
            ImportJobEntity(
                fileName = fileName,
                format = format,
                policy = policy.name,
                totalRows = rows.size,
                startedAt = started,
            ),
        )
        return try {
            val keys = loadKeySets()
            var added = 0
            var updated = 0
            var skipped = 0
            var errors = 0
            val problems = mutableListOf<RowProblem>()
            val batch = mutableListOf<DrugEntity>()
            val now = System.currentTimeMillis()
            rows.forEachIndexed { index, row ->
                val fields = extract(row, mapping)
                if (fields.isEmpty()) {
                    skipped++
                } else {
                    val problem = validateFields(fields)
                    when {
                        problem != null -> {
                            errors++
                            if (problems.size < 5000) {
                                problems.add(RowProblem(row.number, problem))
                            }
                        }
                        else -> {
                            val matchId = matchKey(fields, keys)
                            when {
                                matchId == null -> {
                                    batch.add(newEntity(fields, now))
                                    added++
                                }
                                matchId < 0 -> skipped++ // In-file duplicate.
                                policy == Policy.SKIP -> skipped++
                                else -> {
                                    val existing = drugDao.getById(matchId)
                                    if (existing == null) {
                                        batch.add(newEntity(fields, now))
                                        added++
                                    } else {
                                        batch.add(applyPolicy(existing, fields, policy, now))
                                        updated++
                                    }
                                }
                            }
                            rememberKeys(fields, matchId ?: -2L, keys)
                        }
                    }
                }
                if (batch.size >= 500) {
                    val chunk = batch.toList()
                    batch.clear()
                    db.withTransaction { drugDao.upsertAll(chunk) }
                }
                if ((index + 1) % 500 == 0 || index == rows.lastIndex) {
                    publish(jobId, fileName, format, policy, rows.size, index + 1, added, updated, skipped, errors, started, onProgress)
                }
            }
            if (batch.isNotEmpty()) {
                val chunk = batch.toList()
                batch.clear()
                db.withTransaction { drugDao.upsertAll(chunk) }
            }
            finish(
                jobId, fileName, format, policy, rows.size, added, updated,
                skipped, errors, problems, started, ImportJobEntity.STATUS_DONE,
                onProgress,
            )
        } catch (e: Exception) {
            finish(
                jobId, fileName, format, policy, rows.size, 0, 0, 0, 0,
                emptyList(), started, ImportJobEntity.STATUS_FAILED, onProgress,
            )
            throw e
        }
    }

    suspend fun importJson(
        fileName: String,
        drugs: List<JsonDrug>,
        policy: Policy,
        onProgress: (Progress) -> Unit,
    ): Report {
        val started = System.currentTimeMillis()
        val jobId = jobDao.insert(
            ImportJobEntity(
                fileName = fileName,
                format = "json",
                policy = policy.name,
                totalRows = drugs.size,
                startedAt = started,
            ),
        )
        return try {
            val keys = loadKeySets()
            var added = 0
            var updated = 0
            var skipped = 0
            var errors = 0
            val problems = mutableListOf<RowProblem>()
            val aliasQueue = mutableListOf<Pair<Long, List<String>>>()
            val now = System.currentTimeMillis()
            drugs.forEachIndexed { index, drug ->
                val number = index + 2
                val fields = drug.toFieldMap()
                if (fields.isEmpty()) {
                    skipped++
                } else {
                    val problem = validateFields(fields)
                    when {
                        problem != null -> {
                            errors++
                            if (problems.size < 5000) {
                                problems.add(RowProblem(number, problem))
                            }
                        }
                        else -> {
                            val matchId = matchKey(fields, keys)
                            val id = when {
                                matchId == null -> {
                                    added++
                                    drugDao.upsert(newEntity(fields, now))
                                }
                                matchId < 0 -> {
                                    skipped++
                                    null
                                }
                                policy == Policy.SKIP -> {
                                    skipped++
                                    null
                                }
                                else -> {
                                    val existing = drugDao.getById(matchId)
                                    if (existing == null) {
                                        added++
                                        drugDao.upsert(newEntity(fields, now))
                                    } else {
                                        updated++
                                        drugDao.upsert(applyPolicy(existing, fields, policy, now))
                                        matchId
                                    }
                                }
                            }
                            if (id != null && id > 0 && drug.aliases.isNotEmpty()) {
                                aliasQueue.add(id to drug.aliases)
                            }
                            rememberKeys(fields, matchId ?: -2L, keys)
                        }
                    }
                }
                if ((index + 1) % 200 == 0 || index == drugs.lastIndex) {
                    onProgress(Progress(index + 1, drugs.size, added, updated, skipped, errors))
                }
            }
            refreshJsonAliases(aliasQueue, now)
            finish(
                jobId, fileName, "json", policy, drugs.size, added, updated,
                skipped, errors, problems, started, ImportJobEntity.STATUS_DONE,
                onProgress,
            )
        } catch (e: Exception) {
            finish(
                jobId, fileName, "json", policy, drugs.size, 0, 0, 0, 0,
                emptyList(), started, ImportJobEntity.STATUS_FAILED, onProgress,
            )
            throw e
        }
    }

    private suspend fun publish(
        jobId: Long,
        fileName: String,
        format: String,
        policy: Policy,
        total: Int,
        processed: Int,
        added: Int,
        updated: Int,
        skipped: Int,
        errors: Int,
        started: Long,
        onProgress: (Progress) -> Unit,
    ) {
        onProgress(Progress(processed, total, added, updated, skipped, errors))
        jobDao.update(
            ImportJobEntity(
                id = jobId,
                fileName = fileName,
                format = format,
                policy = policy.name,
                status = ImportJobEntity.STATUS_RUNNING,
                totalRows = total,
                processedRows = processed,
                added = added,
                updated = updated,
                skipped = skipped,
                errors = errors,
                startedAt = started,
                finishedAt = 0,
            ),
        )
    }

    private suspend fun finish(
        jobId: Long,
        fileName: String,
        format: String,
        policy: Policy,
        total: Int,
        added: Int,
        updated: Int,
        skipped: Int,
        errors: Int,
        problems: List<RowProblem>,
        started: Long,
        status: String,
        onProgress: (Progress) -> Unit,
    ): Report {
        val finished = System.currentTimeMillis()
        jobDao.update(
            ImportJobEntity(
                id = jobId,
                fileName = fileName,
                format = format,
                policy = policy.name,
                status = status,
                totalRows = total,
                processedRows = total,
                added = added,
                updated = updated,
                skipped = skipped,
                errors = errors,
                startedAt = started,
                finishedAt = finished,
            ),
        )
        onProgress(Progress(total, total, added, updated, skipped, errors))
        return Report(
            jobId, total, added, updated, skipped, errors,
            errorCsv(problems), finished - started,
        )
    }

    private suspend fun refreshJsonAliases(
        queue: List<Pair<Long, List<String>>>,
        now: Long,
    ) {
        if (queue.isEmpty()) return
        queue.forEach { (drugId, aliases) ->
            aliases.forEach { raw ->
                val norm = ArabicNormalizer.normalize(raw)
                if (norm.isNotEmpty()) {
                    aliasDao.insert(
                        DrugAliasEntity(
                            drugId = drugId,
                            aliasNorm = norm,
                            aliasRaw = raw.trim(),
                            source = "import",
                            createdAt = now,
                        ),
                    )
                }
            }
        }
        queue.map { it.first }.toSet().chunked(500).forEach { chunk ->
            val refreshed = drugDao.getByIds(chunk).map { entity ->
                val aliasNorms = aliasDao.listForDrug(entity.id).map { it.aliasNorm }
                entity.copy(
                    searchNorm = SearchText.searchNormOf(
                        entity.tradeEn,
                        entity.tradeAr,
                        entity.scientific,
                        entity.manufacturer,
                        entity.barcode,
                        aliasNorms,
                    ),
                    updatedAt = now,
                )
            }
            db.withTransaction { drugDao.upsertAll(refreshed) }
        }
    }

    private fun errorCsv(problems: List<RowProblem>): String =
        CsvCodec.stringify(
            listOf("row", "reason"),
            problems.map { listOf(it.rowNumber.toString(), it.reason) },
        )

    /** Column cells -> non-blank canonical fields (with date normalization). */
    private fun extract(row: DataRow, mapping: Map<Int, String>): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        mapping.forEach { (col, fieldId) ->
            val raw = row.cells.getOrNull(col)?.trim()
            if (!raw.isNullOrEmpty()) fields[fieldId] = raw
        }
        fields["priceDate"]?.let { raw ->
            val serial = raw.toDoubleOrNull()
            if (serial != null) {
                Xlsx.excelSerialToIso(serial)?.let { fields["priceDate"] = it }
            }
        }
        return fields
    }

    private fun validateFields(fields: Map<String, String>): String? {
        val hasName = DrugColumns.NAME_FIELDS.any { !fields[it].isNullOrBlank() }
        if (!hasName) return "missing name"
        fields["price"]?.let {
            if (DrugColumns.parsePrice(it) == null) return "invalid price: $it"
        }
        return null
    }

    private suspend fun loadKeySets(): KeySets {
        val byBarcode = mutableMapOf<String, Long>()
        val byRegNo = mutableMapOf<String, Long>()
        val byNameStrength = mutableMapOf<Pair<String, String>, Long>()
        drugDao.allKeys().forEach { key ->
            key.barcode?.takeIf { it.isNotBlank() }?.let { byBarcode[it] = key.id }
            key.regNo?.takeIf { it.isNotBlank() }?.let { byRegNo[it] = key.id }
            byNameStrength[key.tradeNorm to ArabicNormalizer.normalize(key.strength ?: "")] = key.id
        }
        return KeySets(byBarcode, byRegNo, byNameStrength)
    }

    private fun matchKey(fields: Map<String, String>, keys: KeySets): Long? {
        fields["barcode"]?.takeIf { it.isNotBlank() }?.let {
            keys.byBarcode[it]?.let { id -> return id }
        }
        fields["regNo"]?.takeIf { it.isNotBlank() }?.let {
            keys.byRegNo[it]?.let { id -> return id }
        }
        val name = SearchText.tradeNormOf(fields["tradeEn"], fields["tradeAr"], fields["scientific"])
        val strength = ArabicNormalizer.normalize(fields["strength"] ?: "")
        return keys.byNameStrength[name to strength]
    }

    private fun rememberKeys(fields: Map<String, String>, id: Long, keys: KeySets) {
        fields["barcode"]?.takeIf { it.isNotBlank() }?.let {
            keys.byBarcode.putIfAbsent(it, id)
        }
        fields["regNo"]?.takeIf { it.isNotBlank() }?.let {
            keys.byRegNo.putIfAbsent(it, id)
        }
        val name = SearchText.tradeNormOf(fields["tradeEn"], fields["tradeAr"], fields["scientific"])
        val strength = ArabicNormalizer.normalize(fields["strength"] ?: "")
        keys.byNameStrength.putIfAbsent(name to strength, id)
    }

    private fun newEntity(fields: Map<String, String>, now: Long): DrugEntity {
        val tradeEn = fields["tradeEn"]
        val tradeAr = fields["tradeAr"]
        val scientific = fields["scientific"]
        return DrugEntity(
            tradeEn = tradeEn,
            tradeAr = tradeAr,
            scientific = scientific,
            actives = fields["actives"],
            strength = fields["strength"],
            dosageForm = fields["dosageForm"]?.let { DrugColumns.canonicalForm(it) },
            route = fields["route"],
            manufacturer = fields["manufacturer"],
            country = fields["country"],
            agent = fields["agent"],
            regNo = fields["regNo"],
            barcode = fields["barcode"],
            packSize = fields["packSize"],
            price = fields["price"]?.let { DrugColumns.parsePrice(it) },
            priceDate = fields["priceDate"],
            dispensing = fields["dispensing"]?.let { DrugColumns.canonicalDispensing(it) }?.takeIf { it.isNotEmpty() },
            therapeutic = fields["therapeutic"],
            atc = fields["atc"],
            indications = fields["indications"],
            dosageAdults = fields["dosageAdults"],
            dosageChildren = fields["dosageChildren"],
            contraindications = fields["contraindications"],
            sideEffects = fields["sideEffects"],
            interactions = fields["interactions"],
            pregnancy = fields["pregnancy"],
            warnings = fields["warnings"],
            storage = fields["storage"],
            rxNormId = fields["rxNormId"],
            dailymedId = fields["dailymedId"],
            source = "import",
            reviewed = fields["reviewed"]?.let { DrugColumns.parseBool(it) } ?: false,
            createdAt = now,
            updatedAt = now,
            tradeNorm = SearchText.tradeNormOf(tradeEn, tradeAr, scientific),
            searchNorm = SearchText.searchNormOf(
                tradeEn, tradeAr, scientific, fields["manufacturer"], fields["barcode"],
            ),
        )
    }

    private fun applyPolicy(
        existing: DrugEntity,
        fields: Map<String, String>,
        policy: Policy,
        now: Long,
    ): DrugEntity {
        fun pick(current: String?, fieldId: String, transform: (String) -> String? = { it }): String? {
            val incoming = fields[fieldId]?.let(transform)
            return when (policy) {
                Policy.UPDATE -> incoming ?: current
                Policy.MERGE -> if (current.isNullOrBlank()) incoming ?: current else current
                Policy.SKIP -> current
            }
        }
        val tradeEn = pick(existing.tradeEn, "tradeEn")
        val tradeAr = pick(existing.tradeAr, "tradeAr")
        val scientific = pick(existing.scientific, "scientific")
        val manufacturer = pick(existing.manufacturer, "manufacturer")
        val barcode = pick(existing.barcode, "barcode")
        val price = fields["price"]?.let { DrugColumns.parsePrice(it) }
        return existing.copy(
            tradeEn = tradeEn,
            tradeAr = tradeAr,
            scientific = scientific,
            actives = pick(existing.actives, "actives"),
            strength = pick(existing.strength, "strength"),
            dosageForm = pick(existing.dosageForm, "dosageForm") { DrugColumns.canonicalForm(it) },
            route = pick(existing.route, "route"),
            manufacturer = manufacturer,
            country = pick(existing.country, "country"),
            agent = pick(existing.agent, "agent"),
            regNo = pick(existing.regNo, "regNo"),
            barcode = barcode,
            packSize = pick(existing.packSize, "packSize"),
            price = when (policy) {
                Policy.UPDATE -> price ?: existing.price
                Policy.MERGE -> existing.price ?: price
                Policy.SKIP -> existing.price
            },
            priceDate = pick(existing.priceDate, "priceDate"),
            dispensing = pick(existing.dispensing, "dispensing") {
                DrugColumns.canonicalDispensing(it).takeIf { code -> code.isNotEmpty() }
            },
            therapeutic = pick(existing.therapeutic, "therapeutic"),
            atc = pick(existing.atc, "atc"),
            indications = pick(existing.indications, "indications"),
            dosageAdults = pick(existing.dosageAdults, "dosageAdults"),
            dosageChildren = pick(existing.dosageChildren, "dosageChildren"),
            contraindications = pick(existing.contraindications, "contraindications"),
            sideEffects = pick(existing.sideEffects, "sideEffects"),
            interactions = pick(existing.interactions, "interactions"),
            pregnancy = pick(existing.pregnancy, "pregnancy"),
            warnings = pick(existing.warnings, "warnings"),
            storage = pick(existing.storage, "storage"),
            rxNormId = pick(existing.rxNormId, "rxNormId"),
            dailymedId = pick(existing.dailymedId, "dailymedId"),
            reviewed = fields["reviewed"]?.let { DrugColumns.parseBool(it) } ?: existing.reviewed,
            updatedAt = now,
            tradeNorm = SearchText.tradeNormOf(tradeEn, tradeAr, scientific),
            searchNorm = SearchText.searchNormOf(tradeEn, tradeAr, scientific, manufacturer, barcode),
        )
    }
}
