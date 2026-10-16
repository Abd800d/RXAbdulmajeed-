package com.almakhedhi.rxreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.almakhedhi.rxreader.core.database.entity.EnrichCacheEntity

@Dao
interface EnrichCacheDao {

    @Query("SELECT * FROM enrichment_cache WHERE cacheKey = :key")
    suspend fun get(key: String): EnrichCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: EnrichCacheEntity)

    @Query("DELETE FROM enrichment_cache WHERE updatedAt < :before")
    suspend fun clearBefore(before: Long): Int

    @Query("DELETE FROM enrichment_cache")
    suspend fun clearAll()
}
