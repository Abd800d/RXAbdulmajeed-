package com.almakhedhi.rxreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.almakhedhi.rxreader.core.database.entity.PrescriptionEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionItemEntity
import com.almakhedhi.rxreader.core.database.entity.PrescriptionSummary
import com.almakhedhi.rxreader.core.database.entity.PrescriptionWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {

    @Insert
    suspend fun insert(prescription: PrescriptionEntity): Long

    @Insert
    suspend fun insertItems(items: List<PrescriptionItemEntity>)

    @Query(
        """
        SELECT p.id AS id, p.createdAt AS createdAt, p.doctor AS doctor,
            p.clinic AS clinic, COUNT(i.id) AS itemCount,
            SUM(CASE WHEN i.matchedDrugId IS NOT NULL THEN 1 ELSE 0 END) AS matchedCount
        FROM prescriptions p LEFT JOIN prescription_items i ON i.prescriptionId = p.id
        GROUP BY p.id ORDER BY p.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PrescriptionSummary>>

    @Query(
        """
        SELECT p.id AS id, p.createdAt AS createdAt, p.doctor AS doctor,
            p.clinic AS clinic, COUNT(i.id) AS itemCount,
            SUM(CASE WHEN i.matchedDrugId IS NOT NULL THEN 1 ELSE 0 END) AS matchedCount
        FROM prescriptions p LEFT JOIN prescription_items i ON i.prescriptionId = p.id
        GROUP BY p.id ORDER BY p.createdAt DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<PrescriptionSummary>>

    @Transaction
    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getWithItems(id: Long): PrescriptionWithItems?

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getById(id: Long): PrescriptionEntity?

    @Query("DELETE FROM prescriptions WHERE id = :id")
    suspend fun delete(id: Long)
}
