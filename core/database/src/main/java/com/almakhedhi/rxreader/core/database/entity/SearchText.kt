package com.almakhedhi.rxreader.core.database.entity

import com.almakhedhi.rxreader.core.matching.ArabicNormalizer

/**
 * Normalized search text shared by the repository and the import engine.
 * `tradeNorm` is the exact-match key; `searchNorm` is the denormalized
 * FTS document (names + maker + barcode + aliases).
 */
object SearchText {

    fun bestName(tradeEn: String?, tradeAr: String?, scientific: String?): String =
        tradeEn?.takeIf { it.isNotBlank() }
            ?: tradeAr?.takeIf { it.isNotBlank() }
            ?: scientific?.takeIf { it.isNotBlank() }
            ?: ""

    fun tradeNormOf(tradeEn: String?, tradeAr: String?, scientific: String?): String =
        ArabicNormalizer.normalize(bestName(tradeEn, tradeAr, scientific))

    fun searchNormOf(
        tradeEn: String?,
        tradeAr: String?,
        scientific: String?,
        manufacturer: String?,
        barcode: String?,
        aliases: List<String> = emptyList(),
    ): String {
        val parts = listOfNotNull(
            tradeEn,
            tradeAr,
            scientific,
            manufacturer,
            barcode,
        ) + aliases
        return ArabicNormalizer.normalize(parts.joinToString(" "))
    }
}
