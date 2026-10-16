package com.almakhedhi.rxreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (Phase 4): image provenance columns + the API payload cache.
 * Column order in CREATE TABLE must match field order in the entities.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drug_images ADD COLUMN kind TEXT NOT NULL DEFAULT 'manual'")
        db.execSQL("ALTER TABLE drug_images ADD COLUMN license TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS enrichment_cache (" +
                "cacheKey TEXT PRIMARY KEY NOT NULL, " +
                "source TEXT NOT NULL, " +
                "payload TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL)",
        )
    }
}
