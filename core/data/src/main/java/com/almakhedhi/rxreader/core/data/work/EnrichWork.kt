package com.almakhedhi.rxreader.core.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID

/** Schedules [EnrichWorker] runs (single drug or bulk) with network gating. */
object EnrichWork {
    const val KEY_IDS = "ids"
    const val KEY_IMAGES = "images"
    const val KEY_AI = "ai"
    const val PROGRESS = "progress"
    const val CURRENT = "current"
    const val OUT_DONE = "done"
    const val OUT_ENRICHED = "enriched"
    const val OUT_IMAGES = "images_saved"
    const val OUT_AI = "ai_done"
    const val OUT_AI_SKIPPED = "ai_skipped"
    const val SINGLE_PREFIX = "enrich_"
    const val BULK_NAME = "enrich_bulk"

    fun enqueueSingle(
        context: Context,
        drugId: Long,
        withImages: Boolean = true,
        withAi: Boolean = false,
        wifiOnly: Boolean = false,
    ): UUID =
        enqueue(context, "$SINGLE_PREFIX$drugId", longArrayOf(drugId), withImages, withAi, wifiOnly)

    fun enqueueBulk(
        context: Context,
        drugIds: List<Long>,
        withImages: Boolean = true,
        withAi: Boolean = false,
        wifiOnly: Boolean = false,
    ): UUID =
        enqueue(context, BULK_NAME, drugIds.toLongArray(), withImages, withAi, wifiOnly)

    private fun enqueue(
        context: Context,
        uniqueName: String,
        ids: LongArray,
        withImages: Boolean,
        withAi: Boolean,
        wifiOnly: Boolean,
    ): UUID {
        val request = OneTimeWorkRequestBuilder<EnrichWorker>()
            .setInputData(
                workDataOf(
                    KEY_IDS to ids,
                    KEY_IMAGES to withImages,
                    KEY_AI to withAi,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .addTag(BULK_NAME)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }
}
