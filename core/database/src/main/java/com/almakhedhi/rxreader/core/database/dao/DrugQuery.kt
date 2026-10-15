package com.almakhedhi.rxreader.core.database.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** Browse filter for the drug list and filtered exports. */
data class DrugFilter(
    val therapeutic: String? = null,
    val form: String? = null,
    val manufacturer: String? = null,
    val incompleteOnly: Boolean = false,
    val reviewed: Boolean? = null,
    val sort: DrugSort = DrugSort.NAME,
)

enum class DrugSort {
    NAME,
    UPDATED_DESC,
    CREATED_DESC,
}

/**
 * Builds injection-safe list/count queries: column names come from fixed
 * enums, user values are bound arguments. Search itself uses FTS, not this.
 */
object DrugQuery {

    fun list(filter: DrugFilter, limit: Int, offset: Int): SupportSQLiteQuery {
        val (where, args) = whereClause(filter)
        val order = when (filter.sort) {
            DrugSort.NAME -> "COALESCE(NULLIF(tradeEn, ''), NULLIF(tradeAr, ''), scientific, '') ASC"
            DrugSort.UPDATED_DESC -> "updatedAt DESC"
            DrugSort.CREATED_DESC -> "createdAt DESC"
        }
        return SimpleSQLiteQuery(
            "SELECT * FROM drugs $where ORDER BY $order LIMIT ? OFFSET ?",
            (args + listOf(limit, offset)).toTypedArray(),
        )
    }

    fun count(filter: DrugFilter): SupportSQLiteQuery {
        val (where, args) = whereClause(filter)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM drugs $where",
            args.toTypedArray(),
        )
    }

    private fun whereClause(filter: DrugFilter): Pair<String, List<Any>> {
        val clauses = mutableListOf("deletedAt IS NULL")
        val args = mutableListOf<Any>()
        filter.therapeutic?.let {
            clauses.add("therapeutic = ?")
            args.add(it)
        }
        filter.form?.let {
            clauses.add("dosageForm = ?")
            args.add(it)
        }
        filter.manufacturer?.let {
            clauses.add("manufacturer = ?")
            args.add(it)
        }
        filter.reviewed?.let {
            clauses.add("reviewed = ?")
            args.add(if (it) 1 else 0)
        }
        if (filter.incompleteOnly) {
            clauses.add(
                "((scientific IS NULL OR scientific = '') OR " +
                    "(strength IS NULL OR strength = '') OR " +
                    "price IS NULL OR " +
                    "(manufacturer IS NULL OR manufacturer = ''))",
            )
        }
        return "WHERE " + clauses.joinToString(" AND ") to args
    }
}
