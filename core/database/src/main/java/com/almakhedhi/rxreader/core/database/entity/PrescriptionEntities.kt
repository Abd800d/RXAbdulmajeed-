package com.almakhedhi.rxreader.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A confirmed prescription analysis. The source image lives in app files. */
@Entity(
    tableName = "prescriptions",
    indices = [Index(value = ["createdAt"])],
)
data class PrescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long = 0,
    val imageFile: String? = null,
    val language: String = "",
    val doctor: String? = null,
    val clinic: String? = null,
    val dateStr: String? = null,
    /** Model warnings, newline-joined. */
    val warnings: String = "",
    val sync: String = "none",
)

/**
 * One analyzed medication line. `matchedDrugId` intentionally has no
 * foreign key: deleting a drug must not delete history, and a dangling id
 * simply renders as "removed from catalog".
 */
@Entity(
    tableName = "prescription_items",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["prescriptionId"])],
)
data class PrescriptionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionId: Long = 0,
    val lineIndex: Int = 0,
    val written: String = "",
    val trade: String? = null,
    val scientific: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val route: String? = null,
    val dose: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructionsAr: String = "",
    val instructionsEn: String = "",
    val confidence: Double = 0.0,
    val illegible: Boolean = false,
    val notes: String? = null,
    /** Integer [ymin, xmin, ymax, xmax] box, JSON-encoded. */
    val boxJson: String = "[]",
    /** Top alternatives as a display summary ("Name (90%), …"). */
    val alternatives: String = "",
    val matchedDrugId: Long? = null,
    val matchScore: Double = 0.0,
)

/** History list row with item/match counts (single GROUP BY query). */
data class PrescriptionSummary(
    val id: Long,
    val createdAt: Long,
    val doctor: String?,
    val clinic: String?,
    val itemCount: Int,
    val matchedCount: Int,
)

/** Detail screen aggregate loaded in one transaction. */
data class PrescriptionWithItems(
    @Embedded
    val prescription: PrescriptionEntity,
    @Relation(parentColumn = "id", entityColumn = "prescriptionId")
    val items: List<PrescriptionItemEntity>,
)
