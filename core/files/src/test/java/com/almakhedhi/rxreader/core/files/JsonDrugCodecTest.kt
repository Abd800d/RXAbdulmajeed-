package com.almakhedhi.rxreader.core.files

import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec
import com.almakhedhi.rxreader.core.files.json.JsonDrugCodec.toFieldMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonDrugCodecTest {

    @Test
    fun `parses versioned file and bare arrays`() {
        val file = """{"schemaVersion":1,"count":1,"drugs":[{"tradeEn":"Panadol","price":1500,"unknownKey":1}]}"""
        val drugs = JsonDrugCodec.parse(file)
        assertEquals(1, drugs.size)
        assertEquals("Panadol", drugs[0].tradeEn)
        assertEquals(1500.0, drugs[0].price)
        assertEquals(1, JsonDrugCodec.schemaVersionOf(file))
        val bare = JsonDrugCodec.parse("""[{"tradeAr":"بنادول"}]""")
        assertEquals("بنادول", bare[0].tradeAr)
    }

    @Test
    fun `field map skips blanks`() {
        val drugs = JsonDrugCodec.parse("""[{"tradeEn":"X","price":5,"strength":""}]""")
        val map = drugs[0].toFieldMap()
        assertEquals("X", map["tradeEn"])
        assertEquals("5.0", map["price"])
        assertTrue(!map.containsKey("strength"))
    }
}
