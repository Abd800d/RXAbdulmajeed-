package com.almakhedhi.rxreader.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Prepares captured images for the AI reader: downscales to a bounded size
 * and re-encodes as JPEG, which also strips EXIF metadata (location, etc.).
 *
 * Every capture path already yields an upright image (the ML Kit scanner
 * returns upright pages, the cropper normalizes orientation, and the in-app
 * camera sets its target rotation), so no EXIF rotation handling is needed.
 */
@Singleton
class ImagePreprocessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Full pipeline: load [uri] scaled down, then strip its metadata.
     * Returns null when the image cannot be decoded.
     */
    fun preprocess(uri: Uri, maxSide: Int = 2048): Bitmap? =
        loadScaled(uri, maxSide)?.let { stripMetadata(it) }

    /** Loads [uri] so its longest side is at most [maxSide] px. */
    fun loadScaled(uri: Uri, maxSide: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val scale = maxSide.toFloat() / max(raw.width, raw.height).toFloat()
        if (scale >= 1f) return raw
        val scaled = Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt(),
            (raw.height * scale).toInt(),
            true,
        )
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    /** Re-encodes as JPEG (dropping EXIF metadata) and decodes back. */
    fun stripMetadata(bitmap: Bitmap, quality: Int = 85): Bitmap {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
    }
}
