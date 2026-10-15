package com.almakhedhi.rxreader.core.matching

import kotlin.math.max
import kotlin.math.min

/**
 * Approximate string scoring for drug-name matching (PLAN §7.3). Both
 * functions are allocation-light; callers pass already-normalized text.
 */
object Fuzzy {

    /**
     * Jaro-Winkler similarity in [0, 1]. Rewards common prefixes, which
     * suits drug names where typos usually trail the end.
     */
    fun jaroWinkler(a: String, b: String): Double {
        val jaro = jaro(a, b)
        if (jaro == 0.0) return 0.0
        var prefix = 0
        val maxPrefix = min(min(a.length, b.length), 4)
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }

    private fun jaro(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val range = max(max(a.length, b.length) / 2 - 1, 0)
        val aMatch = BooleanArray(a.length)
        val bMatch = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val from = max(i - range, 0)
            val to = min(i + range, b.length - 1)
            for (j in from..to) {
                if (!bMatch[j] && a[i] == b[j]) {
                    aMatch[i] = true
                    bMatch[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var j = 0
        for (i in a.indices) {
            if (!aMatch[i]) continue
            while (!bMatch[j]) j++
            if (a[i] != b[j]) transpositions++
            j++
        }
        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
    }

    /** Levenshtein edit distance (single-row dynamic programming). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(previous[j] + 1, current[j - 1] + 1),
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Normalized Levenshtein similarity in [0, 1]. */
    fun levenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }
}
