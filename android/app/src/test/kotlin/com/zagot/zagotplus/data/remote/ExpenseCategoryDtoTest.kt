package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests for ExpenseCategoryDto conversion between DTO and Entity.
 */
class ExpenseCategoryDtoTest {

    private val testId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    // ==================== toEntity Tests ====================

    @Test
    fun `toEntity converts all fields correctly`() {
        val dto = ExpenseCategoryDto(
            id = testId.toString(),
            localId = "local-123",
            name = "Транспорт",
            isActive = true,
            createdAt = testInstant.toString(),
            syncedAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("local-123", entity.localId)
        assertEquals("Транспорт", entity.name)
        assertTrue(entity.isActive)
        assertEquals(testInstant, entity.createdAt)
        assertEquals(testInstant, entity.syncedAt)
    }

    @Test
    fun `toEntity handles null syncedAt`() {
        val dto = ExpenseCategoryDto(
            id = testId.toString(),
            localId = "local-456",
            name = "Їжа",
            isActive = false,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertNull(entity.syncedAt)
        assertFalse(entity.isActive)
    }

    @Test
    fun `toEntity preserves Ukrainian characters`() {
        val dto = ExpenseCategoryDto(
            id = testId.toString(),
            localId = "local-789",
            name = "Комунальні послуги",
            isActive = true,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = dto.toEntity()

        assertEquals("Комунальні послуги", entity.name)
    }

    // ==================== fromEntity Tests ====================

    @Test
    fun `fromEntity converts all fields correctly`() {
        val entity = ExpenseCategoryEntity(
            id = testId,
            localId = "local-123",
            name = "Пальне",
            isActive = true,
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = ExpenseCategoryDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("local-123", dto.localId)
        assertEquals("Пальне", dto.name)
        assertTrue(dto.isActive)
        assertEquals(testInstant.toString(), dto.createdAt)
        assertEquals(testInstant.toString(), dto.syncedAt)
    }

    @Test
    fun `fromEntity handles null syncedAt`() {
        val entity = ExpenseCategoryEntity(
            id = testId,
            localId = "local-456",
            name = "Оренда",
            isActive = true,
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = ExpenseCategoryDto.fromEntity(entity)

        assertNull(dto.syncedAt)
    }

    @Test
    fun `fromEntity handles inactive category`() {
        val entity = ExpenseCategoryEntity(
            id = testId,
            localId = "local-789",
            name = "Застаріла категорія",
            isActive = false,
            createdAt = testInstant,
            syncedAt = null
        )

        val dto = ExpenseCategoryDto.fromEntity(entity)

        assertFalse(dto.isActive)
    }

    // ==================== Round-trip Tests ====================

    @Test
    fun `round trip entity to dto to entity preserves all data`() {
        val original = ExpenseCategoryEntity(
            id = testId,
            localId = "round-trip-test",
            name = "Категорія тест",
            isActive = true,
            createdAt = testInstant,
            syncedAt = testInstant
        )

        val dto = ExpenseCategoryDto.fromEntity(original)
        val restored = dto.toEntity()

        assertEquals(original.id, restored.id)
        assertEquals(original.localId, restored.localId)
        assertEquals(original.name, restored.name)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.syncedAt, restored.syncedAt)
    }

    @Test
    fun `round trip dto to entity to dto preserves all data`() {
        val original = ExpenseCategoryDto(
            id = testId.toString(),
            localId = "round-trip-dto",
            name = "DTO тест",
            isActive = false,
            createdAt = testInstant.toString(),
            syncedAt = null
        )

        val entity = original.toEntity()
        val restored = ExpenseCategoryDto.fromEntity(entity)

        assertEquals(original.id, restored.id)
        assertEquals(original.localId, restored.localId)
        assertEquals(original.name, restored.name)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.syncedAt, restored.syncedAt)
    }
}
