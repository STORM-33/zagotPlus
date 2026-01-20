package com.zagot.zagotplus.data.local.converter

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Tests for Room type converters.
 * Critical for catching serialization bugs that are silent killers at runtime.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setup() {
        converters = Converters()
    }

    // ==================== UUID Conversion ====================

    @Test
    fun `fromUUID converts UUID to String`() {
        val uuid = UUID.fromString("12345678-1234-1234-1234-123456789012")
        
        val result = converters.fromUUID(uuid)
        
        assertEquals("12345678-1234-1234-1234-123456789012", result)
    }

    @Test
    fun `toUUID converts String to UUID`() {
        val string = "12345678-1234-1234-1234-123456789012"
        
        val result = converters.toUUID(string)
        
        assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789012"), result)
    }

    @Test
    fun `fromUUID handles null`() {
        val result = converters.fromUUID(null)
        
        assertNull(result)
    }

    @Test
    fun `toUUID handles null`() {
        val result = converters.toUUID(null)
        
        assertNull(result)
    }

    @Test
    fun `UUID roundtrip preserves value`() {
        val original = UUID.randomUUID()
        
        val string = converters.fromUUID(original)
        val restored = converters.toUUID(string)
        
        assertEquals(original, restored)
    }

    @Test
    fun `toUUID returns null on invalid UUID format`() {
        val result = converters.toUUID("not-a-valid-uuid")
        assertNull(result)
    }

    // ==================== Instant Conversion ====================

    @Test
    fun `fromInstant converts Instant to epoch millis`() {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        
        val result = converters.fromInstant(instant)
        
        // Verify the conversion is correct by round-tripping
        assertEquals(instant.toEpochMilli(), result)
    }

    @Test
    fun `toInstant converts epoch millis to Instant`() {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val epochMilli = instant.toEpochMilli()
        
        val result = converters.toInstant(epochMilli)
        
        assertEquals(instant, result)
    }

    @Test
    fun `fromInstant handles null`() {
        val result = converters.fromInstant(null)
        
        assertNull(result)
    }

    @Test
    fun `toInstant handles null`() {
        val result = converters.toInstant(null)
        
        assertNull(result)
    }

    @Test
    fun `Instant roundtrip preserves value`() {
        val original = Instant.now()
        
        val millis = converters.fromInstant(original)
        val restored = converters.toInstant(millis)
        
        // Note: Precision may be lost for nanoseconds, but millis should match
        assertEquals(original.toEpochMilli(), restored?.toEpochMilli())
    }

    @Test
    fun `toInstant handles epoch zero`() {
        val result = converters.toInstant(0L)
        
        assertEquals(Instant.EPOCH, result)
    }

    @Test
    fun `toInstant handles negative epoch (dates before 1970)`() {
        val preEpoch = -86400000L // One day before epoch
        
        val result = converters.toInstant(preEpoch)
        
        assertEquals(Instant.parse("1969-12-31T00:00:00Z"), result)
    }

    // ==================== BigDecimal Conversion ====================

    @Test
    fun `fromBigDecimal converts to plain string`() {
        val decimal = BigDecimal("123.45")
        
        val result = converters.fromBigDecimal(decimal)
        
        assertEquals("123.45", result)
    }

    @Test
    fun `toBigDecimal converts string to BigDecimal`() {
        val string = "123.45"
        
        val result = converters.toBigDecimal(string)
        
        assertEquals(BigDecimal("123.45"), result)
    }

    @Test
    fun `fromBigDecimal handles null`() {
        val result = converters.fromBigDecimal(null)
        
        assertNull(result)
    }

    @Test
    fun `toBigDecimal handles null`() {
        val result = converters.toBigDecimal(null)
        
        assertNull(result)
    }

    @Test
    fun `BigDecimal roundtrip preserves value`() {
        val original = BigDecimal("12345.67890")
        
        val string = converters.fromBigDecimal(original)
        val restored = converters.toBigDecimal(string)
        
        assertEquals(original, restored)
    }

    @Test
    fun `fromBigDecimal preserves precision`() {
        val highPrecision = BigDecimal("0.0000000001")
        
        val result = converters.fromBigDecimal(highPrecision)
        
        assertEquals("0.0000000001", result)
    }

    @Test
    fun `toBigDecimal preserves precision`() {
        val result = converters.toBigDecimal("0.0000000001")
        
        assertEquals(BigDecimal("0.0000000001"), result)
    }

    @Test
    fun `fromBigDecimal does not use scientific notation`() {
        val large = BigDecimal("99999999999999999999.99")
        
        val result = converters.fromBigDecimal(large)
        
        assertFalse("Should not contain E", result?.contains("E") == true)
        assertEquals("99999999999999999999.99", result)
    }

    @Test
    fun `toBigDecimal handles zero`() {
        val result = converters.toBigDecimal("0")
        
        assertEquals(BigDecimal.ZERO, result?.stripTrailingZeros())
    }

    @Test
    fun `toBigDecimal handles negative values`() {
        val result = converters.toBigDecimal("-123.45")
        
        assertEquals(BigDecimal("-123.45"), result)
    }

    @Test
    fun `toBigDecimal returns null on invalid number format`() {
        val result = converters.toBigDecimal("not-a-number")
        assertNull(result)
    }

    @Test
    fun `BigDecimal roundtrip with scale preserves trailing zeros`() {
        val original = BigDecimal("100.00")
        
        val string = converters.fromBigDecimal(original)
        val restored = converters.toBigDecimal(string)
        
        assertEquals(original.scale(), restored?.scale())
        assertEquals("100.00", restored?.toPlainString())
    }
}
