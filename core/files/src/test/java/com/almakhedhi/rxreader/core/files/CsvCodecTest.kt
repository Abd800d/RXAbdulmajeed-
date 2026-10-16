package com.almakhedhi.rxreader.core.files

import com.almakhedhi.rxreader.core.files.csv.CsvCodec
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `parses simple rows`() {
        val table = CsvCodec.parseText("a,b,c\r\n1,2,3\r\n4,5,6", ',')
        assertEquals(listOf("a", "b", "c"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("1", "2", "3"), table.rows[0])
    }

    @Test
    fun `handles quotes commas and embedded newlines`() {
        val table = CsvCodec.parseText("name,notes\n\"Panadol, Extra\",\"line1\nline2\"\n\"say \"\"hi\"\"\",x", ',')
        assertEquals(listOf("Panadol, Extra", "line1\nline2"), table.rows[0])
        assertEquals(listOf("say \"hi\"", "x"), table.rows[1])
    }

    @Test
    fun `detects delimiter`() {
        assertEquals(';', CsvCodec.detectDelimiter("a;b;c"))
        assertEquals(',', CsvCodec.detectDelimiter("a,b,c"))
        assertEquals('\t', CsvCodec.detectDelimiter("a\tb\tc"))
    }

    @Test
    fun `detects utf8 bom`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "بنادول".toByteArray(Charsets.UTF_8)
        val decoded = CsvCodec.decode(bytes)
        assertEquals("UTF-8", decoded.encoding)
        assertEquals("بنادول", decoded.text)
    }

    @Test
    fun `falls back to windows-1256 for old arabic files`() {
        // NOTE: windows-1256 has no Arabic-Indic digits, so the test uses
        // ASCII digits (Java would silently encode ١٢٥٠ as ????).
        val bytes = "بنادول,1250".toByteArray(Charset.forName("windows-1256"))
        val decoded = CsvCodec.decode(bytes)
        assertEquals("windows-1256", decoded.encoding)
        assertEquals("بنادول,1250", decoded.text)
    }

    @Test
    fun `stringify quotes only when needed`() {
        val text = CsvCodec.stringifyRow(listOf("plain", "with,comma", "with\"quote"))
        assertEquals("plain,\"with,comma\",\"with\"\"quote\"\r\n", text)
    }
}
