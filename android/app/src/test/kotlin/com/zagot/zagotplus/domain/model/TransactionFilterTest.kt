package com.zagot.zagotplus.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class TransactionFilterTest {

    // === hasActiveFilters tests ===

    @Test
    fun `hasActiveFilters returns false for empty filter`() {
        val filter = TransactionFilter()
        
        assertFalse(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true when types is not empty`() {
        val filter = TransactionFilter(types = setOf(TransactionType.PURCHASE))
        
        assertTrue(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true when locationId is set`() {
        val filter = TransactionFilter(locationId = UUID.randomUUID())
        
        assertTrue(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true when startDate is set`() {
        val filter = TransactionFilter(startDate = Instant.now())
        
        assertTrue(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true when endDate is set`() {
        val filter = TransactionFilter(endDate = Instant.now())
        
        assertTrue(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true when productNameSearch is non-blank`() {
        val filter = TransactionFilter(productNameSearch = "Горіх")
        
        assertTrue(filter.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns false when productNameSearch is blank`() {
        val filterEmpty = TransactionFilter(productNameSearch = "")
        val filterSpaces = TransactionFilter(productNameSearch = "   ")
        
        assertFalse(filterEmpty.hasActiveFilters)
        assertFalse(filterSpaces.hasActiveFilters)
    }

    @Test
    fun `hasActiveFilters returns true with multiple filters`() {
        val filter = TransactionFilter(
            types = setOf(TransactionType.PURCHASE, TransactionType.SALE),
            locationId = UUID.randomUUID(),
            startDate = Instant.now(),
            productNameSearch = "test"
        )
        
        assertTrue(filter.hasActiveFilters)
    }

    // === Default values tests ===

    @Test
    fun `default filter has empty types set`() {
        val filter = TransactionFilter()
        
        assertTrue(filter.types.isEmpty())
    }

    @Test
    fun `default filter has null locationId`() {
        val filter = TransactionFilter()
        
        assertNull(filter.locationId)
    }

    @Test
    fun `default filter has null dates`() {
        val filter = TransactionFilter()
        
        assertNull(filter.startDate)
        assertNull(filter.endDate)
    }

    @Test
    fun `default filter has null productNameSearch`() {
        val filter = TransactionFilter()
        
        assertNull(filter.productNameSearch)
    }

    // === DateRangePreset tests ===

    @Test
    fun `DateRangePreset contains all expected values`() {
        val values = DateRangePreset.values()
        
        assertEquals(5, values.size)
        assertTrue(values.contains(DateRangePreset.TODAY))
        assertTrue(values.contains(DateRangePreset.THIS_WEEK))
        assertTrue(values.contains(DateRangePreset.THIS_MONTH))
        assertTrue(values.contains(DateRangePreset.CUSTOM))
        assertTrue(values.contains(DateRangePreset.ALL))
    }
}
