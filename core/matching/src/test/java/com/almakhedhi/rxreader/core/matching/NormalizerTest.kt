package com.almakhedhi.rxreader.core.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {

    @Test
    fun `unifies alef forms`() {
        assertEquals("احمد", ArabicNormalizer.normalize("أحمد"))
        assertEquals("احمد", ArabicNormalizer.normalize("إحمد"))
        assertEquals("احمد", ArabicNormalizer.normalize("آحمد"))
    }

    @Test
    fun `unifies ta marbuta and alef maksura`() {
        assertEquals("صيدليه", ArabicNormalizer.normalize("صيدلية"))
        assertEquals("علي", ArabicNormalizer.normalize("على"))
    }

    @Test
    fun `strips tashkeel and tatweel`() {
        assertEquals("دواء", ArabicNormalizer.normalize("دَوَاء"))
        assertEquals("علاج", ArabicNormalizer.normalize("عــلاج"))
    }

    @Test
    fun `folds digits case and whitespace`() {
        assertEquals("augmentin 625", ArabicNormalizer.normalize("  Augmentin ٦٢٥ "))
        assertEquals("500", ArabicNormalizer.normalize("٥٠٠"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", ArabicNormalizer.normalize("   "))
    }
}
