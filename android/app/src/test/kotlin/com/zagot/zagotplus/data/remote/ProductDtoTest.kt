package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.remote.dto.ProductDto
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for ProductDto serialization and conversion.
 */
class ProductDtoTest {

    private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `toEntity converts all fields correctly`() {
        val dto = ProductDto(
            id = testId.toString(),
            localId = "local-123",
            name = "Горіх білий",
            defaultBuyPrice = 50.0,
            defaultSellPrice = 75.0,
            isActive = true,
            createdAt = testInstant.toString(),
            imageUri = "content://images/1"
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals("Горіх білий", entity.name)
        assertEquals(BigDecimal.valueOf(50.0), entity.defaultBuyPrice)
        assertEquals(BigDecimal.valueOf(75.0), entity.defaultSellPrice)
        assertTrue(entity.isActive)
        assertEquals(testInstant, entity.createdAt)
        assertEquals("content://images/1", entity.imageUri)
        assertNotNull(entity.syncedAt) // Should be set to now
    }

    @Test
    fun `toEntity handles null prices correctly`() {
        val dto = ProductDto(
            id = testId.toString(),
            localId = "local-456",
            name = "Насіння",
            defaultBuyPrice = null,
            defaultSellPrice = null,
            isActive = false,
            createdAt = testInstant.toString(),
            imageUri = null
        )

        val entity = dto.toEntity()

        assertNull(entity.defaultBuyPrice)
        assertNull(entity.defaultSellPrice)
        assertFalse(entity.isActive)
        assertNull(entity.imageUri)
    }

    @Test
    fun `fromEntity converts all fields correctly`() {
        val entity = ProductEntity(
            id = testId,
            localId = "local-789",
            name = "Горіх чорний",
            defaultBuyPrice = BigDecimal("45.50"),
            defaultSellPrice = BigDecimal("68.00"),
            isActive = true,
            createdAt = testInstant,
            syncedAt = testInstant,
            imageUri = "file://path/to/image.jpg"
        )

        val dto = ProductDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-789", dto.localId)
        assertEquals("Горіх чорний", dto.name)
        assertEquals(45.50, dto.defaultBuyPrice!!, 0.001)
        assertEquals(68.00, dto.defaultSellPrice!!, 0.001)
        assertTrue(dto.isActive)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals("file://path/to/image.jpg", dto.imageUri)
    }

    @Test
    fun `fromEntity handles null prices correctly`() {
        val entity = ProductEntity(
            id = testId,
            localId = "local-abc",
            name = "Test Product",
            defaultBuyPrice = null,
            defaultSellPrice = null,
            isActive = false,
            createdAt = testInstant,
            syncedAt = null,
            imageUri = null
        )

        val dto = ProductDto.fromEntity(entity)

        assertNull(dto.defaultBuyPrice)
        assertNull(dto.defaultSellPrice)
        assertNull(dto.imageUri)
    }

    @Test
    fun `roundtrip preserves numeric precision`() {
        val originalEntity = ProductEntity(
            id = testId,
            localId = "roundtrip-test",
            name = "Precision Test",
            defaultBuyPrice = BigDecimal("123.45"),
            defaultSellPrice = BigDecimal("199.99"),
            isActive = true,
            createdAt = testInstant,
            syncedAt = testInstant,
            imageUri = null
        )

        val dto = ProductDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.defaultBuyPrice?.toDouble(), convertedEntity.defaultBuyPrice?.toDouble())
        assertEquals(originalEntity.defaultSellPrice?.toDouble(), convertedEntity.defaultSellPrice?.toDouble())
    }

    @Test
    fun `toEntity handles zero prices correctly`() {
        val dto = ProductDto(
            id = testId.toString(),
            localId = "local-zero",
            name = "Free Product",
            defaultBuyPrice = 0.0,
            defaultSellPrice = 0.0,
            isActive = true,
            createdAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(BigDecimal.valueOf(0.0), entity.defaultBuyPrice)
        assertEquals(BigDecimal.valueOf(0.0), entity.defaultSellPrice)
    }

    @Test
    fun `toEntity handles unicode product names`() {
        val dto = ProductDto(
            id = testId.toString(),
            localId = "local-unicode",
            name = "Насіння соняшникове смажене",
            defaultBuyPrice = 30.0,
            defaultSellPrice = 45.0,
            isActive = true,
            createdAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals("Насіння соняшникове смажене", entity.name)
    }
}
