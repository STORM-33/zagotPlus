package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.domain.model.PurchaseBatch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PurchaseBatchRepositoryImplTest {

    private lateinit var database: ZagotDatabase
    private lateinit var purchaseBatchDao: PurchaseBatchDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: PurchaseBatchRepositoryImpl

    private val batchId1 = UUID.randomUUID()
    private val batchId2 = UUID.randomUUID()
    private val locationId = UUID.randomUUID()
    private val now = Instant.now()

    @Before
    fun setup() {
        database = mockk()
        purchaseBatchDao = mockk()
        transactionDao = mockk()
        repository = PurchaseBatchRepositoryImpl(database, purchaseBatchDao, transactionDao)
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
        syncedAt: Instant? = null
    ) = PurchaseBatchEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    @Test
    fun `observeAll returns mapped batches`() = runTest {
        val entities = listOf(
            createBatchEntity(batchId1, notes = "Batch 1"),
            createBatchEntity(batchId2, notes = "Batch 2")
        )
        every { purchaseBatchDao.observeAll() } returns flowOf(entities)

        val batches = repository.observeAll().first()

        assertEquals(2, batches.size)
        assertEquals(batchId1, batches[0].id)
        assertEquals("Batch 1", batches[0].notes)
        assertEquals(batchId2, batches[1].id)
        assertEquals("Batch 2", batches[1].notes)
    }

    @Test
    fun `observeAll returns empty list when no batches`() = runTest {
        every { purchaseBatchDao.observeAll() } returns flowOf(emptyList())

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
        coEvery { purchaseBatchDao.getById(batchId1) } returns entity

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
        coEvery { purchaseBatchDao.getById(batchId1) } returns null

        val batch = repository.getById(batchId1)

        assertNull(batch)
    }

    @Test
    fun `getByLocalId returns mapped batch when found`() = runTest {
        val localId = "local-123"
        val entity = createBatchEntity(batchId1, localId = localId)
        coEvery { purchaseBatchDao.getByLocalId(localId) } returns entity

        val batch = repository.getByLocalId(localId)

        assertEquals(batchId1, batch?.id)
        assertEquals(localId, batch?.localId)
    }

    @Test
    fun `getByLocalId returns null when not found`() = runTest {
        coEvery { purchaseBatchDao.getByLocalId("unknown") } returns null

        val batch = repository.getByLocalId("unknown")

        assertNull(batch)
    }

    @Test
    fun `getUnsynced returns only batches without syncedAt`() = runTest {
        val unsyncedEntity = createBatchEntity(batchId1, syncedAt = null)
        coEvery { purchaseBatchDao.getUnsynced() } returns listOf(unsyncedEntity)

        val batches = repository.getUnsynced()

        assertEquals(1, batches.size)
        assertNull(batches[0].syncedAt)
    }

    @Test
    fun `markSynced calls dao with correct parameters`() = runTest {
        coEvery { purchaseBatchDao.markSynced(batchId1, any()) } returns Unit

        repository.markSynced(batchId1)

        coVerify { purchaseBatchDao.markSynced(batchId1, any()) }
    }

    @Test
    fun `delete calls dao with correct id`() = runTest {
        coEvery { purchaseBatchDao.delete(batchId1) } returns Unit

        repository.delete(batchId1)

        coVerify { purchaseBatchDao.delete(batchId1) }
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
        every { purchaseBatchDao.observeAll() } returns flowOf(listOf(entity))

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
        every { purchaseBatchDao.observeAll() } returns flowOf(listOf(entity))

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
}
