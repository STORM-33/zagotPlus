package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.domain.model.LocationType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LocationRepositoryImplTest {

    private lateinit var locationDao: LocationDao
    private lateinit var repository: LocationRepositoryImpl

    private val locationId1 = UUID.randomUUID()
    private val locationId2 = UUID.randomUUID()
    private val now = Instant.now()

    @Before
    fun setup() {
        locationDao = mockk()
        repository = LocationRepositoryImpl(locationDao)
    }

    private fun createLocationEntity(
        id: UUID,
        name: String,
        type: String
    ) = LocationEntity(
        id = id,
        name = name,
        type = type,
        createdAt = now
    )

    @Test
    fun `getAllLocations returns mapped locations`() = runTest {
        val entities = listOf(
            createLocationEntity(locationId1, "Кіоск 1", "kiosk"),
            createLocationEntity(locationId2, "Склад", "mobile")
        )
        every { locationDao.getAllFlow() } returns flowOf(entities)

        val locations = repository.getAllLocations().first()

        assertEquals(2, locations.size)
        assertEquals(locationId1, locations[0].id)
        assertEquals("Кіоск 1", locations[0].name)
        assertEquals(LocationType.KIOSK, locations[0].type)
        assertEquals(locationId2, locations[1].id)
        assertEquals(LocationType.MOBILE, locations[1].type)
    }

    @Test
    fun `getAllLocations returns empty list when no locations`() = runTest {
        every { locationDao.getAllFlow() } returns flowOf(emptyList())

        val locations = repository.getAllLocations().first()

        assertEquals(0, locations.size)
    }

    @Test
    fun `getLocationsByType returns only kiosk locations`() = runTest {
        val kioskEntities = listOf(
            createLocationEntity(locationId1, "Кіоск 1", "kiosk")
        )
        every { locationDao.getByTypeFlow("kiosk") } returns flowOf(kioskEntities)

        val locations = repository.getLocationsByType("kiosk").first()

        assertEquals(1, locations.size)
        assertEquals(LocationType.KIOSK, locations[0].type)
    }

    @Test
    fun `getLocationsByType returns only mobile locations`() = runTest {
        val mobileEntities = listOf(
            createLocationEntity(locationId2, "Склад", "mobile")
        )
        every { locationDao.getByTypeFlow("mobile") } returns flowOf(mobileEntities)

        val locations = repository.getLocationsByType("mobile").first()

        assertEquals(1, locations.size)
        assertEquals(LocationType.MOBILE, locations[0].type)
    }

    @Test
    fun `getLocationById returns mapped location when found`() = runTest {
        val entity = createLocationEntity(locationId1, "Кіоск 1", "kiosk")
        coEvery { locationDao.getById(locationId1) } returns entity

        val location = repository.getLocationById(locationId1)

        assertEquals(locationId1, location?.id)
        assertEquals("Кіоск 1", location?.name)
        assertEquals(LocationType.KIOSK, location?.type)
        assertEquals(now, location?.createdAt)
    }

    @Test
    fun `getLocationById returns null when not found`() = runTest {
        coEvery { locationDao.getById(locationId1) } returns null

        val location = repository.getLocationById(locationId1)

        assertNull(location)
    }
}
