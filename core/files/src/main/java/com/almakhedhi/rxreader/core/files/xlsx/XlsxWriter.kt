package com.almakhedhi.rxreader.core.files.xlsx

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal streaming xlsx writer: one RTL sheet, styled header, frozen top
 * row, inline strings (no shared-strings part), numbers as numbers. Rows
 * stream through an iterator so 50k-row exports stay memory-safe.
 */
object XlsxWriter {

    private val numberLike = Regex("-?\\d+(\\.\\d+)?")

    fun write(
        out: OutputStream,
        sheetName: String,
        header: List<String>,
        rows: Iterator<List<String?>>,
        colWidths: List<Int>? = null,
    ) {
        val safeName = sheetName.take(31).replace(Regex("[\\[\\]:*?/\\\\]"), " ")
        ZipOutputStream(out).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypes())
            zip.writeEntry("_rels/.rels", rels())
            zip.writeEntry("xl/workbook.xml", workbook(safeName))
            zip.writeEntry("xl/_rels/workbook.xml.rels", workbookRels())
            zip.writeEntry("xl/styles.xml", styles())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.writeText(XML_HEADER + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            zip.writeText("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            val widths = colWidths ?: header.indices.map { 20 }
            zip.writeText("<cols>")
            widths.forEachIndexed { index, width ->
                val col = index + 1
                zip.writeText("<col min=\"$col\" max=\"$col\" width=\"$width\" customWidth=\"1\"/>")
            }
            zip.writeText("</cols><sheetData>")
            zip.writeText(rowXml(1, header.map { it }, headerStyle = true))
            var rowNumber = 2
            while (rows.hasNext()) {
                zip.writeText(rowXml(rowNumber, rows.next(), headerStyle = false))
                rowNumber++
            }
            zip.writeText("</sheetData></worksheet>")
            zip.closeEntry()
        }
    }

    private fun rowXml(number: Int, cells: List<String?>, headerStyle: Boolean): String {
        val out = StringBuilder("<row r=\"$number\">")
        cells.forEachIndexed { index, cell ->
            if (cell == null || cell.isEmpty()) return@forEachIndexed
            val ref = Xlsx.columnIndexToName(index) + number
            val style = if (headerStyle) " s=\"1\"" else ""
            if (!headerStyle && numberLike.matches(cell)) {
                out.append("<c r=\"$ref\"$style><v>$cell</v></c>")
            } else if (headerStyle) {
                out.append("<c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${cell.escape()}</t></is></c>")
            } else {
                out.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${cell.escape()}</t></is></c>")
            }
        }
        return out.append("</row>").toString()
    }

    private fun String.escape(): String {
        val out = StringBuilder(length)
        for (ch in this) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                    out.append(ch)
                }
            }
        }
        return out.toString()
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        writeText(content)
        closeEntry()
    }

    private fun ZipOutputStream.writeText(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private fun contentTypes(): String =
        XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

    private fun rels(): String =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheetName: String): String =
        XML_HEADER +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"${sheetName.escape()}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "</workbook>"

    private fun workbookRels(): String =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"../styles.xml\"/>" +
            "</Relationships>"

    private fun styles(): String =
        XML_HEADER +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills><fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0E7C6B\"/><bgColor rgb=\"FF0E7C6B\"/></patternFill></fill></fills>" +
            "<borders><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"1\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/></cellXfs>" +
            "</styleSheet>"
}
