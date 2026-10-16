package com.almakhedhi.rxreader.core.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.almakhedhi.rxreader.core.database.dao.DrugImageDao
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import com.almakhedhi.rxreader.core.database.entity.ImageKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Product images: every image is decoded, downscaled and re-encoded to
 * WebP inside app-private storage; the DB row keeps provenance + license.
 */
@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageDao: DrugImageDao,
) {
    fun observeImages(drugId: Long): Flow<List<DrugImageEntity>> =
        imageDao.observeForDrug(drugId)

    suspend fun imageCount(drugId: Long): Int = imageDao.countForDrug(drugId)

    fun fileFor(fileName: String): File = File(File(context.filesDir, DIR), fileName)

    suspend fun addLocalImage(
        drugId: Long,
        bytes: ByteArray,
        kind: String = ImageKind.MANUAL,
        sourceUrl: String? = null,
        license: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("bytes are not a decodable image")
        val scaled = scaleDown(decoded)
        if (scaled !== decoded) decoded.recycle()
        val name = "img_${UUID.randomUUID()}.webp"
        File(context.filesDir, DIR).apply { mkdirs() }
        FileOutputStream(fileFor(name)).use { out ->
            @Suppress("DEPRECATION") // WEBP (not WEBP_LOSSY) runs on every API >= 14.
            val ok = scaled.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
            if (!ok) throw IllegalStateException("WebP encode failed")
        }
        val row = DrugImageEntity(
            drugId = drugId,
            fileName = name,
            sourceUrl = sourceUrl,
            width = scaled.width,
            height = scaled.height,
            createdAt = System.currentTimeMillis(),
            kind = kind,
            license = license,
        )
        scaled.recycle()
        imageDao.insert(row)
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= MAX_SIDE) return src
        val ratio = MAX_SIDE.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    suspend fun deleteImage(id: Long) = withContext(Dispatchers.IO) {
        val row = imageDao.getById(id)
        row?.fileName?.let { fileFor(it).delete() }
        imageDao.delete(id)
    }

    suspend fun deleteByDrug(drugId: Long) = withContext(Dispatchers.IO) {
        imageDao.listForDrug(drugId).forEach { row ->
            row.fileName?.let { fileFor(it).delete() }
        }
        imageDao.deleteByDrug(drugId)
    }

    companion object {
        const val DIR = "drug_images"
        const val MAX_SIDE = 1600
        const val WEBP_QUALITY = 82
    }
}
