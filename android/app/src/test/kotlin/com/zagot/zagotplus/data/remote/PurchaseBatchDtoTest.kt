package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for PurchaseBatchDto serialization and conversion.
 */
class PurchaseBatchDtoTest {

    private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testLocationId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `toEntity converts all fields correctly`() {
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-123",
            locationId = testLocationId.toString(),
            notes = "Test batch notes",
            totalWeightKg = "150.5",
            totalAmount = "7525.00",
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals(testLocationId, entity.locationId)
        assertEquals("Test batch notes", entity.notes)
        assertEquals(0, BigDecimal("150.5").compareTo(entity.totalWeightKg))
        assertEquals(0, BigDecimal("7525.00").compareTo(entity.totalAmount))
        assertEquals(5, entity.itemCount)
        assertEquals("device-abc", entity.deviceId)
        assertEquals(testInstant, entity.createdAt)
        assertEquals(testInstant, entity.syncedAt)
    }

    @Test
    fun `toEntity handles null fields correctly`() {
        val dto = PurchaseBatchDto(
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
        val entity = PurchaseBatchEntity(
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

        val dto = PurchaseBatchDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-789", dto.localId)
        assertEquals(testLocationId.toString(), dto.locationId)
        assertEquals("Entity notes", dto.notes)
        assertEquals("200.50", dto.totalWeightKg)
        assertEquals("10025.00", dto.totalAmount)
        assertEquals(10, dto.itemCount)
        assertEquals("device-xyz", dto.deviceId)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals(testInstant.toString(), dto.syncedAt)
    }

    @Test
    fun `fromEntity handles null fields correctly`() {
        val entity = PurchaseBatchEntity(
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

        val dto = PurchaseBatchDto.fromEntity(entity)

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
        val originalEntity = PurchaseBatchEntity(
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

        val dto = PurchaseBatchDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.localId, convertedEntity.localId)
        assertEquals(originalEntity.locationId, convertedEntity.locationId)
        assertEquals(originalEntity.notes, convertedEntity.notes)
        assertEquals(originalEntity.totalWeightKg?.toDouble(), convertedEntity.totalWeightKg?.toDouble())
        assertEquals(originalEntity.totalAmount?.toDouble(), convertedEntity.totalAmount?.toDouble())
        assertEquals(originalEntity.itemCount, convertedEntity.itemCount)
        assertEquals(originalEntity.deviceId, convertedEntity.deviceId)
        assertEquals(originalEntity.createdAt, convertedEntity.createdAt)
        assertEquals(originalEntity.syncedAt, convertedEntity.syncedAt)
    }

    @Test
    fun `toEntity handles zero values correctly`() {
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-zero",
            locationId = null,
            notes = null,
            totalWeightKg = "0.0",
            totalAmount = "0.0",
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

    @Test
    fun `toEntity handles large batch correctly`() {
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-large",
            locationId = testLocationId.toString(),
            notes = "Large batch from market",
            totalWeightKg = "5000.0",
            totalAmount = "250000.00",
            itemCount = 100,
            deviceId = "device-truck",
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(0, BigDecimal("5000.0").compareTo(entity.totalWeightKg))
        assertEquals(0, BigDecimal("250000.00").compareTo(entity.totalAmount))
        assertEquals(100, entity.itemCount)
    }

    @Test
    fun `toEntity handles unicode notes`() {
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-unicode",
            locationId = null,
            notes = "Закупка від постачальника №5 - Центральний ринок",
            totalWeightKg = "50.0",
            totalAmount = "2500.0",
            itemCount = 3,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("Закупка від постачальника №5 - Центральний ринок", entity.notes)
    }

    // ==================== Voiding Fields Tests ====================

    @Test
    fun `toEntity converts voiding fields correctly`() {
        val voidedAt = Instant.parse("2024-01-16T14:30:00Z")
        val correctsBatchId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002")
        
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-voided",
            locationId = testLocationId.toString(),
            notes = "Voided batch",
            totalWeightKg = "100.0",
            totalAmount = "5000.0",
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString(),
            isVoided = true,
            correctsBatchId = correctsBatchId.toString(),
            correctionReason = "Помилка при зважуванні",
            voidedAt = voidedAt.toString(),
            voidedByDeviceId = "device-xyz"
        )

        val entity = dto.toEntity()

        assertTrue(entity.isVoided)
        assertEquals(correctsBatchId, entity.correctsBatchId)
        assertEquals("Помилка при зважуванні", entity.correctionReason)
        assertEquals(voidedAt, entity.voidedAt)
        assertEquals("device-xyz", entity.voidedByDeviceId)
    }

    @Test
    fun `toEntity defaults voiding fields when not provided`() {
        val dto = PurchaseBatchDto(
            id = testId.toString(),
            localId = "local-not-voided",
            locationId = testLocationId.toString(),
            notes = null,
            totalWeightKg = "100.0",
            totalAmount = "5000.0",
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant.toString(),
            syncedAt = null
            // voiding fields not specified - should default
        )

        val entity = dto.toEntity()

        assertFalse(entity.isVoided)
        assertNull(entity.correctsBatchId)
        assertNull(entity.correctionReason)
        assertNull(entity.voidedAt)
        assertNull(entity.voidedByDeviceId)
    }

    @Test
    fun `fromEntity converts voiding fields correctly`() {
        val voidedAt = Instant.parse("2024-01-16T14:30:00Z")
        val correctsBatchId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002")
        
        val entity = PurchaseBatchEntity(
            id = testId,
            localId = "local-voided-entity",
            locationId = testLocationId,
            notes = "Voided entity",
            totalWeightKg = BigDecimal("100.00"),
            totalAmount = BigDecimal("5000.00"),
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant,
            syncedAt = testInstant,
            isVoided = true,
            correctsBatchId = correctsBatchId,
            correctionReason = "Невірна вага",
            voidedAt = voidedAt,
            voidedByDeviceId = "device-xyz"
        )

        val dto = PurchaseBatchDto.fromEntity(entity)

        assertTrue(dto.isVoided)
        assertEquals(correctsBatchId.toString(), dto.correctsBatchId)
        assertEquals("Невірна вага", dto.correctionReason)
        assertEquals(voidedAt.toString(), dto.voidedAt)
        assertEquals("device-xyz", dto.voidedByDeviceId)
    }

    @Test
    fun `roundtrip preserves voiding fields`() {
        val voidedAt = Instant.parse("2024-01-16T14:30:00Z")
        val correctsBatchId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002")
        
        val originalEntity = PurchaseBatchEntity(
            id = testId,
            localId = "roundtrip-voided",
            locationId = testLocationId,
            notes = "Roundtrip voided",
            totalWeightKg = BigDecimal("100.00"),
            totalAmount = BigDecimal("5000.00"),
            itemCount = 5,
            deviceId = "device-abc",
            createdAt = testInstant,
            syncedAt = testInstant,
            isVoided = true,
            correctsBatchId = correctsBatchId,
            correctionReason = "Помилка оператора",
            voidedAt = voidedAt,
            voidedByDeviceId = "device-xyz"
        )

        val dto = PurchaseBatchDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.isVoided, convertedEntity.isVoided)
        assertEquals(originalEntity.correctsBatchId, convertedEntity.correctsBatchId)
        assertEquals(originalEntity.correctionReason, convertedEntity.correctionReason)
        assertEquals(originalEntity.voidedAt, convertedEntity.voidedAt)
        assertEquals(originalEntity.voidedByDeviceId, convertedEntity.voidedByDeviceId)
    }
}
