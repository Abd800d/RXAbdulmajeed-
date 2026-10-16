package com.almakhedhi.rxreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.almakhedhi.rxreader.core.database.entity.DrugAliasEntity
import com.almakhedhi.rxreader.core.database.entity.DrugImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugAliasDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alias: DrugAliasEntity): Long

    @Query("SELECT * FROM drug_aliases WHERE drugId = :drugId ORDER BY aliasRaw")
    fun observeForDrug(drugId: Long): Flow<List<DrugAliasEntity>>

    @Query("SELECT * FROM drug_aliases WHERE drugId = :drugId")
    suspend fun listForDrug(drugId: Long): List<DrugAliasEntity>

    /** Alias match (match pipeline stage 2). */
    @Query("SELECT drugId FROM drug_aliases WHERE aliasNorm = :norm")
    suspend fun findDrugIdsByAlias(norm: String): List<Long>

    @Query("DELETE FROM drug_aliases WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DrugImageDao {

    @Insert
    suspend fun insert(image: DrugImageEntity): Long

    @Query("SELECT * FROM drug_images WHERE drugId = :drugId ORDER BY createdAt")
    fun observeForDrug(drugId: Long): Flow<List<DrugImageEntity>>

    @Query("SELECT COUNT(*) FROM drug_images WHERE drugId = :drugId")
    suspend fun countForDrug(drugId: Long): Int

    @Query("DELETE FROM drug_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM drug_images WHERE id = :id")
    suspend fun getById(id: Long): DrugImageEntity?

    @Query("SELECT * FROM drug_images WHERE drugId = :drugId ORDER BY createdAt")
    suspend fun listForDrug(drugId: Long): List<DrugImageEntity>

    @Query("DELETE FROM drug_images WHERE drugId = :drugId")
    suspend fun deleteByDrug(drugId: Long)
}
