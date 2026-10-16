package com.almakhedhi.rxreader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Raw API payload cache for enrichment (RxNorm/openFDA/DailyMed/Commons).
 * Keyed by "source:normalized-query"; entries expire by [updatedAt].
 */
@Entity(tableName = "enrichment_cache")
data class EnrichCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val source: String,
    val payload: String,
    val updatedAt: Long = 0,
)
