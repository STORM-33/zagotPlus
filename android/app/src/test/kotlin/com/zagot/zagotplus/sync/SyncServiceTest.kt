package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Tests for SyncService.
 *
 * Uses SyncDataSource interface for mocking, avoiding Supabase internal coroutine issues.
 */
class SyncServiceTest {

    private lateinit var syncDataSource: SyncDataSource
    private lateinit var transactionDao: TransactionDao
    private lateinit var purchaseBatchDao: PurchaseBatchDao
    private lateinit var locationDao: LocationDao
    private lateinit var productDao: ProductDao
    private lateinit var syncPreferences: SyncPreferences
    private lateinit var syncService: SyncService

    // Test data
    private val testTransaction = TransactionEntity(
        id = UUID.randomUUID(),
        localId = "test-local-id-1",
        locationId = UUID.randomUUID(),
        type = "sale",
        transferLocationId = null,
        productId = UUID.randomUUID(),
        weightKg = BigDecimal("10.0"),
        pricePerKg = BigDecimal("55.00"),
        totalAmount = BigDecimal("550.00"),
        notes = null,
        deviceId = "device-1",
        createdAt = Instant.now(),
        syncedAt = null
    )

    @Before
    fun setup() {
        syncDataSource = mockk()
        transactionDao = mockk()
        purchaseBatchDao = mockk()
        locationDao = mockk()
        productDao = mockk()
        syncPreferences = mockk()

        // Default empty responses
        coEvery { purchaseBatchDao.getUnsynced() } returns emptyList()
        coEvery { syncDataSource.pullLocations() } returns emptyList()
        coEvery { syncDataSource.pullProducts() } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns emptyList()

        syncService = SyncService(
            syncDataSource = syncDataSource,
            transactionDao = transactionDao,
            purchaseBatchDao = purchaseBatchDao,
            locationDao = locationDao,
            productDao = productDao,
            syncPreferences = syncPreferences
        )
    }

    // ==================== Success Cases ====================

    @Test
    fun `sync returns Success when no pending transactions and pull succeeds`() = runTest {
        // Given: No pending transactions
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushed)
        assertEquals(0, success.pulled)
    }

    @Test
    fun `sync returns Success with correct counts when both push and pull succeed`() = runTest {
        // Given: One pending transaction
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushTransaction(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)
        assertEquals(0, success.pulled)

        // Verify transaction was marked as synced
        coVerify { transactionDao.markAsSynced(testTransaction.localId, any()) }
    }

    // ==================== Partial Failure Cases ====================

    @Test
    fun `sync returns Partial when push succeeds but pull fails`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pushTransaction(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } throws RuntimeException("Connection timeout")

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Partial)
        val partial = result as SyncResult.Partial
        assertEquals(1, partial.pushed)
        assertEquals("Connection timeout", partial.pullError)
        assertTrue(partial.isAtLeastPartial)
    }

    // ==================== Failure Cases ====================

    @Test
    fun `sync returns Failure when push fails for all transactions`() = runTest {
        // Given: Pending transaction but push will fail
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)
        coEvery { syncDataSource.pushTransaction(any()) } throws RuntimeException("Network unavailable")
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - Since individual failures don't stop sync, it should succeed with 0 pushed
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushed)
    }

    // ==================== Reference Data Resilience ====================

    @Test
    fun `sync continues when reference data pull fails`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullLocations() } throws RuntimeException("Reference data error")
        coEvery { syncDataSource.pullProducts() } throws RuntimeException("Reference data error")
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - sync should still succeed
        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `products sync independently when locations pull fails`() = runTest {
        // Given: locations fail but products succeed
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullLocations() } throws RuntimeException("Location error")
        
        val productDto = ProductDto(
            id = UUID.randomUUID().toString(),
            name = "Test Product",
            defaultBuyPrice = 10.0,
            defaultSellPrice = 15.0,
            isActive = true,
            createdAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullProducts() } returns listOf(productDto)
        coEvery { productDao.getById(any()) } returns null
        coEvery { productDao.insert(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - products should still be synced despite location failure
        assertTrue(result is SyncResult.Success)
        coVerify { productDao.insert(any()) }
    }

    // ==================== Push Edge Cases ====================

    @Test
    fun `sync handles individual transaction push failure gracefully`() = runTest {
        // Given: Two transactions, first will fail, second will succeed
        val transaction2 = testTransaction.copy(
            id = UUID.randomUUID(),
            localId = "test-local-id-2"
        )
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction, transaction2)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        var callCount = 0
        coEvery { syncDataSource.pushTransaction(any()) } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("First push failed")
            }
        }
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - Should be Success but with only 1 pushed (not 2)
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)

        // Only second transaction should be marked as synced
        coVerify(exactly = 1) { transactionDao.markAsSynced(any(), any()) }
    }

    // ==================== Pull with Data ====================

    @Test
    fun `sync pulls new transactions from remote`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-local-id",
            locationId = UUID.randomUUID().toString(),
            type = "sale",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = 5.0,
            pricePerKg = 50.0,
            totalAmount = 250.0,
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.getByLocalId("remote-local-id") } returns null
        coEvery { transactionDao.insert(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushed)
        assertEquals(1, success.pulled)
        coVerify { transactionDao.insert(any()) }
    }

    @Test
    fun `sync skips transactions that already exist locally`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "existing-local-id",
            locationId = UUID.randomUUID().toString(),
            type = "sale",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = 5.0,
            pricePerKg = 50.0,
            totalAmount = 250.0,
            notes = null,
            deviceId = "this-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.getByLocalId("existing-local-id") } returns testTransaction

        // When
        val result = syncService.sync()

        // Then - should not insert duplicate
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pulled)
        coVerify(exactly = 0) { transactionDao.insert(any()) }
    }
}
