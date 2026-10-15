package com.almakhedhi.rxreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Record of one import run with its final report counts. */
@Entity(
    tableName = "import_jobs",
    indices = [Index(value = ["startedAt"])],
)
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String = "",
    val format: String = "",
    val policy: String = "",
    val status: String = STATUS_RUNNING,
    val totalRows: Int = 0,
    val processedRows: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}
