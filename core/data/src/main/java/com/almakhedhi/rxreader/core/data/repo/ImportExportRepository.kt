package com.almakhedhi.rxreader.core.data.repo

import com.almakhedhi.rxreader.core.database.dao.DrugFilter
import com.almakhedhi.rxreader.core.database.dao.ImportJobDao
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import com.almakhedhi.rxreader.core.files.export.ExportEngine
import com.almakhedhi.rxreader.core.files.ingest.ImportEngine
import com.almakhedhi.rxreader.core.files.json.JsonDrug
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec
import com.almakhedhi.rxreader.core.files.templates.Templates
import com.almakhedhi.rxreader.core.files.xlsx.XlsxReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Import/export facade: format detection, parsing, validation, batched
 * import, filtered export, templates, and Google Sheets CSV download.
 */
@Singleton
class ImportExportRepository @Inject constructor(
    private val importEngine: ImportEngine,
    private val exportEngine: ExportEngine,
    private val jobDao: ImportJobDao,
) {
    companion object {
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }

    enum class Format { CSV, XLSX, JSON }

    data class ParsedTable(
        val format: Format,
        val header: List<String>,
        val rows: List<ImportEngine.DataRow>,
        val encoding: String,
        val delimiter: Char?,
        val mapping: DrugColumns.MappingResult,
        val sheetNames: List<String> = emptyList(),
        val sheetName: String = "",
    )

    fun detectFormat(fileName: String, bytes: ByteArray): Format {
        val lower = fileName.lowercase()
        if (lower.endsWith(".json")) return Format.JSON
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm")) return Format.XLSX
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) return Format.CSV
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return Format.XLSX // PK zip signature.
        }
        val head = bytes.take(4096).toByteArray()
        val text = runCatching { CsvCodec.decode(head).text.trimStart() }.getOrDefault("")
        if (text.startsWith("{") || text.startsWith("[")) return Format.JSON
        return Format.CSV
    }

    fun parseCsv(
        bytes: ByteArray,
        delimiter: Char? = null,
        encoding: String? = null,
    ): ParsedTable {
        val decoded = if (encoding != null) {
            runCatching {
                String(bytes, charset(encoding))
            }.getOrElse { CsvCodec.decode(bytes).text }
        } else {
            CsvCodec.decode(bytes).text
        }
        val usedEncoding = encoding ?: CsvCodec.decode(bytes).encoding
        val firstLine = decoded.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val usedDelimiter = delimiter ?: CsvCodec.detectDelimiter(firstLine)
        val table = CsvCodec.parseText(decoded, usedDelimiter)
        val rows = table.rows.mapIndexed { index, cells ->
            ImportEngine.DataRow(index + 2, cells)
        }
        return ParsedTable(
            format = Format.CSV,
            header = table.header,
            rows = rows,
            encoding = usedEncoding,
            delimiter = usedDelimiter,
            mapping = DrugColumns.autoMap(table.header),
        )
    }

    fun parseXlsx(bytes: ByteArray, sheetName: String? = null): ParsedTable {
        val names = XlsxReader.sheetNames(bytes)
        val sheet = if (sheetName != null) {
            XlsxReader.readSheet(bytes, sheetName)
        } else {
            XlsxReader.readFirstSheet(bytes)
        } ?: XlsxReader.Sheet("", emptyList())
        val header = sheet.rows.firstOrNull()?.cells?.map { it ?: "" } ?: emptyList()
        val rows = sheet.rows.drop(1).map { ImportEngine.DataRow(it.number, it.cells) }
        return ParsedTable(
            format = Format.XLSX,
            header = header,
            rows = rows,
            encoding = "UTF-8",
            delimiter = null,
            mapping = DrugColumns.autoMap(header),
            sheetNames = names,
            sheetName = sheet.name,
        )
    }

    fun parseJson(text: String): Pair<List<JsonDrug>, Int?> =
        JsonDrugCodec.parse(text) to JsonDrugCodec.schemaVersionOf(text)

    /** Downloads a published Google Sheets CSV link (30s timeout, size cap). */
    fun downloadSheetsCsv(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "text/csv,*/*")
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val out = bytesCapped(connection.inputStream.readBytes())
            return out
        } finally {
            connection.disconnect()
        }
    }

    private fun bytesCapped(bytes: ByteArray): ByteArray {
        if (bytes.size > MAX_FILE_BYTES) {
            throw IllegalStateException("File exceeds ${MAX_FILE_BYTES / 1024 / 1024} MB")
        }
        return bytes
    }

    fun checkSize(bytes: ByteArray) {
        bytesCapped(bytes)
    }

    suspend fun validate(
        rows: List<ImportEngine.DataRow>,
        mapping: Map<Int, String>,
    ): ImportEngine.Validation = importEngine.validate(rows, mapping)

    /** Dry-run validation over decoded JSON field maps. */
    suspend fun validateJson(maps: List<Map<String, String>>): ImportEngine.Validation =
        importEngine.validateMaps(maps)

    suspend fun runImport(
        fileName: String,
        format: Format,
        rows: List<ImportEngine.DataRow>,
        mapping: Map<Int, String>,
        policy: ImportEngine.Policy,
        onProgress: (ImportEngine.Progress) -> Unit,
    ): ImportEngine.Report =
        importEngine.runImport(fileName, format.name.lowercase(), rows, mapping, policy, onProgress)

    suspend fun importJson(
        fileName: String,
        drugs: List<JsonDrug>,
        policy: ImportEngine.Policy,
        onProgress: (ImportEngine.Progress) -> Unit,
    ): ImportEngine.Report = importEngine.importJson(fileName, drugs, policy, onProgress)

    suspend fun exportCount(filter: DrugFilter): Int = exportEngine.count(filter)

    suspend fun exportCsv(out: OutputStream, filter: DrugFilter) {
        exportEngine.exportCsv(out, filter)
    }

    suspend fun exportXlsx(out: OutputStream, filter: DrugFilter) {
        exportEngine.exportXlsx(out, filter)
    }

    suspend fun exportJson(out: OutputStream, filter: DrugFilter) {
        exportEngine.exportJson(out, filter)
    }

    suspend fun exportZip(out: OutputStream, filter: DrugFilter) {
        exportEngine.exportZip(out, filter)
    }

    fun writeTemplateCsv(out: OutputStream) = Templates.writeCsv(out)

    fun writeTemplateXlsx(out: OutputStream) = Templates.writeXlsx(out)

    fun writeTemplateJson(out: OutputStream) = Templates.writeJson(out)

    fun observeRecentJobs(limit: Int = 5): Flow<List<ImportJobEntity>> =
        jobDao.observeRecent(limit)
}
