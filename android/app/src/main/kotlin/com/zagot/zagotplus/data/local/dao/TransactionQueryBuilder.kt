package com.zagot.zagotplus.data.local.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import java.time.Instant
import java.util.UUID

/**
 * Builder for constructing dynamic transaction filter queries.
 */
class TransactionQueryBuilder {

    private val types = mutableListOf<String>()
    private var locationId: UUID? = null
    private var startDate: Instant? = null
    private var endDate: Instant? = null
    private var productNameSearch: String? = null
    private var limit: Int? = null
    private var offset: Int? = null

    fun withTypes(types: List<String>): TransactionQueryBuilder {
        this.types.clear()
        this.types.addAll(types)
        return this
    }

    fun withLocation(locationId: UUID?): TransactionQueryBuilder {
        this.locationId = locationId
        return this
    }

    fun withDateRange(start: Instant?, end: Instant?): TransactionQueryBuilder {
        this.startDate = start
        this.endDate = end
        return this
    }

    fun withProductNameSearch(searchQuery: String?): TransactionQueryBuilder {
        this.productNameSearch = searchQuery?.takeIf { it.isNotBlank() }
        return this
    }

    fun withPagination(limit: Int, offset: Int): TransactionQueryBuilder {
        this.limit = limit
        this.offset = offset
        return this
    }

    /**
     * Build query to get filtered transactions.
     */
    fun build(): SupportSQLiteQuery {
        val sql = StringBuilder()
        val args = mutableListOf<Any>()

        if (productNameSearch != null) {
            sql.append("SELECT t.* FROM transactions t ")
            sql.append("INNER JOIN products p ON t.product_id = p.id ")
        } else {
            sql.append("SELECT * FROM transactions t ")
        }

        val conditions = mutableListOf<String>()

        // Type filter
        if (types.isNotEmpty()) {
            val placeholders = types.joinToString(",") { "?" }
            conditions.add("t.type IN ($placeholders)")
            args.addAll(types)
        }

        // Location filter
        locationId?.let {
            conditions.add("t.location_id = ?")
            args.add(it.toString())
        }

        // Date range filter
        startDate?.let {
            conditions.add("t.created_at >= ?")
            args.add(it.toEpochMilli())
        }
        endDate?.let {
            conditions.add("t.created_at <= ?")
            args.add(it.toEpochMilli())
        }

        // Product name search
        productNameSearch?.let { search ->
            conditions.add("p.name LIKE ?")
            args.add("%$search%")
        }

        if (conditions.isNotEmpty()) {
            sql.append("WHERE ")
            sql.append(conditions.joinToString(" AND "))
        }

        sql.append(" ORDER BY t.created_at DESC")

        // Pagination
        limit?.let { l ->
            sql.append(" LIMIT ?")
            args.add(l)
            offset?.let { o ->
                sql.append(" OFFSET ?")
                args.add(o)
            }
        }

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    /**
     * Build query to get count of filtered transactions.
     */
    fun buildCount(): SupportSQLiteQuery {
        val sql = StringBuilder()
        val args = mutableListOf<Any>()

        if (productNameSearch != null) {
            sql.append("SELECT COUNT(*) FROM transactions t ")
            sql.append("INNER JOIN products p ON t.product_id = p.id ")
        } else {
            sql.append("SELECT COUNT(*) FROM transactions t ")
        }

        val conditions = mutableListOf<String>()

        // Type filter
        if (types.isNotEmpty()) {
            val placeholders = types.joinToString(",") { "?" }
            conditions.add("t.type IN ($placeholders)")
            args.addAll(types)
        }

        // Location filter
        locationId?.let {
            conditions.add("t.location_id = ?")
            args.add(it.toString())
        }

        // Date range filter
        startDate?.let {
            conditions.add("t.created_at >= ?")
            args.add(it.toEpochMilli())
        }
        endDate?.let {
            conditions.add("t.created_at <= ?")
            args.add(it.toEpochMilli())
        }

        // Product name search
        productNameSearch?.let { search ->
            conditions.add("p.name LIKE ?")
            args.add("%$search%")
        }

        if (conditions.isNotEmpty()) {
            sql.append("WHERE ")
            sql.append(conditions.joinToString(" AND "))
        }

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
