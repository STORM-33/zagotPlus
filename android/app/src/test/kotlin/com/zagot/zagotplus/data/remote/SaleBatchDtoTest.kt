package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for SaleBatchDto serialization and conversion.
 */
class SaleBatchDtoTest {

    private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testLocationId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `toEntity converts all fields correctly`() {
        val dto = SaleBatchDto(
            id = testId.toString(),
            localId = "local-123",
            locationId = testLocationId.toString(),
            notes = "Test notes",
            totalWeightKg = 150.5,
            totalAmount = 7525.00,
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals(testLocationId, entity.locationId)
        assertEquals("Test notes", entity.notes)
        assertEquals(BigDecimal.valueOf(150.5), entity.totalWeightKg)
        assertEquals(BigDecimal.valueOf(7525.00), entity.totalAmount)
        assertEquals(5, entity.itemCount)
        assertEquals("device-abc", entity.deviceId)
        assertEquals(testInstant, entity.createdAt)
        assertEquals(testInstant, entity.syncedAt)
    }

    @Test
    fun `toEntity handles null fields correctly`() {
        val dto = SaleBatchDto(
            id = testId.toString(),
            localId = "local-456",
            locationId = null,
            notes = null,
            totalWeightKg = null,
            totalAmount = null,
            itemCount = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-456", entity.localId)
        assertNull(entity.locationId)
        assertNull(entity.notes)
        assertNull(entity.totalWeightKg)
        assertNull(entity.totalAmount)
        assertNull(entity.itemCount)
        assertNull(entity.deviceId)
        assertEquals(testInstant, entity.createdAt)
        assertNull(entity.syncedAt)
    }

    @Test
    fun `fromEntity converts all fields correctly`() {
        val entity = SaleBatchEntity(
            id = testId,
            localId = "local-789",
            locationId = testLocationId,
            notes = "Entity notes",
            totalWeightKg = BigDecimal("200.50"),
            totalAmount = BigDecimal("10025.00"),
            itemCount = 10,
            deviceId = "device-xyz",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = SaleBatchDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-789", dto.localId)
        assertEquals(testLocationId.toString(), dto.locationId)
        assertEquals("Entity notes", dto.notes)
        assertEquals(200.50, dto.totalWeightKg!!, 0.001)
        assertEquals(10025.00, dto.totalAmount!!, 0.001)
        assertEquals(10, dto.itemCount)
        assertEquals("device-xyz", dto.deviceId)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals(testInstant.toString(), dto.syncedAt)
    }

    @Test
    fun `fromEntity handles null fields correctly`() {
        val entity = SaleBatchEntity(
            id = testId,
            localId = "local-abc",
            locationId = null,
            notes = null,
            totalWeightKg = null,
            totalAmount = null,
            itemCount = null,
            deviceId = null,
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = SaleBatchDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-abc", dto.localId)
        assertNull(dto.locationId)
        assertNull(dto.notes)
        assertNull(dto.totalWeightKg)
        assertNull(dto.totalAmount)
        assertNull(dto.itemCount)
        assertNull(dto.deviceId)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertNull(dto.syncedAt)
    }

    @Test
    fun `roundtrip entity to dto and back preserves data`() {
        val originalEntity = SaleBatchEntity(
            id = testId,
            localId = "roundtrip-test",
            locationId = testLocationId,
            notes = "Roundtrip test notes",
            totalWeightKg = BigDecimal("333.33"),
            totalAmount = BigDecimal("16666.50"),
            itemCount = 7,
            deviceId = "device-roundtrip",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = SaleBatchDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.localId, convertedEntity.localId)
        assertEquals(originalEntity.locationId, convertedEntity.locationId)
        assertEquals(originalEntity.notes, convertedEntity.notes)
        // Note: BigDecimal precision may differ slightly due to Double conversion
        assertEquals(originalEntity.totalWeightKg?.toDouble(), convertedEntity.totalWeightKg?.toDouble())
        assertEquals(originalEntity.totalAmount?.toDouble(), convertedEntity.totalAmount?.toDouble())
        assertEquals(originalEntity.itemCount, convertedEntity.itemCount)
        assertEquals(originalEntity.deviceId, convertedEntity.deviceId)
        assertEquals(originalEntity.createdAt, convertedEntity.createdAt)
        assertEquals(originalEntity.syncedAt, convertedEntity.syncedAt)
    }

    @Test
    fun `toEntity handles zero values correctly`() {
        val dto = SaleBatchDto(
            id = testId.toString(),
            localId = "local-zero",
            locationId = null,
            notes = null,
            totalWeightKg = 0.0,
            totalAmount = 0.0,
            itemCount = 0,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(BigDecimal.valueOf(0.0), entity.totalWeightKg)
        assertEquals(BigDecimal.valueOf(0.0), entity.totalAmount)
        assertEquals(0, entity.itemCount)
    }
}
