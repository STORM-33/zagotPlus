package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import androidx.room.withTransaction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.invoke
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
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

    private lateinit var database: ZagotDatabase
    private lateinit var syncDataSource: SyncDataSource
    private lateinit var transactionDao: TransactionDao
    private lateinit var purchaseBatchDao: PurchaseBatchDao
    private lateinit var saleBatchDao: SaleBatchDao
    private lateinit var locationDao: LocationDao
    private lateinit var productDao: ProductDao
    private lateinit var expenseCategoryDao: ExpenseCategoryDao
    private lateinit var cashOperationDao: CashOperationDao
    private lateinit var syncPreferences: SyncPreferences
    private lateinit var supabaseAuthManager: com.zagot.zagotplus.data.remote.SupabaseAuthManager
    private lateinit var devicePreferences: com.zagot.zagotplus.data.preferences.DevicePreferences
    private lateinit var syncService: SyncService

    // Test data
    private val testExpenseCategory = ExpenseCategoryEntity(
        id = UUID.randomUUID(),
        localId = "cat-local-id-1",
        name = "Транспорт",
        isActive = true,
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val testCashOperation = CashOperationEntity(
        id = UUID.randomUUID(),
        localId = "cash-local-id-1",
        locationId = UUID.randomUUID(),
        type = "deposit",
        amount = BigDecimal("1000.00"),
        categoryId = null,
        batchId = null,
        notes = "Початкова каса",
        deviceId = "device-1",
        createdAt = Instant.now(),
        syncedAt = null
    )

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
        saleBatchDao = mockk()
        locationDao = mockk()
        productDao = mockk()
        expenseCategoryDao = mockk()
        cashOperationDao = mockk()
        syncPreferences = mockk()
        supabaseAuthManager = mockk()
        devicePreferences = mockk()
        database = mockk()

        // Mock auth and device
        coEvery { supabaseAuthManager.ensureAuthenticated(any()) } returns true
        every { devicePreferences.getDeviceId() } returns "test-device-id"

        // Default empty responses
        coEvery { productDao.getUnsynced() } returns emptyList()
        coEvery { purchaseBatchDao.getUnsynced() } returns emptyList()
        coEvery { saleBatchDao.getUnsynced() } returns emptyList()
        coEvery { expenseCategoryDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns emptyList()
        coEvery { syncDataSource.pullLocations() } returns emptyList()
        coEvery { syncDataSource.pullProducts() } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns emptyList()
        coEvery { syncDataSource.pullSaleBatches(any()) } returns emptyList()
        coEvery { syncDataSource.pullExpenseCategories(any()) } returns emptyList()
        coEvery { syncDataSource.pullCashOperations(any()) } returns emptyList()
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        
        // Default getAllLocalIds for batch deduplication
        coEvery { transactionDao.getAllLocalIds() } returns emptyList()
        coEvery { purchaseBatchDao.getAllLocalIds() } returns emptyList()
        coEvery { saleBatchDao.getAllLocalIds() } returns emptyList()
        coEvery { expenseCategoryDao.getAllLocalIds() } returns emptyList()
        coEvery { cashOperationDao.getAllLocalIds() } returns emptyList()
        
        // Default empty responses for server timestamp fetch (conflict detection)
        coEvery { syncDataSource.getTransactionTimestamps(any()) } returns emptyMap()
        coEvery { syncDataSource.getPurchaseBatchTimestamps(any()) } returns emptyMap()
        coEvery { syncDataSource.getSaleBatchTimestamps(any()) } returns emptyMap()
        coEvery { syncDataSource.getExpenseCategoryTimestamps(any()) } returns emptyMap()
        coEvery { syncDataSource.getCashOperationTimestamps(any()) } returns emptyMap()
        coEvery { syncDataSource.getProductTimestamps(any()) } returns emptyMap()
        
        // Mock DAO insertAll methods for transaction-based pulls
        coEvery { expenseCategoryDao.insertAll(any()) } just Runs
        coEvery { purchaseBatchDao.insertAll(any()) } just Runs
        coEvery { saleBatchDao.insertAll(any()) } just Runs
        coEvery { transactionDao.insertAll(any()) } just Runs
        coEvery { cashOperationDao.insertAll(any()) } just Runs
        
        // Mock DAO upsertAll methods for batch syncing (FK-safe updates)
        coEvery { purchaseBatchDao.upsertAll(any()) } just Runs
        coEvery { saleBatchDao.upsertAll(any()) } just Runs
        
        // Mock Room's withTransaction extension function
        // The function executes the block directly without actual transaction
        mockkStatic("androidx.room.RoomDatabaseKt")
        val blockSlot = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(blockSlot)) } answers {
            kotlinx.coroutines.runBlocking { blockSlot.captured.invoke() }
        }

        syncService = SyncService(
            database = database,
            syncDataSource = syncDataSource,
            transactionDao = transactionDao,
            purchaseBatchDao = purchaseBatchDao,
            saleBatchDao = saleBatchDao,
            locationDao = locationDao,
            productDao = productDao,
            expenseCategoryDao = expenseCategoryDao,
            cashOperationDao = cashOperationDao,
            syncPreferences = syncPreferences,
            supabaseAuthManager = supabaseAuthManager,
            devicePreferences = devicePreferences
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
        
        // IMPORTANT: When no records are pulled, timestamp should NOT be updated
        // This prevents clock skew issues where device time ahead of server could cause missed records
        coVerify(exactly = 0) { syncPreferences.setLastSyncTimestamp(any()) }
    }

    @Test
    fun `sync returns Success with correct counts when both push and pull succeed`() = runTest {
        // Given: One pending transaction
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
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
        
        // Timestamp should NOT be updated when no records are pulled (even if push succeeded)
        coVerify(exactly = 0) { syncPreferences.setLastSyncTimestamp(any()) }
    }

    @Test
    fun `sync updates timestamp using server_updated_at from pulled records`() = runTest {
        // Given: No pending transactions, one new transaction to pull
        val serverTimestamp = Instant.parse("2026-01-15T12:00:00Z")
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-tx-1",
            locationId = UUID.randomUUID().toString(),
            type = "purchase",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = "10.0",
            pricePerKg = "50.0",
            totalAmount = "500.0",
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = serverTimestamp.toString(),
            batchId = null,
            saleBatchId = null
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.getAllLocalIds() } returns emptyList()
        coEvery { transactionDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pulled)
        
        // Verify timestamp was updated using the server_updated_at from pulled record
        coVerify { syncPreferences.setLastSyncTimestamp(serverTimestamp) }
    }

    // ==================== Partial Failure Cases ====================

    @Test
    fun `sync returns Partial when push succeeds but pull fails`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
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
        coEvery { syncDataSource.pushTransactions(any()) } throws RuntimeException("Network unavailable")
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - Batch push failure now causes entire sync to fail
        assertTrue(result is SyncResult.Failure)
        val failure = result as SyncResult.Failure
        assertEquals("Network unavailable", failure.error)
        assertEquals(SyncPhase.PUSH, failure.phase)
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
            localId = UUID.randomUUID().toString(),
            name = "Test Product",
            defaultBuyPrice = "10.0",
            defaultSellPrice = "15.0",
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
    fun `sync batch push failure causes sync to fail`() = runTest {
        // Given: Two transactions, batch push will fail
        val transaction2 = testTransaction.copy(
            id = UUID.randomUUID(),
            localId = "test-local-id-2"
        )
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction, transaction2)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        coEvery { syncDataSource.pushTransactions(any()) } throws RuntimeException("Batch push failed")
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then - Batch push failure causes entire push phase to fail
        assertTrue(result is SyncResult.Failure)
        val failure = result as SyncResult.Failure
        assertEquals("Batch push failed", failure.error)
        assertEquals(SyncPhase.PUSH, failure.phase)

        // Neither transaction should be marked as synced
        coVerify(exactly = 0) { transactionDao.markAsSynced(any(), any()) }
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
            weightKg = "5.0",
            pricePerKg = "50.0",
            totalAmount = "250.0",
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            batchId = null,
            saleBatchId = null
        )
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushed)
        assertEquals(1, success.pulled)
        coVerify { transactionDao.insertAll(any()) }
    }

    @Test
    fun `sync upserts transactions that already exist locally`() = runTest {
        // Given: Transaction already exists locally, but server has updated version
        val existingLocalId = "existing-local-id"
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = existingLocalId,
            locationId = UUID.randomUUID().toString(),
            type = "sale",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = "5.0",
            pricePerKg = "50.0",
            totalAmount = "250.0",
            notes = null,
            deviceId = "this-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            batchId = null,
            saleBatchId = null,
            serverUpdatedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then - should upsert (insertAll with REPLACE strategy handles this)
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pulled)
        // Verify insertAll was called - Room's REPLACE strategy will handle upsert
        coVerify { transactionDao.insertAll(any()) }
    }

    // ==================== Expense Category Tests ====================

    @Test
    fun `sync pushes pending expense categories`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { expenseCategoryDao.getUnsynced() } returns listOf(testExpenseCategory)
        coEvery { expenseCategoryDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushExpenseCategories(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pushed >= 1)
        coVerify { syncDataSource.pushExpenseCategories(any()) }
        coVerify { expenseCategoryDao.markSynced(testExpenseCategory.id, any()) }
    }

    @Test
    fun `sync pulls new expense categories`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        val remoteCategory = ExpenseCategoryDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-cat-id",
            name = "Пальне",
            isActive = true,
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullExpenseCategories(any()) } returns listOf(remoteCategory)
        coEvery { expenseCategoryDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pulled >= 1)
        coVerify { expenseCategoryDao.insertAll(any()) }
    }

    @Test
    fun `sync upserts expense categories that already exist locally`() = runTest {
        // Given: Expense category already exists locally, but server has updated version
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        val remoteCategory = ExpenseCategoryDto(
            id = UUID.randomUUID().toString(),
            localId = "existing-cat-id",
            name = "Пальне",
            isActive = false,  // Updated value from server (deactivated)
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullExpenseCategories(any()) } returns listOf(remoteCategory)
        coEvery { expenseCategoryDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then - should upsert the category (insertAll with REPLACE strategy)
        assertTrue(result is SyncResult.Success)
        coVerify { expenseCategoryDao.insertAll(any()) }
    }

    @Test
    fun `sync fails when expense category batch push fails`() = runTest {
        // Given: Two categories, batch push will fail
        val category2 = testExpenseCategory.copy(
            id = UUID.randomUUID(),
            localId = "cat-local-id-2",
            name = "Їжа"
        )
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { expenseCategoryDao.getUnsynced() } returns listOf(testExpenseCategory, category2)
        coEvery { expenseCategoryDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        coEvery { syncDataSource.pushExpenseCategories(any()) } throws RuntimeException("Batch push failed")

        // When
        val result = syncService.sync()

        // Then - Batch push failure causes sync to fail
        assertTrue(result is SyncResult.Failure)
        val failure = result as SyncResult.Failure
        assertEquals("Batch push failed", failure.error)
        coVerify(exactly = 0) { expenseCategoryDao.markSynced(any(), any()) }
    }

    // ==================== Cash Operation Tests ====================

    @Test
    fun `sync pushes pending cash operations`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns listOf(testCashOperation)
        coEvery { cashOperationDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushCashOperations(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pushed >= 1)
        coVerify { syncDataSource.pushCashOperations(any()) }
        coVerify { cashOperationDao.markSynced(testCashOperation.id, any()) }
    }

    @Test
    fun `sync pulls new cash operations`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        val remoteCashOp = CashOperationDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-cash-id",
            locationId = UUID.randomUUID().toString(),
            type = "withdrawal",
            amount = "500.0",
            categoryId = null,
            batchId = null,
            notes = "Видача готівки",
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullCashOperations(any()) } returns listOf(remoteCashOp)
        coEvery { cashOperationDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pulled >= 1)
        coVerify { cashOperationDao.insertAll(any()) }
    }

    @Test
    fun `sync upserts cash operations that already exist locally`() = runTest {
        // Given: Cash operation already exists locally, but server has updated version
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        val remoteCashOp = CashOperationDto(
            id = UUID.randomUUID().toString(),
            localId = "existing-cash-id",
            locationId = UUID.randomUUID().toString(),
            type = "deposit",
            amount = "1000.0",
            categoryId = null,
            batchId = null,
            notes = "Updated notes",  // Changed field from server
            deviceId = "this-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullCashOperations(any()) } returns listOf(remoteCashOp)
        coEvery { cashOperationDao.insertAll(any()) } just Runs

        // When
        val result = syncService.sync()

        // Then - should upsert the cash operation (insertAll with REPLACE strategy)
        assertTrue(result is SyncResult.Success)
        coVerify { cashOperationDao.insertAll(any()) }
    }

    @Test
    fun `sync fails when cash operation batch push fails`() = runTest {
        // Given: Two operations, batch push will fail
        val cashOp2 = testCashOperation.copy(
            id = UUID.randomUUID(),
            localId = "cash-local-id-2",
            type = "withdrawal",
            amount = BigDecimal("500.00")
        )
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns listOf(testCashOperation, cashOp2)
        coEvery { cashOperationDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        coEvery { syncDataSource.pushCashOperations(any()) } throws RuntimeException("Batch push failed")

        // When
        val result = syncService.sync()

        // Then - Batch push failure causes sync to fail
        assertTrue(result is SyncResult.Failure)
        val failure = result as SyncResult.Failure
        assertEquals("Batch push failed", failure.error)
        coVerify(exactly = 0) { cashOperationDao.markSynced(any(), any()) }
    }

    @Test
    fun `sync handles payment cash operation with category`() = runTest {
        // Given
        val categoryId = UUID.randomUUID()
        val paymentOp = testCashOperation.copy(
            type = "payment",
            categoryId = categoryId,
            notes = "Оплата за транспорт"
        )
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns listOf(paymentOp)
        coEvery { cashOperationDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushCashOperations(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify { 
            syncDataSource.pushCashOperations(match { dtos ->
                dtos.any { it.type == "payment" && it.categoryId == categoryId.toString() }
            }) 
        }
    }

    @Test
    fun `sync handles purchase cash operation linked to batch`() = runTest {
        // Given
        val batchId = UUID.randomUUID()
        val purchaseOp = testCashOperation.copy(
            type = "purchase",
            batchId = batchId,
            amount = BigDecimal("4500.00")
        )
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns listOf(purchaseOp)
        coEvery { cashOperationDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pushCashOperations(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify { 
            syncDataSource.pushCashOperations(match { dtos ->
                dtos.any { it.type == "purchase" && it.batchId == batchId.toString() }
            }) 
        }
    }

    @Test
    fun `sync upserts transaction with batch_id from server`() = runTest {
        // Given: Server has transaction with batch_id that needs to be synced locally
        val localId = "tx-with-batch-id"
        val batchId = UUID.randomUUID()
        val saleBatchId = UUID.randomUUID()
        
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = localId,
            locationId = UUID.randomUUID().toString(),
            type = "purchase",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = "10.0",
            pricePerKg = "50.0",
            totalAmount = "500.0",
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            batchId = batchId.toString(),
            saleBatchId = saleBatchId.toString()
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.insertAll(any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then: Transaction should be upserted with batch IDs
        assertTrue(result is SyncResult.Success)
        coVerify { 
            transactionDao.insertAll(match { list ->
                list.size == 1 && 
                list[0].batchId == batchId &&
                list[0].saleBatchId == saleBatchId
            })
        }
    }

    @Test
    fun `sync does not call updateBatchIds - uses upsert instead`() = runTest {
        // Given: With new upsert-based sync, we don't use updateBatchIds anymore
        val localId = "tx-with-batch-id"
        val existingBatchId = UUID.randomUUID()
        
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = localId,
            locationId = UUID.randomUUID().toString(),
            type = "purchase",
            transferLocationId = null,
            productId = UUID.randomUUID().toString(),
            weightKg = "10.0",
            pricePerKg = "50.0",
            totalAmount = "500.0",
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            batchId = existingBatchId.toString(),
            saleBatchId = null
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        coEvery { transactionDao.insertAll(any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then: Should use insertAll (upsert), not updateBatchIds
        assertTrue(result is SyncResult.Success)
        coVerify { transactionDao.insertAll(any()) }
        // updateBatchIds should never be called - we use full upsert now
        coVerify(exactly = 0) { transactionDao.updateBatchIds(any(), any(), any()) }
    }

    // ==================== Voided Batch Sync Tests ====================
    // These tests verify the critical fix for syncing voided status across devices

    @Test
    fun `sync upserts voided purchase batch from server`() = runTest {
        // Given: A batch was voided on another device and synced to server
        // This device has the original non-voided version
        val batchId = UUID.randomUUID()
        val localId = "batch-to-be-voided"
        
        val voidedBatchFromServer = PurchaseBatchDto(
            id = batchId.toString(),
            localId = localId,
            locationId = UUID.randomUUID().toString(),
            notes = "Original batch",
            totalWeightKg = "100.0",
            totalAmount = "5000.0",
            itemCount = 5,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = true,  // CRITICAL: This batch was voided on another device
            correctsBatchId = null,
            correctionReason = null
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns listOf(voidedBatchFromServer)
        
        // When
        val result = syncService.sync()
        
        // Then: The voided batch should be upserted, updating local record
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.upsertAll(match { list ->
                list.size == 1 && list[0].isVoided == true
            })
        }
    }

    @Test
    fun `sync upserts voided sale batch from server`() = runTest {
        // Given: A sale batch was voided on another device and synced to server
        val batchId = UUID.randomUUID()
        val localId = "sale-batch-to-be-voided"
        
        val voidedSaleBatchFromServer = SaleBatchDto(
            id = batchId.toString(),
            localId = localId,
            locationId = UUID.randomUUID().toString(),
            notes = "Original sale batch",
            totalWeightKg = "50.0",
            totalAmount = "3000.0",
            itemCount = 3,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = true,  // CRITICAL: This batch was voided on another device
            correctsBatchId = null,
            correctionReason = null
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullSaleBatches(any()) } returns listOf(voidedSaleBatchFromServer)
        
        // When
        val result = syncService.sync()
        
        // Then: The voided sale batch should be upserted, updating local record
        assertTrue(result is SyncResult.Success)
        coVerify { 
            saleBatchDao.upsertAll(match { list ->
                list.size == 1 && list[0].isVoided == true
            })
        }
    }

    @Test
    fun `sync upserts correction batch with correctsBatchId from server`() = runTest {
        // Given: A correction batch was created on another device
        val originalBatchId = UUID.randomUUID()
        val correctionBatchId = UUID.randomUUID()
        val localId = "correction-batch-id"
        
        val correctionBatchFromServer = PurchaseBatchDto(
            id = correctionBatchId.toString(),
            localId = localId,
            locationId = UUID.randomUUID().toString(),
            notes = "Corrected batch",
            totalWeightKg = "95.0",  // Corrected weight
            totalAmount = "4750.0",
            itemCount = 5,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = false,
            correctsBatchId = originalBatchId.toString(),  // Links to original
            correctionReason = "Помилка при зважуванні"
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns listOf(correctionBatchFromServer)
        
        // When
        val result = syncService.sync()
        
        // Then: The correction batch should be upserted with all correction metadata
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.upsertAll(match { list ->
                list.size == 1 && 
                list[0].correctsBatchId == originalBatchId &&
                list[0].correctionReason == "Помилка при зважуванні"
            })
        }
    }

    @Test
    fun `sync pulls voided batch with audit trail fields`() = runTest {
        // Given: A batch was voided on another device with full audit trail
        val batchId = UUID.randomUUID()
        val voidedAt = Instant.parse("2024-01-16T14:30:00Z")
        
        val voidedBatchFromServer = PurchaseBatchDto(
            id = batchId.toString(),
            localId = "batch-with-audit",
            locationId = UUID.randomUUID().toString(),
            notes = "Batch with audit trail",
            totalWeightKg = "100.0",
            totalAmount = "5000.0",
            itemCount = 5,
            deviceId = "device-a",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = true,
            correctsBatchId = null,
            correctionReason = null,
            voidedAt = voidedAt.toString(),
            voidedByDeviceId = "device-b"  // Voided by different device
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns listOf(voidedBatchFromServer)
        
        // When
        val result = syncService.sync()
        
        // Then: The voided batch should include audit trail fields
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.upsertAll(match { list ->
                list.size == 1 && 
                list[0].isVoided &&
                list[0].voidedAt == voidedAt &&
                list[0].voidedByDeviceId == "device-b"
            })
        }
    }

    @Test
    fun `sync pushes locally voided batch to server`() = runTest {
        // Given: A batch was voided locally and needs to sync
        val batchId = UUID.randomUUID()
        val voidedAt = Instant.now()
        
        val localVoidedBatch = com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity(
            id = batchId,
            localId = "local-voided-batch",
            locationId = UUID.randomUUID(),
            notes = "Locally voided batch",
            totalWeightKg = BigDecimal("100.0"),
            totalAmount = BigDecimal("5000.0"),
            itemCount = 5,
            deviceId = "this-device",
            createdAt = Instant.now(),
            syncedAt = null,  // Not synced yet
            isVoided = true,
            correctsBatchId = null,
            correctionReason = null,
            voidedAt = voidedAt,
            voidedByDeviceId = "this-device"
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { purchaseBatchDao.getUnsynced() } returns listOf(localVoidedBatch)
        coEvery { syncDataSource.pushBatches(any()) } just Runs
        coEvery { purchaseBatchDao.markSynced(any(), any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns emptyList()
        
        // When
        val result = syncService.sync()
        
        // Then: The voided batch should be pushed with voiding fields
        assertTrue(result is SyncResult.Success)
        coVerify { 
            syncDataSource.pushBatches(match { list ->
                list.size == 1 && 
                list[0].isVoided &&
                list[0].voidedAt != null &&
                list[0].voidedByDeviceId == "this-device"
            })
        }
    }

    @Test
    fun `sync upserts correction sale batch from server`() = runTest {
        // Given: A sale batch correction was created on another device
        val originalBatchId = UUID.randomUUID()
        val correctionBatchId = UUID.randomUUID()
        
        val correctionSaleBatchFromServer = SaleBatchDto(
            id = correctionBatchId.toString(),
            localId = "sale-correction-id",
            locationId = UUID.randomUUID().toString(),
            notes = "Corrected sale batch",
            totalWeightKg = "45.0",  // Corrected weight
            totalAmount = "2250.0",
            itemCount = 3,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = false,
            correctsBatchId = originalBatchId.toString(),
            correctionReason = "Невірна ціна"
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullSaleBatches(any()) } returns listOf(correctionSaleBatchFromServer)
        
        // When
        val result = syncService.sync()
        
        // Then: The correction sale batch should be upserted with all metadata
        assertTrue(result is SyncResult.Success)
        coVerify { 
            saleBatchDao.upsertAll(match { list ->
                list.size == 1 && 
                list[0].correctsBatchId == originalBatchId &&
                list[0].correctionReason == "Невірна ціна"
            })
        }
    }

    @Test
    fun `sync pulls both voided original and correction batch together`() = runTest {
        // Given: Server has both original (voided) and correction batch
        val originalBatchId = UUID.randomUUID()
        val correctionBatchId = UUID.randomUUID()
        val locationId = UUID.randomUUID()
        
        val originalVoidedBatch = PurchaseBatchDto(
            id = originalBatchId.toString(),
            localId = "original-batch",
            locationId = locationId.toString(),
            notes = "Original batch",
            totalWeightKg = "100.0",
            totalAmount = "5000.0",
            itemCount = 5,
            deviceId = "device-a",
            createdAt = Instant.now().minusSeconds(3600).toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = true,
            correctsBatchId = null,
            correctionReason = null,
            voidedAt = Instant.now().toString(),
            voidedByDeviceId = "device-b"
        )
        
        val correctionBatch = PurchaseBatchDto(
            id = correctionBatchId.toString(),
            localId = "correction-batch",
            locationId = locationId.toString(),
            notes = "Correction batch",
            totalWeightKg = "95.0",
            totalAmount = "4750.0",
            itemCount = 5,
            deviceId = "device-b",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            isVoided = false,
            correctsBatchId = originalBatchId.toString(),
            correctionReason = "Помилка при зважуванні"
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()
        coEvery { syncDataSource.pullBatches(any()) } returns listOf(originalVoidedBatch, correctionBatch)
        
        // When
        val result = syncService.sync()
        
        // Then: Both batches should be upserted correctly
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.upsertAll(match { list ->
                list.size == 2 &&
                list.any { it.id == originalBatchId && it.isVoided } &&
                list.any { it.id == correctionBatchId && it.correctsBatchId == originalBatchId }
            })
        }
    }

    // ==================== Server-Wins Conflict Resolution Tests ====================

    @Test
    fun `sync skips transaction push when server has newer version`() = runTest {
        // Given: A local transaction with stale data
        val serverUpdatedTime = Instant.now()
        val localUpdatedTime = serverUpdatedTime.minusSeconds(3600) // 1 hour older
        
        val localTransaction = testTransaction.copy(
            serverUpdatedAt = localUpdatedTime
        )
        
        coEvery { transactionDao.getUnsynced() } returns listOf(localTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        
        // Server reports newer timestamp for this record
        coEvery { syncDataSource.getTransactionTimestamps(listOf(localTransaction.localId)) } returns 
            mapOf(localTransaction.localId to serverUpdatedTime.toString())
        
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: Transaction should be skipped (not pushed), but marked as synced
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pushed) // Should be 0 since transaction was skipped
        
        // Should NOT push the stale data
        coVerify(exactly = 0) { syncDataSource.pushTransactions(any()) }
        
        // Should mark as synced so pull phase can update it
        coVerify { transactionDao.markAsSynced(localTransaction.localId, any()) }
    }

    @Test
    fun `sync pushes transaction when local version is newer than server`() = runTest {
        // Given: A local transaction that is newer than server
        val serverUpdatedTime = Instant.now().minusSeconds(3600) // 1 hour ago
        val localUpdatedTime = Instant.now() // Now
        
        val localTransaction = testTransaction.copy(
            serverUpdatedAt = localUpdatedTime
        )
        
        coEvery { transactionDao.getUnsynced() } returns listOf(localTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        
        // Server reports older timestamp for this record
        coEvery { syncDataSource.getTransactionTimestamps(listOf(localTransaction.localId)) } returns 
            mapOf(localTransaction.localId to serverUpdatedTime.toString())
        
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: Transaction should be pushed since local is newer
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)
        
        coVerify { syncDataSource.pushTransactions(match { it.size == 1 }) }
    }

    @Test
    fun `sync pushes new transaction not on server`() = runTest {
        // Given: A new local transaction that doesn't exist on server
        val localTransaction = testTransaction.copy(
            serverUpdatedAt = null // New record, never synced
        )
        
        coEvery { transactionDao.getUnsynced() } returns listOf(localTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        
        // Server has no record for this localId (returns empty map)
        coEvery { syncDataSource.getTransactionTimestamps(listOf(localTransaction.localId)) } returns emptyMap()
        
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: New transaction should be pushed
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)
        
        coVerify { syncDataSource.pushTransactions(match { it.size == 1 }) }
    }

    @Test
    fun `sync skips stale records but pushes new ones in same batch`() = runTest {
        // Given: One stale record (server is newer) and one new record
        val serverUpdatedTime = Instant.now()
        
        val staleTransaction = testTransaction.copy(
            localId = "stale-tx",
            serverUpdatedAt = serverUpdatedTime.minusSeconds(3600) // Older than server
        )
        val newTransaction = testTransaction.copy(
            localId = "new-tx",
            serverUpdatedAt = null // New, never synced
        )
        
        coEvery { transactionDao.getUnsynced() } returns listOf(staleTransaction, newTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        
        // Server has newer version of stale record, no record for new one
        coEvery { syncDataSource.getTransactionTimestamps(any()) } returns 
            mapOf(staleTransaction.localId to serverUpdatedTime.toString())
        
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: Only new transaction should be pushed
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed) // Only the new one
        
        // Push should only include the new transaction
        coVerify { 
            syncDataSource.pushTransactions(match { list -> 
                list.size == 1 && list[0].localId == "new-tx" 
            }) 
        }
        
        // Both should be marked as synced
        coVerify { transactionDao.markAsSynced("stale-tx", any()) }
        coVerify { transactionDao.markAsSynced("new-tx", any()) }
    }

    @Test
    fun `sync proceeds with push when timestamp fetch fails`() = runTest {
        // Given: A local transaction
        val localTransaction = testTransaction.copy()
        
        coEvery { transactionDao.getUnsynced() } returns listOf(localTransaction)
        coEvery { transactionDao.markAsSynced(any(), any()) } just Runs
        
        // Timestamp fetch fails (network error, etc.)
        coEvery { syncDataSource.getTransactionTimestamps(any()) } throws 
            RuntimeException("Network error")
        
        coEvery { syncDataSource.pushTransactions(any()) } just Runs
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: Should proceed with push despite timestamp fetch failure
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)
        
        coVerify { syncDataSource.pushTransactions(match { it.size == 1 }) }
    }

    @Test
    fun `sync skips sale batch when server has newer version`() = runTest {
        // Given: A local sale batch with stale data
        val serverUpdatedTime = Instant.now()
        val localUpdatedTime = serverUpdatedTime.minusSeconds(3600)
        
        val localBatchId = UUID.randomUUID()
        val localBatch = SaleBatchEntity(
            id = localBatchId,
            localId = "sale-batch-1",
            locationId = UUID.randomUUID(),
            notes = "Test batch",
            totalWeightKg = BigDecimal("100.0"),
            totalAmount = BigDecimal("5000.0"),
            itemCount = 5,
            deviceId = "device-1",
            createdAt = Instant.now(),
            syncedAt = null,
            serverUpdatedAt = localUpdatedTime
        )
        
        coEvery { saleBatchDao.getUnsynced() } returns listOf(localBatch)
        coEvery { saleBatchDao.markSynced(any<UUID>(), any()) } just Runs
        
        // Server reports newer timestamp
        coEvery { syncDataSource.getSaleBatchTimestamps(listOf("sale-batch-1")) } returns 
            mapOf("sale-batch-1" to serverUpdatedTime.toString())
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then: Sale batch should be skipped (not pushed)
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        
        // Should NOT push the stale sale batch
        coVerify(exactly = 0) { syncDataSource.pushSaleBatches(any()) }
        
        // Should mark as synced
        coVerify { saleBatchDao.markSynced(localBatchId, any()) }
    }
}
