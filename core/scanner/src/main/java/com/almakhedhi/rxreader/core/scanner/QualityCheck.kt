package com.almakhedhi.rxreader.core.scanner

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic image-quality gate: Laplacian-variance blur detection plus mean
 * luminance, computed on a small grayscale copy. Flags bad captures so the
 * user can retake instead of wasting an AI call on an unreadable photo.
 */
@Singleton
class QualityCheck @Inject constructor() {

    data class Quality(
        val blurScore: Double,
        val brightness: Double,
        val isBlurry: Boolean,
        val isDark: Boolean,
    )

    fun assess(bitmap: Bitmap): Quality {
        val width = 128
        val height = (128f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()
        val gray = DoubleArray(width * height) { i ->
            val p = pixels[i]
            0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)
        }
        var sum = 0.0
        for (v in gray) sum += v
        val brightness = sum / gray.size
        // Laplacian responses, skipping the borders.
        var lapSum = 0.0
        var lapSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x]
                val lap = gray[(y - 1) * width + x] +
                    gray[(y + 1) * width + x] +
                    gray[y * width + x - 1] +
                    gray[y * width + x + 1] - 4 * center
                lapSum += lap
                lapSq += lap * lap
                count++
            }
        }
        val mean = lapSum / count
        val variance = lapSq / count - mean * mean
        return Quality(
            blurScore = variance,
            brightness = brightness,
            isBlurry = variance < BLUR_THRESHOLD,
            isDark = brightness < DARK_THRESHOLD,
        )
    }

    companion object {
        /** Tuned for 0-255 grayscale; revalidate on real photos in Phase 7. */
        const val BLUR_THRESHOLD = 120.0
        const val DARK_THRESHOLD = 60.0
    }
}
