package com.zagot.zagotplus.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * DAO tests for LocationDao using Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LocationDaoTest {

    private lateinit var database: ZagotDatabase
    private lateinit var locationDao: LocationDao

    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        locationDao = database.locationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createLocation(
        id: UUID = UUID.randomUUID(),
        name: String = "Склад №1",
        type: String = "kiosk"
    ) = LocationEntity(
        id = id,
        name = name,
        type = type,
        createdAt = testInstant
    )

    // ==================== Insert Tests ====================

    @Test
    fun `insert location stores it in database`() = runTest {
        val location = createLocation(name = "Мобільний пункт")
        
        locationDao.insert(location)
        
        val retrieved = locationDao.getById(location.id)
        assertNotNull(retrieved)
        assertEquals("Мобільний пункт", retrieved?.name)
    }

    @Test
    fun `insert with conflict replaces existing location`() = runTest {
        val id = UUID.randomUUID()
        val original = createLocation(id = id, name = "Original")
        val updated = createLocation(id = id, name = "Updated")
        
        locationDao.insert(original)
        locationDao.insert(updated)
        
        val retrieved = locationDao.getById(id)
        assertEquals("Updated", retrieved?.name)
    }

    @Test
    fun `insertAll stores multiple locations`() = runTest {
        val locations = listOf(
            createLocation(name = "Локація 1"),
            createLocation(name = "Локація 2"),
            createLocation(name = "Локація 3")
        )
        
        locationDao.insertAll(locations)
        
        val all = locationDao.getAll()
        assertEquals(3, all.size)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getAll returns all locations ordered by created_at`() = runTest {
        val earlier = Instant.parse("2024-01-01T10:00:00Z")
        val later = Instant.parse("2024-01-15T10:00:00Z")
        
        locationDao.insert(LocationEntity(UUID.randomUUID(), "Later", "kiosk", later))
        locationDao.insert(LocationEntity(UUID.randomUUID(), "Earlier", "kiosk", earlier))
        
        val all = locationDao.getAll()
        
        assertEquals(2, all.size)
        assertEquals("Earlier", all[0].name)
        assertEquals("Later", all[1].name)
    }

    @Test
    fun `getAllFlow emits updates reactively`() = runTest {
        val location1 = createLocation(name = "Локація 1")
        locationDao.insert(location1)
        
        val initial = locationDao.getAllFlow().first()
        assertEquals(1, initial.size)
        
        locationDao.insert(createLocation(name = "Локація 2"))
        
        val updated = locationDao.getAllFlow().first()
        assertEquals(2, updated.size)
    }

    @Test
    fun `getById returns null for non-existent location`() = runTest {
        val result = locationDao.getById(UUID.randomUUID())
        
        assertNull(result)
    }

    // ==================== Type Filter Tests ====================

    @Test
    fun `getByTypeFlow returns only locations of specified type`() = runTest {
        locationDao.insertAll(listOf(
            createLocation(name = "Кіоск 1", type = "kiosk"),
            createLocation(name = "Мобільний 1", type = "mobile"),
            createLocation(name = "Кіоск 2", type = "kiosk")
        ))
        
        val kiosks = locationDao.getByTypeFlow("kiosk").first()
        
        assertEquals(2, kiosks.size)
        assertTrue(kiosks.all { it.type == "kiosk" })
    }

    @Test
    fun `getByTypeFlow returns empty for non-existent type`() = runTest {
        locationDao.insert(createLocation(type = "kiosk"))
        
        val warehouses = locationDao.getByTypeFlow("warehouse").first()
        
        assertTrue(warehouses.isEmpty())
    }

    // ==================== Update Tests ====================

    @Test
    fun `update modifies existing location`() = runTest {
        val location = createLocation(name = "Original")
        locationDao.insert(location)
        
        val updated = location.copy(name = "Modified")
        locationDao.update(updated)
        
        val retrieved = locationDao.getById(location.id)
        assertEquals("Modified", retrieved?.name)
    }

    // ==================== Delete Tests ====================

    @Test
    fun `deleteAll clears all locations`() = runTest {
        locationDao.insertAll(listOf(
            createLocation(name = "1"),
            createLocation(name = "2"),
            createLocation(name = "3")
        ))
        
        locationDao.deleteAll()
        
        val all = locationDao.getAll()
        assertTrue(all.isEmpty())
    }
}
