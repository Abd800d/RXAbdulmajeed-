package com.almakhedhi.rxreader.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.almakhedhi.rxreader.core.database.entity.DrugEntity
import com.almakhedhi.rxreader.core.database.entity.DrugKey
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Upsert
    suspend fun upsert(drug: DrugEntity): Long

    @Upsert
    suspend fun upsertAll(drugs: List<DrugEntity>): List<Long>

    @Query("SELECT * FROM drugs WHERE id = :id")
    suspend fun getById(id: Long): DrugEntity?

    @Query("SELECT * FROM drugs WHERE id = :id")
    fun observeById(id: Long): Flow<DrugEntity?>

    @Query("SELECT * FROM drugs WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getByIds(ids: List<Long>): List<DrugEntity>

    /** Exact normalized trade-name match (match pipeline stage 1). */
    @Query("SELECT * FROM drugs WHERE tradeNorm = :norm AND deletedAt IS NULL")
    suspend fun findByTradeNorm(norm: String): List<DrugEntity>

    /** Prefix full-text search over the denormalized search column. */
    @Query(
        """
        SELECT drugs.* FROM drugs
        JOIN drugs_fts ON drugs.rowid = drugs_fts.docid
        WHERE drugs_fts MATCH :query AND drugs.deletedAt IS NULL
        ORDER BY rank LIMIT :limit
        """,
    )
    suspend fun searchFts(query: String, limit: Int): List<DrugEntity>

    /** Filtered/sorted/paged browse; SQL is built by the repository. */
    @RawQuery(observedEntities = [DrugEntity::class])
    fun observeRaw(query: SupportSQLiteQuery): Flow<List<DrugEntity>>

    @RawQuery
    suspend fun listRaw(query: SupportSQLiteQuery): List<DrugEntity>

    @RawQuery
    suspend fun countRaw(query: SupportSQLiteQuery): Int

    @Query("SELECT DISTINCT therapeutic FROM drugs WHERE deletedAt IS NULL AND therapeutic IS NOT NULL AND therapeutic != '' ORDER BY therapeutic")
    suspend fun distinctTherapeutics(): List<String>

    @Query("SELECT DISTINCT dosageForm FROM drugs WHERE deletedAt IS NULL AND dosageForm IS NOT NULL AND dosageForm != '' ORDER BY dosageForm")
    suspend fun distinctForms(): List<String>

    @Query("SELECT DISTINCT manufacturer FROM drugs WHERE deletedAt IS NULL AND manufacturer IS NOT NULL AND manufacturer != '' ORDER BY manufacturer")
    suspend fun distinctManufacturers(): List<String>

    @Query("UPDATE drugs SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE drugs SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: Long, at: Long)

    @Query("DELETE FROM drugs WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("SELECT * FROM drugs WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<DrugEntity>>

    @Query("SELECT COUNT(*) FROM drugs WHERE deletedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    @Query("DELETE FROM drugs WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeBefore(before: Long): Int

    @Query("DELETE FROM drugs WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash(): Int

    @Query("SELECT COUNT(*) FROM drugs WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    /** Invalidation signal for the paged list (every write bumps updatedAt). */
    @Query("SELECT MAX(updatedAt) FROM drugs")
    fun observeMaxUpdated(): Flow<Long?>

    /** LIKE fallback when an FTS query is rejected; token must be sanitized. */
    @Query(
        """
        SELECT * FROM drugs WHERE deletedAt IS NULL AND
        (tradeEn LIKE '%' || :token || '%' OR tradeAr LIKE '%' || :token || '%' OR scientific LIKE '%' || :token || '%')
        LIMIT :limit
        """,
    )
    suspend fun searchLike(token: String, limit: Int): List<DrugEntity>

    /** All business keys for in-memory import duplicate detection. */
    @Query("SELECT id, barcode, regNo, tradeNorm, strength FROM drugs WHERE deletedAt IS NULL")
    suspend fun allKeys(): List<DrugKey>
}
