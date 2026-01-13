package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.remote.dto.LocationDto
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for LocationDto serialization and conversion.
 */
class LocationDtoTest {

    private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `toEntity converts all fields correctly`() {
        val dto = LocationDto(
            id = testId.toString(),
            name = "Кіоск 1",
            type = "kiosk",
            createdAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals(testId, entity.id)
        assertEquals("Кіоск 1", entity.name)
        assertEquals("kiosk", entity.type)
        assertEquals(testInstant, entity.createdAt)
    }

    @Test
    fun `toEntity handles mobile type correctly`() {
        val dto = LocationDto(
            id = testId.toString(),
            name = "Мобільний",
            type = "mobile",
            createdAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals("mobile", entity.type)
    }

    @Test
    fun `fromEntity converts all fields correctly`() {
        val entity = LocationEntity(
            id = testId,
            name = "Точка 2",
            type = "kiosk",
            createdAt = testInstant
        )

        val dto = LocationDto.fromEntity(entity)

        assertEquals(testId.toString(), dto.id)
        assertEquals("Точка 2", dto.name)
        assertEquals("kiosk", dto.type)
        assertEquals(testInstant.toString(), dto.createdAt)
    }

    @Test
    fun `roundtrip entity to dto and back preserves data`() {
        val originalEntity = LocationEntity(
            id = testId,
            name = "Roundtrip Test",
            type = "mobile",
            createdAt = testInstant
        )

        val dto = LocationDto.fromEntity(originalEntity)
        val convertedEntity = dto.toEntity()

        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.name, convertedEntity.name)
        assertEquals(originalEntity.type, convertedEntity.type)
        assertEquals(originalEntity.createdAt, convertedEntity.createdAt)
    }

    @Test
    fun `toEntity handles unicode names correctly`() {
        val dto = LocationDto(
            id = testId.toString(),
            name = "Точка збору №3 - Центральний ринок",
            type = "kiosk",
            createdAt = testInstant.toString()
        )

        val entity = dto.toEntity()

        assertEquals("Точка збору №3 - Центральний ринок", entity.name)
    }

    @Test
    fun `toEntity handles ISO-8601 timestamp with timezone`() {
        val dto = LocationDto(
            id = testId.toString(),
            name = "Test",
            type = "kiosk",
            createdAt = "2024-06-15T14:30:00Z"
        )

        val entity = dto.toEntity()

        assertEquals(Instant.parse("2024-06-15T14:30:00Z"), entity.createdAt)
    }
}
