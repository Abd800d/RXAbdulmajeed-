package com.almakhedhi.rxreader.core.files

import com.almakhedhi.rxreader.core.files.xlsx.Xlsx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XlsxHelpersTest {

    @Test
    fun `cell refs convert both ways`() {
        assertEquals(0 to 0, Xlsx.cellRefToIndexes("A1"))
        assertEquals(1 to 1, Xlsx.cellRefToIndexes("B2"))
        assertEquals(0 to 25, Xlsx.cellRefToIndexes("Z1"))
        assertEquals(0 to 26, Xlsx.cellRefToIndexes("AA1"))
        assertEquals(11 to 27, Xlsx.cellRefToIndexes("AB12"))
        assertNull(Xlsx.cellRefToIndexes("12"))
        assertNull(Xlsx.cellRefToIndexes("A"))
        assertEquals("A", Xlsx.columnIndexToName(0))
        assertEquals("Z", Xlsx.columnIndexToName(25))
        assertEquals("AA", Xlsx.columnIndexToName(26))
        assertEquals("AB", Xlsx.columnIndexToName(27))
    }

    @Test
    fun `excel serials convert to iso dates`() {
        assertEquals("2024-01-01", Xlsx.excelSerialToIso(45292.0))
        assertEquals("2023-01-01", Xlsx.excelSerialToIso(44927.0))
        assertNull(Xlsx.excelSerialToIso(100.0))
        assertNull(Xlsx.excelSerialToIso(90000.0))
    }

    @Test
    fun `cell values infer by type`() {
        val shared = listOf("Panadol", "Augmentin")
        assertEquals("Augmentin", Xlsx.inferValue("s", "1", shared))
        assertNull(Xlsx.inferValue("s", "9", shared))
        assertEquals("TRUE", Xlsx.inferValue("b", "1", shared))
        assertEquals("FALSE", Xlsx.inferValue("b", "0", shared))
        assertNull(Xlsx.inferValue("e", "#N/A", shared))
        assertEquals("625", Xlsx.inferValue(null, "625", shared))
        assertNull(Xlsx.inferValue(null, null, shared))
    }
}
