package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for TransactionDto serialization and conversion.
 */
class TransactionDtoTest {

    private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testLocationId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    private val testProductId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002")
    private val testBatchId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `toEntity converts purchase transaction correctly`() {
        val dto = TransactionDto(
            id = testId.toString(),
            localId = "local-123",
            locationId = testLocationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = testProductId.toString(),
            weightKg = 25.5,
            pricePerKg = 50.0,
            totalAmount = 1275.0,
            notes = "Test purchase",
            deviceId = "device-abc",
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString(),
            batchId = testBatchId.toString(),
            saleBatchId = null
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals(testLocationId, entity.locationId)
        assertEquals("purchase", entity.type)
        assertNull(entity.transferLocationId)
        assertEquals(testProductId, entity.productId)
        assertEquals(BigDecimal.valueOf(25.5), entity.weightKg)
        assertEquals(BigDecimal.valueOf(50.0), entity.pricePerKg)
        assertEquals(BigDecimal.valueOf(1275.0), entity.totalAmount)
        assertEquals("Test purchase", entity.notes)
        assertEquals("device-abc", entity.deviceId)
        assertEquals(testInstant, entity.createdAt)
        assertEquals(testInstant, entity.syncedAt)
        assertEquals(testBatchId, entity.batchId)
        assertNull(entity.saleBatchId)
    }

    @Test
    fun `toEntity converts transfer transaction correctly`() {
        val transferLocationId = UUID.fromString("990e8400-e29b-41d4-a716-446655440004")
        
        val dto = TransactionDto(
            id = testId.toString(),
            localId = "local-transfer",
            locationId = testLocationId.toString(),
            type = "transfer_out",
            transferLocationId = transferLocationId.toString(),
            productId = testProductId.toString(),
            weightKg = 10.0,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = "device-xyz",
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("transfer_out", entity.type)
        assertEquals(transferLocationId, entity.transferLocationId)
        assertNull(entity.pricePerKg)
        assertNull(entity.totalAmount)
        assertNull(entity.syncedAt)
    }

    @Test
    fun `toEntity handles null fields correctly`() {
        val dto = TransactionDto(
            id = testId.toString(),
            localId = "local-minimal",
            locationId = null,
            type = "sale",
            transferLocationId = null,
            productId = null,
            weightKg = 5.0,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null,
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        assertNull(entity.locationId)
        assertNull(entity.productId)
        assertNull(entity.pricePerKg)
        assertNull(entity.totalAmount)
        assertNull(entity.notes)
        assertNull(entity.deviceId)
        assertNull(entity.syncedAt)
        assertNull(entity.batchId)
        assertNull(entity.saleBatchId)
    }

    @Test
    fun `fromEntity converts all fields correctly`() {
        val saleBatchId = UUID.fromString("aa0e8400-e29b-41d4-a716-446655440005")
        
        val entity = TransactionEntity(
            id = testId,
            localId = "local-789",
            locationId = testLocationId,
            type = "sale",
            transferLocationId = null,
            productId = testProductId,
            weightKg = BigDecimal("100.25"),
            pricePerKg = BigDecimal("75.00"),
            totalAmount = BigDecimal("7518.75"),
            notes = "Wholesale sale",
            deviceId = "device-sale",
            createdAt = testInstant,
            syncedAt = testInstant,
            batchId = null,
            saleBatchId = saleBatchId
        )

        val dto = TransactionDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-789", dto.localId)
        assertEquals(testLocationId.toString(), dto.locationId)
        assertEquals("sale", dto.type)
        assertNull(dto.transferLocationId)
        assertEquals(testProductId.toString(), dto.productId)
        assertEquals(100.25, dto.weightKg, 0.001)
        assertEquals(75.00, dto.pricePerKg!!, 0.001)
        assertEquals(7518.75, dto.totalAmount!!, 0.001)
        assertEquals("Wholesale sale", dto.notes)
        assertEquals("device-sale", dto.deviceId)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals(testInstant.toString(), dto.syncedAt)
        assertNull(dto.batchId)
        assertEquals(saleBatchId.toString(), dto.saleBatchId)
    }

    @Test
    fun `fromEntity handles null fields correctly`() {
        val entity = TransactionEntity(
            id = testId,
            localId = "local-null-test",
            locationId = null,
            type = "purchase",
            transferLocationId = null,
            productId = null,
            weightKg = BigDecimal.ZERO,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant,
            syncedAt = null,
            batchId = null,
            saleBatchId = null
        )

        val dto = TransactionDto.fromEntity(entity)

        assertNull(dto.locationId)
        assertNull(dto.productId)
        assertNull(dto.pricePerKg)
        assertNull(dto.totalAmount)
        assertNull(dto.notes)
        assertNull(dto.deviceId)
        assertNull(dto.syncedAt)
        assertNull(dto.batchId)
        assertNull(dto.saleBatchId)
    }

    @Test
    fun `roundtrip preserves all data`() {
        val originalEntity = TransactionEntity(
            id = testId,
            localId = "roundtrip-tx",
            locationId = testLocationId,
            type = "purchase",
            transferLocationId = null,
            productId = testProductId,
            weightKg = BigDecimal("55.55"),
            pricePerKg = BigDecimal("60.00"),
            totalAmount = BigDecimal("3333.00"),
            notes = "Roundtrip test",
            deviceId = "device-rt",
            createdAt = testInstant,
            syncedAt = testInstant,
            batchId = testBatchId,
            saleBatchId = null
        )

        val dto = TransactionDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.localId, convertedEntity.localId)
        assertEquals(originalEntity.locationId, convertedEntity.locationId)
        assertEquals(originalEntity.type, convertedEntity.type)
        assertEquals(originalEntity.productId, convertedEntity.productId)
        assertEquals(originalEntity.weightKg.toDouble(), convertedEntity.weightKg.toDouble(), 0.001)
        assertEquals(originalEntity.pricePerKg?.toDouble(), convertedEntity.pricePerKg?.toDouble())
        assertEquals(originalEntity.totalAmount?.toDouble(), convertedEntity.totalAmount?.toDouble())
        assertEquals(originalEntity.notes, convertedEntity.notes)
        assertEquals(originalEntity.deviceId, convertedEntity.deviceId)
        assertEquals(originalEntity.createdAt, convertedEntity.createdAt)
        assertEquals(originalEntity.syncedAt, convertedEntity.syncedAt)
        assertEquals(originalEntity.batchId, convertedEntity.batchId)
    }

    @Test
    fun `toEntity handles all transaction types`() {
        val types = listOf("purchase", "sale", "transfer_out", "transfer_in")
        
        for (type in types) {
            val dto = TransactionDto(
                id = testId.toString(),
                localId = "local-$type",
                locationId = testLocationId.toString(),
                type = type,
                transferLocationId = if (type.startsWith("transfer")) testLocationId.toString() else null,
                productId = testProductId.toString(),
                weightKg = 10.0,
                pricePerKg = 50.0,
                totalAmount = 500.0,
                notes = null,
                deviceId = null,
                createdAt = testInstant.toString(),
                syncedAt = null
            )

            val entity = dto.toEntity()
            assertEquals(type, entity.type)
        }
    }

    @Test
    fun `toEntity handles zero weight correctly`() {
        val dto = TransactionDto(
            id = testId.toString(),
            localId = "local-zero-weight",
            locationId = null,
            type = "purchase",
            transferLocationId = null,
            productId = null,
            weightKg = 0.0,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = null,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals(BigDecimal.valueOf(0.0), entity.weightKg)
    }
}
