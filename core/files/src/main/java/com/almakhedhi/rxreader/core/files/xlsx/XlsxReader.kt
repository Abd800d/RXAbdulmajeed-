package com.almakhedhi.rxreader.core.files.xlsx

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Minimal streaming xlsx reader for tabular drug data. Handles shared and
 * inline strings, numbers, booleans, and sparse rows/cells. Styles are
 * ignored (date-formatted numbers arrive raw and are converted later).
 */
object XlsxReader {

    data class Row(val number: Int, val cells: List<String?>)
    data class Sheet(val name: String, val rows: List<Row>)

    const val MAX_ROWS = 200_000

    fun readFirstSheet(bytes: ByteArray): Sheet {
        val names = sheetNames(bytes)
        return readSheet(bytes, names.firstOrNull() ?: "") ?: Sheet("", emptyList())
    }

    fun sheetNames(bytes: ByteArray): List<String> {
        val entries = unzip(bytes)
        val workbook = entries["xl/workbook.xml"] ?: return fallbackSheets(entries)
        val rels = parseWorkbookRels(entries["xl/_rels/workbook.xml.rels"])
        val names = mutableListOf<Pair<String, String>>()
        parser(workbook).use { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.tag() == "sheet") {
                    val name = p.attr("name") ?: continue
                    val file = rels[p.attr("id")] ?: continue
                    names.add(name to file)
                }
            }
        }
        if (names.isEmpty()) return fallbackSheets(entries)
        return names.map { it.first }
    }

    fun readSheet(bytes: ByteArray, name: String): Sheet? {
        val entries = unzip(bytes)
        val workbook = entries["xl/workbook.xml"]
        var target: String? = null
        var actual = name
        if (workbook != null) {
            val rels = parseWorkbookRels(entries["xl/_rels/workbook.xml.rels"])
            parser(workbook).use { p ->
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.START_TAG && p.tag() == "sheet" &&
                        p.attr("name") == name
                    ) {
                        target = rels[p.attr("id")]?.let { "xl/$it" }
                    }
                }
            }
        }
        if (target == null) {
            val fallback = fallbackSheets(entries)
            val index = fallback.indexOf(name).takeIf { it >= 0 } ?: 0
            target = "xl/worksheets/sheet${index + 1}.xml"
            actual = fallback.getOrNull(index) ?: name
        }
        val sheetBytes = entries[target] ?: return null
        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        return Sheet(actual, parseSheet(sheetBytes, shared))
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Row> {
        val rows = mutableListOf<Row>()
        var cells = mutableListOf<String?>()
        var excelRow = 0
        var cellType: String? = null
        var cellValue: String? = null
        var cellCol = -1
        var sequentialCol = 0
        var valueText: String? = null
        var inValue = false
        var inInline = false
        fun flushCell() {
            if (cellCol < 0) cellCol = sequentialCol
            while (cells.size <= cellCol) cells.add(null)
            cells[cellCol] = Xlsx.inferValue(
                if (inInline) "inlineStr" else cellType,
                valueText ?: cellValue,
                shared,
            )
            sequentialCol = cellCol + 1
        }
        parser(bytes).use { p ->
            var event = p.next()
            while (event != XmlPullParser.END_DOCUMENT && rows.size < MAX_ROWS) {
                when {
                    event == XmlPullParser.START_TAG && p.tag() == "row" -> {
                        cells = mutableListOf()
                        sequentialCol = 0
                        excelRow = p.attr("r")?.toIntOrNull() ?: (rows.size + 1)
                    }
                    event == XmlPullParser.START_TAG && p.tag() == "c" -> {
                        cellType = p.attr("t")
                        cellValue = null
                        valueText = null
                        inValue = false
                        inInline = false
                        cellCol = p.attr("r")?.let { Xlsx.cellRefToIndexes(it)?.second } ?: -1
                    }
                    event == XmlPullParser.START_TAG && p.tag() == "v" -> inValue = true
                    event == XmlPullParser.START_TAG && p.tag() == "is" -> inInline = true
                    event == XmlPullParser.TEXT && (inValue || inInline) -> {
                        valueText = (valueText ?: "") + p.text
                    }
                    event == XmlPullParser.END_TAG && p.tag() == "v" -> {
                        cellValue = valueText
                        valueText = null
                        inValue = false
                    }
                    event == XmlPullParser.END_TAG && p.tag() == "c" -> flushCell()
                    event == XmlPullParser.END_TAG && p.tag() == "row" -> {
                        rows.add(Row(excelRow, cells.trimEndNulls()))
                    }
                }
                event = p.next()
            }
        }
        return rows
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        var inText = false
        parser(bytes).use { p ->
            var event = p.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                when {
                    event == XmlPullParser.START_TAG && p.tag() == "si" ->
                        current = StringBuilder()
                    event == XmlPullParser.START_TAG && p.tag() == "t" -> inText = true
                    event == XmlPullParser.TEXT && inText -> current?.append(p.text)
                    event == XmlPullParser.END_TAG && p.tag() == "t" -> inText = false
                    event == XmlPullParser.END_TAG && p.tag() == "si" -> {
                        current?.let { out.add(it.toString()) }
                        current = null
                    }
                }
                event = p.next()
            }
        }
        return out
    }

    private fun parseWorkbookRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        parser(bytes).use { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.tag() == "Relationship") {
                    val id = p.attr("Id")
                    val target = p.attr("Target")
                    if (id != null && target != null && "worksheets" in target) {
                        out[id] = target.substringAfterLast('/')
                    }
                }
            }
        }
        return out
    }

    private fun fallbackSheets(entries: Map<String, ByteArray>): List<String> =
        entries.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .sorted()
            .mapIndexed { index, _ -> "Sheet${index + 1}" }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    out[entry.name] = buffer.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

    private inline fun <T> XmlPullParser.use(block: (XmlPullParser) -> T): T =
        block(this)

    private fun XmlPullParser.tag(): String? = name?.substringAfter(':')

    private fun XmlPullParser.attr(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfter(':') == local) {
                return getAttributeValue(i)
            }
        }
        return null
    }

    private fun List<String?>.trimEndNulls(): List<String?> {
        var end = size
        while (end > 0 && this[end - 1] == null) end--
        return subList(0, end)
    }
}
