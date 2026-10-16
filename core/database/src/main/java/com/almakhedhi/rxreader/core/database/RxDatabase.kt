package com.almakhedhi.rxreader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.almakhedhi.rxreader.core.database.dao.DrugAliasDao
import com.almakhedhi.rxreader.core.database.dao.DrugDao
import com.almakhedhi.rxreader.core.database.dao.DrugImageDao
import com.almakhedhi.rxreader.core.database.dao.EnrichCacheDao
import com.almakhedhi.rxreader.core.database.dao.ImportJobDao
import com.almakhedhi.rxreader.core.database.dao.PrescriptionDao
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.database.entity.DrugFtsEntity
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import com.almakhedhi.rxreader.core.database.entity.EnrichCacheEntity
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionItemEntity

/**
 * Offline-first app database (PLAN §8.3). v2 adds image provenance
 * columns and the enrichment API cache (Phase 4) via [MIGRATION_1_2].
 */
@Database(
    entities = [
        DrugEntity::class,
        DrugFtsEntity::class,
        DrugAliasEntity::class,
        DrugImageEntity::class,
        PrescriptionEntity::class,
        PrescriptionItemEntity::class,
        ImportJobEntity::class,
        EnrichCacheEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RxDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao
    abstract fun aliasDao(): DrugAliasDao
    abstract fun imageDao(): DrugImageDao
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun enrichCacheDao(): EnrichCacheDao
}
