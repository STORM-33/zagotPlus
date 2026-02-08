package com.zagot.zagotplus.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * DAO tests for PurchaseBatchDao using Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PurchaseBatchDaoTest {

    private lateinit var database: ZagotDatabase
    private lateinit var purchaseBatchDao: PurchaseBatchDao
    private lateinit var locationDao: LocationDao

    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")
    private lateinit var testLocationId: UUID

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        purchaseBatchDao = database.purchaseBatchDao()
        locationDao = database.locationDao()

        // Create required foreign key entity
        testLocationId = UUID.randomUUID()
        kotlinx.coroutines.runBlocking {
            locationDao.insert(LocationEntity(testLocationId, "Test Location", "kiosk", testInstant, localId = "loc-$testLocationId"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createBatch(
        id: UUID = UUID.randomUUID(),
        localId: String = "batch-${UUID.randomUUID()}",
        locationId: UUID? = testLocationId,
        notes: String? = null,
        totalWeightKg: BigDecimal = BigDecimal("200.00"),
        totalAmount: BigDecimal = BigDecimal("9000.00"),
        itemCount: Int = 3,
        syncedAt: Instant? = null,
        createdAt: Instant = testInstant,
        isVoided: Boolean = false,
        correctsBatchId: UUID? = null,
        correctionReason: String? = null
    ) = PurchaseBatchEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = "test-device",
        createdAt = createdAt,
        syncedAt = syncedAt,
        isVoided = isVoided,
        correctsBatchId = correctsBatchId,
        correctionReason = correctionReason
    )

    // ==================== Insert Tests ====================

    @Test
    fun `insert batch stores it in database`() = runTest {
        val batch = createBatch()
        
        purchaseBatchDao.insert(batch)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertNotNull(retrieved)
        assertEquals(batch.localId, retrieved?.localId)
    }

    @Test
    fun `insertAll stores multiple batches`() = runTest {
        val batches = listOf(
            createBatch(),
            createBatch(),
            createBatch()
        )
        
        purchaseBatchDao.insertAll(batches)
        
        val all = purchaseBatchDao.observeAll().first()
        assertEquals(3, all.size)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getById returns null for non-existent batch`() = runTest {
        val result = purchaseBatchDao.getById(UUID.randomUUID())
        
        assertNull(result)
    }

    @Test
    fun `getByLocalId finds batch by local id`() = runTest {
        val batch = createBatch(localId = "unique-batch-local-id")
        purchaseBatchDao.insert(batch)
        
        val retrieved = purchaseBatchDao.getByLocalId("unique-batch-local-id")
        
        assertNotNull(retrieved)
        assertEquals(batch.id, retrieved?.id)
    }

    @Test
    fun `observeAll returns batches ordered by created_at DESC`() = runTest {
        val earlier = Instant.parse("2024-01-01T10:00:00Z")
        val later = Instant.parse("2024-01-15T10:00:00Z")
        
        purchaseBatchDao.insert(createBatch(createdAt = earlier))
        purchaseBatchDao.insert(createBatch(createdAt = later))
        
        val all = purchaseBatchDao.observeAll().first()
        
        assertEquals(2, all.size)
        assertTrue(all[0].createdAt >= all[1].createdAt)
    }

    // ==================== Date Range Tests ====================

    @Test
    fun `getBatchesInRange returns batches within date range`() = runTest {
        val jan1 = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val jan15 = LocalDate.of(2024, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        val jan20 = LocalDate.of(2024, 1, 20).atStartOfDay().toInstant(ZoneOffset.UTC)
        val feb1 = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        
        purchaseBatchDao.insert(createBatch(createdAt = jan1))   // Before range
        purchaseBatchDao.insert(createBatch(createdAt = jan15))  // In range
        purchaseBatchDao.insert(createBatch(createdAt = jan20))  // In range
        purchaseBatchDao.insert(createBatch(createdAt = feb1))   // After range
        
        val inRange = purchaseBatchDao.getBatchesInRange(
            startMillis = jan10Millis(),
            endMillis = jan25Millis()
        )
        
        assertEquals(2, inRange.size)
    }

    private fun jan10Millis() = LocalDate.of(2024, 1, 10).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun jan25Millis() = LocalDate.of(2024, 1, 25).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `observeBatchesInRange emits updates reactively`() = runTest {
        val jan15 = LocalDate.of(2024, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        
        purchaseBatchDao.insert(createBatch(createdAt = jan15))
        
        val inRange = purchaseBatchDao.observeBatchesInRange(
            startMillis = jan10Millis(),
            endMillis = jan25Millis()
        ).first()
        
        assertEquals(1, inRange.size)
    }

    // ==================== Sync Tests ====================

    @Test
    fun `getUnsynced returns only batches with null syncedAt`() = runTest {
        purchaseBatchDao.insertAll(listOf(
            createBatch(syncedAt = null),
            createBatch(syncedAt = testInstant),
            createBatch(syncedAt = null)
        ))
        
        val unsynced = purchaseBatchDao.getUnsynced()
        
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { it.syncedAt == null })
    }

    @Test
    fun `markSynced updates syncedAt`() = runTest {
        val batch = createBatch(syncedAt = null)
        purchaseBatchDao.insert(batch)
        
        val syncTime = Instant.now()
        purchaseBatchDao.markSynced(batch.id, syncTime)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertNotNull(retrieved?.syncedAt)
        assertEquals(syncTime.toEpochMilli(), retrieved?.syncedAt?.toEpochMilli())
    }

    // ==================== Update Tests ====================

    @Test
    fun `update modifies existing batch`() = runTest {
        val batch = createBatch(notes = null)
        purchaseBatchDao.insert(batch)
        
        val updated = batch.copy(notes = "Updated notes")
        purchaseBatchDao.update(updated)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertEquals("Updated notes", retrieved?.notes)
    }

    // ==================== Delete Tests ====================

    @Test
    fun `delete removes batch by id`() = runTest {
        val batch = createBatch()
        purchaseBatchDao.insert(batch)
        
        purchaseBatchDao.delete(batch.id)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertNull(retrieved)
    }

    // ==================== BigDecimal Precision Tests ====================

    @Test
    fun `BigDecimal totals are stored with correct precision`() = runTest {
        val batch = createBatch(
            totalWeightKg = BigDecimal("1234.567"),
            totalAmount = BigDecimal("55555.99")
        )
        
        purchaseBatchDao.insert(batch)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertEquals(BigDecimal("1234.567"), retrieved?.totalWeightKg)
        assertEquals(BigDecimal("55555.99"), retrieved?.totalAmount)
    }

    // ==================== Voiding Tests ====================

    @Test
    fun `markVoided sets isVoided and audit fields`() = runTest {
        val batch = createBatch()
        purchaseBatchDao.insert(batch)
        
        val voidedAt = Instant.now().toEpochMilli()
        purchaseBatchDao.markVoided(batch.id, voidedAt, "voiding-device")
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isVoided)
        assertEquals(voidedAt, retrieved.voidedAt?.toEpochMilli())
        assertEquals("voiding-device", retrieved.voidedByDeviceId)
        // syncedAt should be cleared to trigger re-sync
        assertNull(retrieved.syncedAt)
    }

    @Test
    fun `markVoided clears syncedAt to trigger resync`() = runTest {
        val batch = createBatch(syncedAt = testInstant)
        purchaseBatchDao.insert(batch)
        
        // Verify it was synced
        val beforeVoid = purchaseBatchDao.getById(batch.id)
        assertNotNull(beforeVoid?.syncedAt)
        
        // Void the batch
        purchaseBatchDao.markVoided(batch.id, Instant.now().toEpochMilli(), "test-device")
        
        // syncedAt should be null now
        val afterVoid = purchaseBatchDao.getById(batch.id)
        assertNull(afterVoid?.syncedAt)
    }

    @Test
    fun `observeAll excludes voided batches`() = runTest {
        purchaseBatchDao.insertAll(listOf(
            createBatch(isVoided = false),
            createBatch(isVoided = true),
            createBatch(isVoided = false)
        ))
        
        val all = purchaseBatchDao.observeAll().first()
        
        assertEquals(2, all.size)
        assertTrue(all.none { it.isVoided })
    }

    @Test
    fun `getBatchesInRange excludes voided batches`() = runTest {
        val jan15 = LocalDate.of(2024, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        
        purchaseBatchDao.insertAll(listOf(
            createBatch(createdAt = jan15, isVoided = false),
            createBatch(createdAt = jan15, isVoided = true),
            createBatch(createdAt = jan15, isVoided = false)
        ))
        
        val inRange = purchaseBatchDao.getBatchesInRange(
            startMillis = jan10Millis(),
            endMillis = jan25Millis()
        )
        
        assertEquals(2, inRange.size)
        assertTrue(inRange.none { it.isVoided })
    }

    @Test
    fun `observeBatchesInRange excludes voided batches`() = runTest {
        val jan15 = LocalDate.of(2024, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        
        purchaseBatchDao.insertAll(listOf(
            createBatch(createdAt = jan15, isVoided = false),
            createBatch(createdAt = jan15, isVoided = true)
        ))
        
        val inRange = purchaseBatchDao.observeBatchesInRange(
            startMillis = jan10Millis(),
            endMillis = jan25Millis()
        ).first()
        
        assertEquals(1, inRange.size)
        assertFalse(inRange[0].isVoided)
    }

    @Test
    fun `getAllPaginated excludes voided batches`() = runTest {
        purchaseBatchDao.insertAll(listOf(
            createBatch(isVoided = false),
            createBatch(isVoided = true),
            createBatch(isVoided = false),
            createBatch(isVoided = true)
        ))
        
        val paged = purchaseBatchDao.getAllPaginated(10, 0)
        
        assertEquals(2, paged.size)
        assertTrue(paged.none { it.isVoided })
    }

    @Test
    fun `getTotalCount excludes voided batches`() = runTest {
        purchaseBatchDao.insertAll(listOf(
            createBatch(isVoided = false),
            createBatch(isVoided = true),
            createBatch(isVoided = false)
        ))
        
        val count = purchaseBatchDao.getTotalCount()
        
        assertEquals(2, count)
    }

    @Test
    fun `getById returns voided batch for correction lookup`() = runTest {
        // getById should still return voided batches so we can look up corrections
        val batch = createBatch(isVoided = true)
        purchaseBatchDao.insert(batch)
        
        val retrieved = purchaseBatchDao.getById(batch.id)
        
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isVoided)
    }

    @Test
    fun `correction batch stores correctsBatchId and reason`() = runTest {
        val originalBatch = createBatch()
        purchaseBatchDao.insert(originalBatch)
        
        val correctionBatch = createBatch(
            correctsBatchId = originalBatch.id,
            correctionReason = "Помилка при зважуванні"
        )
        purchaseBatchDao.insert(correctionBatch)
        
        val retrieved = purchaseBatchDao.getById(correctionBatch.id)
        
        assertNotNull(retrieved)
        assertEquals(originalBatch.id, retrieved!!.correctsBatchId)
        assertEquals("Помилка при зважуванні", retrieved.correctionReason)
    }
}
