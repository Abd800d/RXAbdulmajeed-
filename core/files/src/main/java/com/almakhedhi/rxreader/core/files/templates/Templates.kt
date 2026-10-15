package com.almakhedhi.rxreader.core.files.templates

import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrug
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrugFile
import com.almakhedhi.rxreader.core.files.xlsx.XlsxWriter
import java.io.OutputStream
import java.time.Instant

/** Downloadable import templates: Arabic headers plus two sample rows. */
object Templates {

    fun header(): List<String> = DrugColumns.ALL.map { it.ar }

    /** Sample cells aligned with [DrugColumns.ALL] order. */
    fun sampleRows(): List<List<String>> =
        listOf(
            listOf(
                "Panadol Extra", "بنادول إكسترا", "Paracetamol + Caffeine", "",
                "500mg + 65mg", "tablet", "oral", "GSK", "United Kingdom", "",
                "", "", "24 tablets", "1500", "", "otc", "Analgesic", "N02BE51",
                "Headache, fever", "1-2 tablets every 6 hours", "", "", "", "", "", "", "",
                "", "", "",
            ),
            listOf(
                "Augmentin", "أوجمنتين", "Amoxicillin + Clavulanate", "",
                "625mg", "tablet", "oral", "GSK", "United Kingdom", "",
                "", "", "30 tablets", "4500", "", "rx", "Antibiotic", "J01CR02",
                "Bacterial infections", "1 tablet every 8 hours", "", "", "", "", "", "", "",
                "", "", "",
            ),
        )

    fun sampleJson(): List<JsonDrug> =
        listOf(
            JsonDrug(
                tradeEn = "Panadol Extra",
                tradeAr = "بنادول إكسترا",
                scientific = "Paracetamol + Caffeine",
                strength = "500mg + 65mg",
                dosageForm = "tablet",
                route = "oral",
                manufacturer = "GSK",
                country = "United Kingdom",
                packSize = "24 tablets",
                price = 1500.0,
                dispensing = "otc",
                therapeutic = "Analgesic",
                atc = "N02BE51",
                indications = "Headache, fever",
                dosageAdults = "1-2 tablets every 6 hours",
            ),
            JsonDrug(
                tradeEn = "Augmentin",
                tradeAr = "أوجمنتين",
                scientific = "Amoxicillin + Clavulanate",
                strength = "625mg",
                dosageForm = "tablet",
                route = "oral",
                manufacturer = "GSK",
                country = "United Kingdom",
                packSize = "30 tablets",
                price = 4500.0,
                dispensing = "rx",
                therapeutic = "Antibiotic",
                atc = "J01CR02",
                indications = "Bacterial infections",
                dosageAdults = "1 tablet every 8 hours",
            ),
        )

    fun writeCsv(out: OutputStream) {
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(CsvCodec.UTF8_BOM)
            writer.write(CsvCodec.stringify(header(), sampleRows()))
        }
    }

    fun writeXlsx(out: OutputStream) {
        XlsxWriter.write(out, "الأدوية", header(), sampleRows().map { row -> row.map { it as String? } }.iterator())
    }

    fun writeJson(out: OutputStream) {
        val drugs = sampleJson()
        val file = JsonDrugFile(
            exportedAt = Instant.now().toString(),
            count = drugs.size,
            drugs = drugs,
        )
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(JsonDrugCodec.stringify(file))
        }
    }
}
