package com.almakhedhi.rxreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.almakhedhi.rxreader.core.database.entity.ImportJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {

    @Insert
    suspend fun insert(job: ImportJobEntity): Long

    @Update
    suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ImportJobEntity>>
}
