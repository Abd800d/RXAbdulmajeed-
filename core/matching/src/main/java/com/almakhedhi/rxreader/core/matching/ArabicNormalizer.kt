package com.almakhedhi.rxreader.core.matching

/**
 * Arabic text normalization for search and matching (PLAN §7.3): unifies
 * alef and hamza forms, ta marbuta, and alef maksura; strips tashkeel and
 * tatweel; folds Arabic-Indic digits and Latin case. Pure functions with
 * precompiled patterns so 50k-row imports stay fast.
 */
object ArabicNormalizer {

    private val tashkeel = Regex("[ً-ٰٟ]")
    private val alef = Regex("[أإآٱ]")
    private val whitespace = Regex("\\s+")

    fun normalize(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return ""
        text = text.replace("ـ", "")
        text = tashkeel.replace(text, "")
        text = alef.replace(text, "ا")
        text = text.replace("ؤ", "ء").replace("ئ", "ء")
        text = text.replace("ة", "ه").replace("ى", "ي")
        val folded = StringBuilder(text.length)
        for (ch in text) folded.append(foldDigit(ch))
        return whitespace.replace(folded.toString().lowercase(), " ").trim()
    }

    private fun foldDigit(ch: Char): Char =
        when (ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            in '۰'..'۹' -> '0' + (ch - '۰')
            else -> ch
        }
}
