package com.almakhedhi.rxreader.core.files.xlsx

import java.time.LocalDate

/**
 * Pure xlsx helpers (cell references, dates, value mapping). The XML layer
 * lives in [XlsxReader]/[XlsxWriter]; everything here is JVM-unit-testable.
 */
object Xlsx {

    /** "B2" -> (row 1, col 1), zero-based. Returns null when malformed. */
    fun cellRefToIndexes(ref: String): Pair<Int, Int>? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        val digits = ref.drop(letters.length)
        if (letters.isEmpty() || digits.isEmpty() || !digits.all { it.isDigit() }) {
            return null
        }
        var col = 0
        for (ch in letters) col = col * 26 + (ch - 'A' + 1)
        val row = digits.toIntOrNull() ?: return null
        if (row < 1) return null
        return (row - 1) to (col - 1)
    }

    /** 0 -> "A", 27 -> "AB". */
    fun columnIndexToName(index: Int): String {
        require(index >= 0) { "Negative column" }
        var i = index
        val out = StringBuilder()
        do {
            out.append('A' + (i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return out.reverse().toString()
    }

    /**
     * Excel 1900-system serial (with its 1900 leap-year bug) to ISO date.
     * Returns null outside the plausible drug-data range.
     */
    fun excelSerialToIso(serial: Double): String? {
        val days = serial.toLong()
        if (days < 20000 || days > 80000) return null
        return LocalDate.of(1899, 12, 30).plusDays(days).toString()
    }

    /**
     * Maps a raw cell to text. [type] is the cell `t` attribute (s =
     * shared string, b = boolean, e = error, str/inlineStr = text,
     * null/n = number kept verbatim).
     */
    fun inferValue(
        type: String?,
        raw: String?,
        sharedStrings: List<String>,
    ): String? {
        if (raw == null) return null
        return when (type) {
            "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1)
            "b" -> if (raw == "1" || raw.equals("true", true)) "TRUE" else "FALSE"
            "e" -> null
            else -> raw
        }
    }
}
