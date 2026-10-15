package com.almakhedhi.rxreader.core.files.export

import com.almakhedhi.rxreader.core.database.dao.DrugDao
import com.almakhedhi.rxreader.core.database.dao.DrugAliasDao
import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.dao.DrugQuery
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrug
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrugFile
import com.almakhedhi.rxreader.core.files.xlsx.XlsxWriter
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filtered exports (CSV/XLSX/JSON/ZIP). CSV and JSON stream page by page;
 * XLSX collects row strings first (entities are discarded per page) because
 * the writer is synchronous while Room paging is suspending.
 */
@Singleton
class ExportEngine @Inject constructor(
    private val drugDao: DrugDao,
    private val aliasDao: DrugAliasDao,
) {
    companion object {
        const val PAGE_SIZE = 2000
    }

    suspend fun count(filter: DrugFilter): Int =
        drugDao.countRaw(DrugQuery.count(filter))

    suspend fun exportCsv(out: OutputStream, filter: DrugFilter) {
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(CsvCodec.UTF8_BOM)
            writer.write(CsvCodec.stringifyRow(DrugColumns.ALL.map { it.ar }))
            forEachPage(filter) { page ->
                page.forEach { writer.write(CsvCodec.stringifyRow(rowOf(it).map { cell -> cell ?: "" })) }
            }
        }
    }

    suspend fun exportXlsx(out: OutputStream, filter: DrugFilter) {
        val rows = mutableListOf<List<String?>>()
        forEachPage(filter) { page ->
            page.forEach { rows.add(rowOf(it)) }
        }
        XlsxWriter.write(out, "الأدوية", DrugColumns.ALL.map { it.ar }, rows.iterator())
    }

    suspend fun exportJson(out: OutputStream, filter: DrugFilter) {
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeJsonBody(writer, filter)
        }
    }

    suspend fun exportZip(out: OutputStream, filter: DrugFilter) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("rxreader-export.json"))
            zip.bufferedWriter(Charsets.UTF_8).use { writer ->
                writeJsonBody(writer, filter)
                writer.flush()
            }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("images/README.txt"))
            zip.write(
                "Drug product images are stored here by Phase 4 (online search and images).\n".toByteArray(),
            )
            zip.closeEntry()
        }
    }

    private suspend fun writeJsonBody(writer: Appendable, filter: DrugFilter) {
        val total = count(filter)
        writer.append("{\"schemaVersion\":${JsonDrugFile.SCHEMA_VERSION},")
        writer.append("\"exportedAt\":\"${Instant.now()}\",")
        writer.append("\"count\":$total,\"drugs\":[")
        var first = true
        forEachPage(filter) { page ->
            page.forEach { entity ->
                if (!first) writer.append(',')
                first = false
                val aliases = aliasDao.listForDrug(entity.id).map { it.aliasRaw }
                writer.append(JsonDrugCodec.encodeDrug(toJsonDrug(entity, aliases)))
            }
        }
        writer.append("]}")
    }

    private suspend fun forEachPage(filter: DrugFilter, block: suspend (List<DrugEntity>) -> Unit) {
        var offset = 0
        while (true) {
            val page = drugDao.listRaw(DrugQuery.list(filter, PAGE_SIZE, offset))
            if (page.isEmpty()) break
            block(page)
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
    }

    /** Entity -> cells in [DrugColumns.ALL] order (canonical codes round-trip). */
    private fun rowOf(e: DrugEntity): List<String?> =
        listOf(
            e.tradeEn,
            e.tradeAr,
            e.scientific,
            e.actives,
            e.strength,
            e.dosageForm,
            e.route,
            e.manufacturer,
            e.country,
            e.agent,
            e.regNo,
            e.barcode,
            e.packSize,
            e.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() },
            e.priceDate,
            e.dispensing,
            e.therapeutic,
            e.atc,
            e.indications,
            e.dosageAdults,
            e.dosageChildren,
            e.contraindications,
            e.sideEffects,
            e.interactions,
            e.pregnancy,
            e.warnings,
            e.storage,
            e.rxNormId,
            e.dailymedId,
            if (e.reviewed) "TRUE" else "",
        )

    private fun toJsonDrug(e: DrugEntity, aliases: List<String>): JsonDrug =
        JsonDrug(
            tradeEn = e.tradeEn,
            tradeAr = e.tradeAr,
            scientific = e.scientific,
            actives = e.actives,
            strength = e.strength,
            dosageForm = e.dosageForm,
            route = e.route,
            manufacturer = e.manufacturer,
            country = e.country,
            agent = e.agent,
            regNo = e.regNo,
            barcode = e.barcode,
            packSize = e.packSize,
            price = e.price,
            priceDate = e.priceDate,
            dispensing = e.dispensing,
            therapeutic = e.therapeutic,
            atc = e.atc,
            indications = e.indications,
            dosageAdults = e.dosageAdults,
            dosageChildren = e.dosageChildren,
            contraindications = e.contraindications,
            sideEffects = e.sideEffects,
            interactions = e.interactions,
            pregnancy = e.pregnancy,
            warnings = e.warnings,
            storage = e.storage,
            rxNormId = e.rxNormId,
            dailymedId = e.dailymedId,
            reviewed = e.reviewed,
            aliases = aliases,
        )
}
