package com.almakhedhi.rxreader.core.files

import com.almakhedhi.rxreader.core.files.columns.DrugColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMapperTest {

    @Test
    fun `maps arabic and english headers`() {
        val result = DrugColumns.autoMap(
            listOf("الاسم التجاري", "Barcode", "Price", "عمود غريب"),
        )
        assertEquals("tradeEn", result.map[0])
        assertEquals("barcode", result.map[1])
        assertEquals("price", result.map[2])
        assertEquals(listOf("عمود غريب"), result.unmappedHeaders)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `first column wins on duplicates`() {
        val result = DrugColumns.autoMap(listOf("السعر", "Price"))
        assertEquals("price", result.map[0])
        assertFalse(result.map.containsKey(1))
        assertEquals(listOf("Price"), result.conflicts)
    }

    @Test
    fun `canonicalizes forms and dispensing`() {
        assertEquals("tablet", DrugColumns.canonicalForm("أقراص"))
        assertEquals("syrup", DrugColumns.canonicalForm("Syrup"))
        assertEquals("other", DrugColumns.canonicalForm("???"))
        assertEquals("rx", DrugColumns.canonicalDispensing("بوصفة"))
        assertEquals("otc", DrugColumns.canonicalDispensing("OTC"))
        assertEquals("", DrugColumns.canonicalDispensing("maybe"))
    }

    @Test
    fun `parses prices and booleans`() {
        assertEquals(1250.0, DrugColumns.parsePrice("1,250"))
        assertEquals(1250.0, DrugColumns.parsePrice("١٢٥٠"))
        assertNull(DrugColumns.parsePrice("abc"))
        assertNull(DrugColumns.parsePrice("-5"))
        assertTrue(DrugColumns.parseBool("نعم"))
        assertTrue(DrugColumns.parseBool("TRUE"))
        assertFalse(DrugColumns.parseBool("0"))
    }
}
