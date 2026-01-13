package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Tests for CashOperationDto conversion between DTO and Entity.
 */
class CashOperationDtoTest {

    private val testId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val testLocationId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val testCategoryId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val testBatchId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    // ==================== toEntity Tests ====================

    @Test
    fun `toEntity converts deposit operation correctly`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "local-123",
            locationId = testLocationId.toString(),
            type = "deposit",
            amount = 1000.50,
            categoryId = null,
            batchId = null,
            notes = "Початкова каса",
            deviceId = "device-1",
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals(testLocationId, entity.locationId)
        assertEquals("deposit", entity.type)
        assertEquals(BigDecimal.valueOf(1000.50), entity.amount)
        assertNull(entity.categoryId)
        assertNull(entity.batchId)
        assertEquals("Початкова каса", entity.notes)
        assertEquals("device-1", entity.deviceId)
        assertEquals(testInstant, entity.createdAt)
        assertEquals(testInstant, entity.syncedAt)
    }

    @Test
    fun `toEntity converts withdrawal operation correctly`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "local-456",
            locationId = testLocationId.toString(),
            type = "withdrawal",
            amount = 500.0,
            categoryId = null,
            batchId = null,
            notes = "Видача готівки",
            deviceId = "device-2",
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("withdrawal", entity.type)
        assertEquals(BigDecimal.valueOf(500.0), entity.amount)
        assertNull(entity.syncedAt)
    }

    @Test
    fun `toEntity converts payment operation with category`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "local-789",
            locationId = testLocationId.toString(),
            type = "payment",
            amount = 250.0,
            categoryId = testCategoryId.toString(),
            batchId = null,
            notes = "Оплата за транспорт",
            deviceId = "device-1",
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("payment", entity.type)
        assertEquals(testCategoryId, entity.categoryId)
        assertNull(entity.batchId)
    }

    @Test
    fun `toEntity converts purchase operation linked to batch`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "local-abc",
            locationId = testLocationId.toString(),
            type = "purchase",
            amount = 4500.0,
            categoryId = null,
            batchId = testBatchId.toString(),
            notes = null,
            deviceId = "device-1",
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("purchase", entity.type)
        assertEquals(testBatchId, entity.batchId)
        assertNull(entity.categoryId)
    }

    @Test
    fun `toEntity handles null locationId`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "local-no-location",
            locationId = null,
            type = "deposit",
            amount = 100.0,
            categoryId = null,
            batchId = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertNull(entity.locationId)
        assertNull(entity.deviceId)
        assertNull(entity.notes)
    }

    // ==================== fromEntity Tests ====================

    @Test
    fun `fromEntity converts deposit operation correctly`() {
        val entity = CashOperationEntity(
            id = testId,
            localId = "local-123",
            locationId = testLocationId,
            type = "deposit",
            amount = BigDecimal("1000.50"),
            categoryId = null,
            batchId = null,
            notes = "Початкова каса",
            deviceId = "device-1",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = CashOperationDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-123", dto.localId)
        assertEquals(testLocationId.toString(), dto.locationId)
        assertEquals("deposit", dto.type)
        assertEquals(1000.50, dto.amount, 0.001)
        assertNull(dto.categoryId)
        assertNull(dto.batchId)
        assertEquals("Початкова каса", dto.notes)
        assertEquals("device-1", dto.deviceId)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals(testInstant.toString(), dto.syncedAt)
    }

    @Test
    fun `fromEntity handles payment with category`() {
        val entity = CashOperationEntity(
            id = testId,
            localId = "local-pay",
            locationId = testLocationId,
            type = "payment",
            amount = BigDecimal("350.00"),
            categoryId = testCategoryId,
            batchId = null,
            notes = "Витрати на пальне",
            deviceId = "device-2",
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = CashOperationDto.fromEntity(entity)

        assertEquals("payment", dto.type)
        assertEquals(testCategoryId.toString(), dto.categoryId)
        assertNull(dto.batchId)
        assertNull(dto.syncedAt)
    }

    @Test
    fun `fromEntity handles purchase linked to batch`() {
        val entity = CashOperationEntity(
            id = testId,
            localId = "local-purch",
            locationId = testLocationId,
            type = "purchase",
            amount = BigDecimal("9000.00"),
            categoryId = null,
            batchId = testBatchId,
            notes = null,
            deviceId = "device-1",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = CashOperationDto.fromEntity(entity)

        assertEquals("purchase", dto.type)
        assertEquals(testBatchId.toString(), dto.batchId)
        assertNull(dto.categoryId)
    }

    @Test
    fun `fromEntity handles null optional fields`() {
        val entity = CashOperationEntity(
            id = testId,
            localId = "local-minimal",
            locationId = null,
            type = "deposit",
            amount = BigDecimal("100.00"),
            categoryId = null,
            batchId = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = CashOperationDto.fromEntity(entity)

        assertNull(dto.locationId)
        assertNull(dto.categoryId)
        assertNull(dto.batchId)
        assertNull(dto.notes)
        assertNull(dto.deviceId)
        assertNull(dto.syncedAt)
    }

    // ==================== Round-trip Tests ====================

    @Test
    fun `round trip entity to dto to entity preserves all data for deposit`() {
        val original = CashOperationEntity(
            id = testId,
            localId = "round-trip-deposit",
            locationId = testLocationId,
            type = "deposit",
            amount = BigDecimal("1234.56"),
            categoryId = null,
            batchId = null,
            notes = "Тестовий депозит",
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = CashOperationDto.fromEntity(original)
        val restored = dto.toEntity()

        assertEquals(original.id, restored.id)
        assertEquals(original.localId, restored.localId)
        assertEquals(original.locationId, restored.locationId)
        assertEquals(original.type, restored.type)
        // BigDecimal comparison with scale handling
        assertEquals(0, original.amount.compareTo(restored.amount))
        assertEquals(original.categoryId, restored.categoryId)
        assertEquals(original.batchId, restored.batchId)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.deviceId, restored.deviceId)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.syncedAt, restored.syncedAt)
    }

    @Test
    fun `round trip entity to dto to entity preserves all data for payment with category`() {
        val original = CashOperationEntity(
            id = testId,
            localId = "round-trip-payment",
            locationId = testLocationId,
            type = "payment",
            amount = BigDecimal("500.00"),
            categoryId = testCategoryId,
            batchId = null,
            notes = "Оплата транспорту",
            deviceId = "device-1",
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = CashOperationDto.fromEntity(original)
        val restored = dto.toEntity()

        assertEquals(original.type, restored.type)
        assertEquals(original.categoryId, restored.categoryId)
        assertNull(restored.batchId)
    }

    @Test
    fun `round trip preserves data for purchase with batch link`() {
        val original = CashOperationEntity(
            id = testId,
            localId = "round-trip-purchase",
            locationId = testLocationId,
            type = "purchase",
            amount = BigDecimal("4500.00"),
            categoryId = null,
            batchId = testBatchId,
            notes = null,
            deviceId = "device-1",
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = CashOperationDto.fromEntity(original)
        val restored = dto.toEntity()

        assertEquals(original.type, restored.type)
        assertEquals(original.batchId, restored.batchId)
        assertNull(restored.categoryId)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles zero amount`() {
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "zero-amount",
            locationId = testLocationId.toString(),
            type = "deposit",
            amount = 0.0,
            categoryId = null,
            batchId = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(BigDecimal.ZERO.setScale(1), entity.amount)
    }

    @Test
    fun `handles large amount`() {
        val largeAmount = 999999999.99
        val dto = CashOperationDto(
            id = testId.toString(),
            localId = "large-amount",
            locationId = testLocationId.toString(),
            type = "deposit",
            amount = largeAmount,
            categoryId = null,
            batchId = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(0, BigDecimal.valueOf(largeAmount).compareTo(entity.amount))
    }

    @Test
    fun `preserves Ukrainian notes text`() {
        val ukrainianNotes = "Оплата за комунальні послуги — опалення та водопостачання"
        val entity = CashOperationEntity(
            id = testId,
            localId = "ukr-notes",
            locationId = testLocationId,
            type = "payment",
            amount = BigDecimal("2500.00"),
            categoryId = testCategoryId,
            batchId = null,
            notes = ukrainianNotes,
            deviceId = "device-1",
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = CashOperationDto.fromEntity(entity)
        val restored = dto.toEntity()

        assertEquals(ukrainianNotes, restored.notes)
    }
}
