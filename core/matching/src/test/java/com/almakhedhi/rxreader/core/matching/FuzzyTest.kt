package com.almakhedhi.rxreader.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTest {

    @Test
    fun `jaro winkler identical is one`() {
        assertEquals(1.0, Fuzzy.jaroWinkler("augmentin", "augmentin"), 0.0)
    }

    @Test
    fun `jaro winkler empty is zero`() {
        assertEquals(0.0, Fuzzy.jaroWinkler("", "augmentin"), 0.0)
    }

    @Test
    fun `jaro winkler tolerates trailing typos`() {
        assertTrue(Fuzzy.jaroWinkler("augmentin", "augmenten") > 0.9)
        assertTrue(Fuzzy.jaroWinkler("panadol", "panadol extra") > 0.8)
    }

    @Test
    fun `jaro winkler separates different names`() {
        assertTrue(Fuzzy.jaroWinkler("augmentin", "insulin") < 0.8)
    }

    @Test
    fun `levenshtein known distance`() {
        assertEquals(3, Fuzzy.levenshtein("kitten", "sitting"))
        assertEquals(0, Fuzzy.levenshtein("panadol", "panadol"))
        assertEquals(7, Fuzzy.levenshtein("", "panadol"))
    }

    @Test
    fun `levenshtein similarity range`() {
        assertEquals(1.0, Fuzzy.levenshteinSimilarity("abc", "abc"), 0.0)
        val partial = Fuzzy.levenshteinSimilarity("panadol", "panado")
        assertTrue(partial > 0.8 && partial < 1.0)
    }
}
