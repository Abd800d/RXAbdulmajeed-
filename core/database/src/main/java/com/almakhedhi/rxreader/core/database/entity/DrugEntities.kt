package com.almakhedhi.rxreader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Drug catalog item. Column groups follow PLAN §8.2. `tradeNorm` powers
 * exact matching, `searchNorm` (denormalized, includes aliases) powers FTS;
 * both are maintained by the repository, never by the UI.
 */
@Entity(
    tableName = "drugs",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["regNo"], unique = true),
        Index(value = ["tradeNorm"]),
        Index(value = ["therapeutic"]),
        Index(value = ["dosageForm"]),
        Index(value = ["manufacturer"]),
        Index(value = ["deletedAt"]),
        Index(value = ["updatedAt"]),
    ],
)
data class DrugEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Identity.
    val tradeEn: String? = null,
    val tradeAr: String? = null,
    val scientific: String? = null,
    val actives: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    // Product.
    val manufacturer: String? = null,
    val country: String? = null,
    val agent: String? = null,
    val regNo: String? = null,
    val barcode: String? = null,
    val packSize: String? = null,
    val price: Double? = null,
    val priceDate: String? = null,
    val dispensing: String? = null,
    // Classification.
    val therapeutic: String? = null,
    val atc: String? = null,
    // Clinical info.
    val indications: String? = null,
    val dosageAdults: String? = null,
    val dosageChildren: String? = null,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val interactions: String? = null,
    val pregnancy: String? = null,
    val warnings: String? = null,
    val storage: String? = null,
    // External ids.
    val rxNormId: String? = null,
    val dailymedId: String? = null,
    // System.
    val source: String = "manual",
    val reviewed: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val sync: String = "none",
    // Search.
    val tradeNorm: String = "",
    val searchNorm: String = "",
)

/**
 * Full-text index over [DrugEntity.searchNorm], auto-synced by Room
 * triggers. `unicode61` tokenization is required for Arabic script.
 */
@Entity(tableName = "drugs_fts")
@Fts4(contentEntity = DrugEntity::class, tokenizer = "unicode61")
data class DrugFtsEntity(
    @ColumnInfo(name = "searchNorm")
    val searchNorm: String,
)

/**
 * Alternative names: pharmacist corrections (learned), import synonyms,
 * and common misspellings. Unique per drug.
 */
@Entity(
    tableName = "drug_aliases",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["drugId"]),
        Index(value = ["aliasNorm"]),
        Index(value = ["drugId", "aliasNorm"], unique = true),
    ],
)
data class DrugAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val drugId: Long,
    val aliasNorm: String,
    val aliasRaw: String,
    val source: String = "manual",
    val createdAt: Long = 0,
)

/** Product photos attached to a drug (files live in app storage). */
@Entity(
    tableName = "drug_images",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["drugId"])],
)
data class DrugImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val drugId: Long,
    val fileName: String? = null,
    val sourceUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = 0,
)

/** Lightweight business keys for import duplicate detection. */
data class DrugKey(
    val id: Long,
    val barcode: String?,
    val regNo: String?,
    val tradeNorm: String,
    val strength: String?,
)
