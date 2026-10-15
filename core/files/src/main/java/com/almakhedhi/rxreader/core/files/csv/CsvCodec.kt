package com.almakhedhi.rxreader.core.files.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Quote-aware CSV parsing/stringifying plus encoding detection. */
object CsvCodec {

    /** U+FEFF prefix so Excel renders Arabic CSV correctly. */
    const val UTF8_BOM = "\uFEFF"

    data class Table(val header: List<String>, val rows: List<List<String>>)

    data class Decoded(val encoding: String, val text: String)

    /**
     * Detects encoding (BOM, strict UTF-8 probe, else Windows-1256 for old
     * Arabic files) and decodes. Never throws: last resort is lenient UTF-8.
     */
    fun decode(bytes: ByteArray): Decoded {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Decoded("UTF-8", String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Decoded("UTF-16LE", String(bytes, Charsets.UTF_16LE))
        }
        if (isStrictUtf8(bytes)) {
            return Decoded("UTF-8", String(bytes, Charsets.UTF_8))
        }
        return try {
            Decoded("windows-1256", String(bytes, Charset.forName("windows-1256")))
        } catch (_: Exception) {
            Decoded("UTF-8", String(bytes, Charsets.UTF_8))
        }
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }

    /** Picks the most frequent candidate delimiter in the first line. */
    fun detectDelimiter(firstLine: String): Char {
        val candidates = charArrayOf(',', ';', '\t', '|')
        var best = ','
        var bestCount = 0
        for (candidate in candidates) {
            val count = firstLine.count { it == candidate }
            if (count > bestCount) {
                best = candidate
                bestCount = count
            }
        }
        return best
    }

    /** RFC-4180-style parse: quotes, doubled quotes, embedded newlines. */
    fun parseText(text: String, delimiter: Char): Table {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() {
            row.add(field.toString())
            field.clear()
        }
        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i += 2
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                !inQuotes && ch == delimiter -> {
                    endField()
                    i++
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    endRow()
                    i++
                    if (ch == '\r' && i < text.length && text[i] == '\n') i++
                }
                else -> {
                    field.append(ch)
                    i++
                }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        if (rows.isEmpty()) return Table(emptyList(), emptyList())
        return Table(rows.first(), rows.drop(1))
    }

    /** Minimal quoting: only fields that need it. */
    fun stringify(header: List<String>, rows: List<List<String>>): String {
        val out = StringBuilder()
        out.append(stringifyRow(header))
        rows.forEach { out.append(stringifyRow(it)) }
        return out.toString()
    }

    /** Single CRLF-terminated row for streaming writers. */
    fun stringifyRow(cells: List<String>): String {
        val out = StringBuilder()
        cells.forEachIndexed { index, cell ->
            if (index > 0) out.append(',')
            val needsQuotes = cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (needsQuotes) {
                out.append('"')
                out.append(cell.replace("\"", "\"\""))
                out.append('"')
            } else {
                out.append(cell)
            }
        }
        out.append("\r\n")
        return out.toString()
    }
}
