package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SaleBatchRepositoryImplTest {

    private lateinit var database: ZagotDatabase
    private lateinit var saleBatchDao: SaleBatchDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var syncManager: SyncManager
    private lateinit var repository: SaleBatchRepositoryImpl

    private val batchId1 = UUID.randomUUID()
    private val batchId2 = UUID.randomUUID()
    private val locationId = UUID.randomUUID()
    private val now = Instant.now()

    @Before
    fun setup() {
        database = mockk()
        saleBatchDao = mockk()
        transactionDao = mockk()
        syncManager = mockk(relaxed = true)
        repository = SaleBatchRepositoryImpl(database, saleBatchDao, transactionDao, syncManager)
    }

    private fun createBatchEntity(
        id: UUID,
        localId: String = UUID.randomUUID().toString(),
        locationId: UUID? = this.locationId,
        notes: String? = null,
        totalWeightKg: BigDecimal? = BigDecimal("100.00"),
        totalAmount: BigDecimal? = BigDecimal("5000.00"),
        itemCount: Int? = 3,
        deviceId: String? = "test-device",
        createdAt: Instant = now,
        syncedAt: Instant? = null,
        isVoided: Boolean = false,
        correctsBatchId: UUID? = null,
        correctionReason: String? = null
    ) = SaleBatchEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt,
        isVoided = isVoided,
        correctsBatchId = correctsBatchId,
        correctionReason = correctionReason
    )

    @Test
    fun `observeAll returns mapped batches`() = runTest {
        val entities = listOf(
            createBatchEntity(batchId1, notes = "Batch 1"),
            createBatchEntity(batchId2, notes = "Batch 2")
        )
        every { saleBatchDao.observeAll() } returns flowOf(entities)

        val batches = repository.observeAll().first()

        assertEquals(2, batches.size)
        assertEquals(batchId1, batches[0].id)
        assertEquals("Batch 1", batches[0].notes)
        assertEquals(batchId2, batches[1].id)
        assertEquals("Batch 2", batches[1].notes)
    }

    @Test
    fun `observeAll returns empty list when no batches`() = runTest {
        every { saleBatchDao.observeAll() } returns flowOf(emptyList())

        val batches = repository.observeAll().first()

        assertEquals(0, batches.size)
    }

    @Test
    fun `getById returns mapped batch when found`() = runTest {
        val entity = createBatchEntity(
            batchId1,
            notes = "Test batch",
            totalWeightKg = BigDecimal("150.50"),
            totalAmount = BigDecimal("7525.00"),
            itemCount = 5
        )
        coEvery { saleBatchDao.getById(batchId1) } returns entity

        val batch = repository.getById(batchId1)

        assertEquals(batchId1, batch?.id)
        assertEquals("Test batch", batch?.notes)
        assertEquals(BigDecimal("150.50"), batch?.totalWeightKg)
        assertEquals(BigDecimal("7525.00"), batch?.totalAmount)
        assertEquals(5, batch?.itemCount)
        assertEquals(locationId, batch?.locationId)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        coEvery { saleBatchDao.getById(batchId1) } returns null

        val batch = repository.getById(batchId1)

        assertNull(batch)
    }

    @Test
    fun `getByLocalId returns mapped batch when found`() = runTest {
        val localId = "local-123"
        val entity = createBatchEntity(batchId1, localId = localId)
        coEvery { saleBatchDao.getByLocalId(localId) } returns entity

        val batch = repository.getByLocalId(localId)

        assertEquals(batchId1, batch?.id)
        assertEquals(localId, batch?.localId)
    }

    @Test
    fun `getByLocalId returns null when not found`() = runTest {
        coEvery { saleBatchDao.getByLocalId("unknown") } returns null

        val batch = repository.getByLocalId("unknown")

        assertNull(batch)
    }

    @Test
    fun `getUnsynced returns only batches without syncedAt`() = runTest {
        val unsyncedEntity = createBatchEntity(batchId1, syncedAt = null)
        coEvery { saleBatchDao.getUnsynced() } returns listOf(unsyncedEntity)

        val batches = repository.getUnsynced()

        assertEquals(1, batches.size)
        assertNull(batches[0].syncedAt)
    }

    @Test
    fun `markSynced calls dao with correct parameters`() = runTest {
        coEvery { saleBatchDao.markSynced(batchId1, any()) } returns Unit

        repository.markSynced(batchId1)

        coVerify { saleBatchDao.markSynced(batchId1, any()) }
    }

    @Test
    fun `delete calls dao with correct id`() = runTest {
        coEvery { saleBatchDao.delete(batchId1) } returns Unit

        repository.delete(batchId1)

        coVerify { saleBatchDao.delete(batchId1) }
    }

    @Test
    fun `observeAll maps all fields correctly`() = runTest {
        val entity = createBatchEntity(
            id = batchId1,
            localId = "local-456",
            locationId = locationId,
            notes = "Full test",
            totalWeightKg = BigDecimal("200.00"),
            totalAmount = BigDecimal("10000.00"),
            itemCount = 10,
            deviceId = "device-abc",
            createdAt = now,
            syncedAt = now
        )
        every { saleBatchDao.observeAll() } returns flowOf(listOf(entity))

        val batches = repository.observeAll().first()

        assertEquals(1, batches.size)
        val batch = batches[0]
        assertEquals(batchId1, batch.id)
        assertEquals("local-456", batch.localId)
        assertEquals(locationId, batch.locationId)
        assertEquals("Full test", batch.notes)
        assertEquals(BigDecimal("200.00"), batch.totalWeightKg)
        assertEquals(BigDecimal("10000.00"), batch.totalAmount)
        assertEquals(10, batch.itemCount)
        assertEquals("device-abc", batch.deviceId)
        assertEquals(now, batch.createdAt)
        assertEquals(now, batch.syncedAt)
    }

    @Test
    fun `observeAll handles null fields correctly`() = runTest {
        val entity = createBatchEntity(
            id = batchId1,
            locationId = null,
            notes = null,
            totalWeightKg = null,
            totalAmount = null,
            itemCount = null,
            deviceId = null,
            syncedAt = null
        )
        every { saleBatchDao.observeAll() } returns flowOf(listOf(entity))

        val batches = repository.observeAll().first()

        assertEquals(1, batches.size)
        val batch = batches[0]
        assertNull(batch.locationId)
        assertNull(batch.notes)
        assertNull(batch.totalWeightKg)
        assertNull(batch.totalAmount)
        assertNull(batch.itemCount)
        assertNull(batch.deviceId)
        assertNull(batch.syncedAt)
    }

    @Test
    fun `getAllBatchesPaginated calls dao with correct parameters`() = runTest {
        val entities = listOf(createBatchEntity(batchId1))
        coEvery { saleBatchDao.getAllPaginated(10, 20) } returns entities

        val batches = repository.getAllBatchesPaginated(10, 20)

        assertEquals(1, batches.size)
        coVerify { saleBatchDao.getAllPaginated(10, 20) }
    }

    @Test
    fun `getTotalBatchCount calls dao`() = runTest {
        coEvery { saleBatchDao.getActiveCount() } returns 42

        val count = repository.getTotalBatchCount()

        assertEquals(42, count)
    }

    @Test
    fun `observeTotalBatchCount returns flow from dao`() = runTest {
        every { saleBatchDao.observeTotalCount() } returns flowOf(15)

        val count = repository.observeTotalBatchCount().first()

        assertEquals(15, count)
    }

    @Test
    fun `getTransactionsForBatch returns transactions from dao`() = runTest {
        coEvery { transactionDao.getBySaleBatchId(batchId1) } returns emptyList()

        val transactions = repository.getTransactionsForBatch(batchId1)

        assertEquals(0, transactions.size)
        coVerify { transactionDao.getBySaleBatchId(batchId1) }
    }

    // ==================== Correction Tests ====================

    @Test
    fun `correctBatch throws when original batch not found`() = runTest {
        coEvery { saleBatchDao.getById(batchId1) } returns null

        val correctedBatch = SaleBatch(
            id = UUID.randomUUID(),
            localId = "local",
            locationId = null,
            notes = null,
            totalWeightKg = null,
            totalAmount = null,
            itemCount = null,
            deviceId = null,
            createdAt = now,
            syncedAt = null
        )

        try {
            repository.correctBatch(batchId1, correctedBatch, emptyList(), "reason")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Партію не знайдено", e.message)
        }
    }

    @Test
    fun `correctBatch throws when original batch already voided`() = runTest {
        val voidedEntity = createBatchEntity(batchId1, isVoided = true)
        coEvery { saleBatchDao.getById(batchId1) } returns voidedEntity

        val correctedBatch = SaleBatch(
            id = UUID.randomUUID(),
            localId = "local",
            locationId = null,
            notes = null,
            totalWeightKg = null,
            totalAmount = null,
            itemCount = null,
            deviceId = null,
            createdAt = now,
            syncedAt = null
        )

        try {
            repository.correctBatch(batchId1, correctedBatch, emptyList(), "reason")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Неможливо виправити вже анульовану партію", e.message)
        }
    }

    @Test
    fun `observeAll maps correction fields correctly`() = runTest {
        val entity = createBatchEntity(
            id = batchId1,
            isVoided = true,
            correctsBatchId = batchId2,
            correctionReason = "Test correction"
        )
        every { saleBatchDao.observeAll() } returns flowOf(listOf(entity))

        val batches = repository.observeAll().first()

        assertEquals(1, batches.size)
        val batch = batches[0]
        assertTrue(batch.isVoided)
        assertEquals(batchId2, batch.correctsBatchId)
        assertEquals("Test correction", batch.correctionReason)
    }

    @Test
    fun `getById maps correction fields correctly`() = runTest {
        val entity = createBatchEntity(
            id = batchId1,
            isVoided = false,
            correctsBatchId = batchId2,
            correctionReason = "Corrected data"
        )
        coEvery { saleBatchDao.getById(batchId1) } returns entity

        val batch = repository.getById(batchId1)

        assertNotNull(batch)
        assertFalse(batch!!.isVoided)
        assertEquals(batchId2, batch.correctsBatchId)
        assertEquals("Corrected data", batch.correctionReason)
    }
}
