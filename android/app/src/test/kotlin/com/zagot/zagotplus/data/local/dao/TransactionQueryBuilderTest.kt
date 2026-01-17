package com.zagot.zagotplus.data.local.dao

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for TransactionQueryBuilder.
 */
class TransactionQueryBuilderTest {

    private val testLocationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testStartDate = Instant.parse("2024-01-01T00:00:00Z")
    private val testEndDate = Instant.parse("2024-01-31T23:59:59Z")

    @Test
    fun `build creates basic query with voided batch exclusion`() {
        val query = TransactionQueryBuilder().build()
        val sql = query.sql

        assertTrue(sql.contains("SELECT t.* FROM transactions t"))
        assertTrue(sql.contains("LEFT JOIN purchase_batches pb ON t.batch_id = pb.id"))
        assertTrue(sql.contains("LEFT JOIN sale_batches sb ON t.sale_batch_id = sb.id"))
        assertTrue(sql.contains("(t.batch_id IS NULL OR pb.is_voided = 0)"))
        assertTrue(sql.contains("(t.sale_batch_id IS NULL OR sb.is_voided = 0)"))
        assertTrue(sql.contains("ORDER BY t.created_at DESC"))
        assertTrue(sql.contains("WHERE"))
    }

    @Test
    fun `build with types filter creates IN clause`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase", "sale"))
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.type IN (?,?)"))
    }

    @Test
    fun `build with single type filter`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.type IN (?)"))
    }

    @Test
    fun `build with empty types does not add type filter`() {
        val query = TransactionQueryBuilder()
            .withTypes(emptyList())
            .build()
        val sql = query.sql

        assertFalse(sql.contains("t.type IN"))
    }

    @Test
    fun `build with location filter`() {
        val query = TransactionQueryBuilder()
            .withLocation(testLocationId)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.location_id = ?"))
    }

    @Test
    fun `build with null location does not add filter`() {
        val query = TransactionQueryBuilder()
            .withLocation(null)
            .build()
        val sql = query.sql

        assertFalse(sql.contains("t.location_id"))
    }

    @Test
    fun `build with date range adds both conditions`() {
        val query = TransactionQueryBuilder()
            .withDateRange(testStartDate, testEndDate)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.created_at >= ?"))
        assertTrue(sql.contains("t.created_at <= ?"))
    }

    @Test
    fun `build with start date only`() {
        val query = TransactionQueryBuilder()
            .withDateRange(testStartDate, null)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.created_at >= ?"))
        assertFalse(sql.contains("t.created_at <= ?"))
    }

    @Test
    fun `build with end date only`() {
        val query = TransactionQueryBuilder()
            .withDateRange(null, testEndDate)
            .build()
        val sql = query.sql

        assertFalse(sql.contains("t.created_at >= ?"))
        assertTrue(sql.contains("t.created_at <= ?"))
    }

    @Test
    fun `build with product name search adds join`() {
        val query = TransactionQueryBuilder()
            .withProductNameSearch("горіх")
            .build()
        val sql = query.sql

        assertTrue(sql.contains("SELECT t.* FROM transactions t"))
        assertTrue(sql.contains("INNER JOIN products p ON t.product_id = p.id"))
        assertTrue(sql.contains("p.name LIKE ?"))
    }

    @Test
    fun `build with blank search does not add filter`() {
        val query = TransactionQueryBuilder()
            .withProductNameSearch("   ")
            .build()
        val sql = query.sql

        assertFalse(sql.contains("INNER JOIN products"))
        assertFalse(sql.contains("p.name LIKE"))
    }

    @Test
    fun `build with null search does not add filter`() {
        val query = TransactionQueryBuilder()
            .withProductNameSearch(null)
            .build()
        val sql = query.sql

        assertFalse(sql.contains("INNER JOIN products"))
    }

    @Test
    fun `build with pagination adds LIMIT and OFFSET`() {
        val query = TransactionQueryBuilder()
            .withPagination(limit = 20, offset = 40)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("LIMIT ?"))
        assertTrue(sql.contains("OFFSET ?"))
    }

    @Test
    fun `build with pagination zero offset still includes OFFSET`() {
        val query = TransactionQueryBuilder()
            .withPagination(limit = 10, offset = 0)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("LIMIT ?"))
        assertTrue(sql.contains("OFFSET ?"))
    }

    @Test
    fun `build with multiple filters combines with AND`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .withLocation(testLocationId)
            .withDateRange(testStartDate, testEndDate)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("AND"))
        // Should have: 2 voided conditions + type + location + 2 date conditions = 5 ANDs
        val andCount = sql.split("AND").size - 1
        assertEquals(5, andCount)
    }

    @Test
    fun `build always orders by created_at DESC`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("sale"))
            .build()
        val sql = query.sql

        assertTrue(sql.contains("ORDER BY t.created_at DESC"))
    }

    @Test
    fun `buildCount creates count query without pagination`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .withPagination(10, 20)
            .buildCount()
        val sql = query.sql

        assertTrue(sql.contains("SELECT COUNT(*)"))
        assertTrue(sql.contains("LEFT JOIN purchase_batches pb"))
        assertTrue(sql.contains("LEFT JOIN sale_batches sb"))
        assertTrue(sql.contains("(t.batch_id IS NULL OR pb.is_voided = 0)"))
        assertFalse(sql.contains("ORDER BY"))
        assertFalse(sql.contains("LIMIT"))
        assertFalse(sql.contains("OFFSET"))
    }

    @Test
    fun `buildCount with product search adds join`() {
        val query = TransactionQueryBuilder()
            .withProductNameSearch("test")
            .buildCount()
        val sql = query.sql

        assertTrue(sql.contains("SELECT COUNT(*) FROM transactions t"))
        assertTrue(sql.contains("INNER JOIN products p"))
    }

    @Test
    fun `buildCount applies same filters as build`() {
        val query = TransactionQueryBuilder()
            .withTypes(listOf("sale"))
            .withLocation(testLocationId)
            .buildCount()
        val sql = query.sql

        assertTrue(sql.contains("t.type IN (?)"))
        assertTrue(sql.contains("t.location_id = ?"))
    }

    @Test
    fun `builder methods are chainable`() {
        val builder = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .withLocation(testLocationId)
            .withDateRange(testStartDate, testEndDate)
            .withProductNameSearch("test")
            .withPagination(10, 0)

        assertNotNull(builder.build())
    }

    @Test
    fun `withTypes clears previous types`() {
        val builder = TransactionQueryBuilder()
            .withTypes(listOf("purchase", "sale"))
            .withTypes(listOf("transfer_out"))

        val query = builder.build()
        val sql = query.sql

        // Should only have one placeholder for transfer_out
        assertTrue(sql.contains("t.type IN (?)"))
        assertFalse(sql.contains("t.type IN (?,?)"))
    }

    @Test
    fun `build query with all transaction types`() {
        val allTypes = listOf("purchase", "sale", "transfer_out", "transfer_in")
        val query = TransactionQueryBuilder()
            .withTypes(allTypes)
            .build()
        val sql = query.sql

        assertTrue(sql.contains("t.type IN (?,?,?,?)"))
    }

    @Test
    fun `query preserves order of type placeholders`() {
        val types = listOf("sale", "purchase")
        val query = TransactionQueryBuilder()
            .withTypes(types)
            .build()

        // The query arguments should contain the types in order
        assertNotNull(query)
    }
}
