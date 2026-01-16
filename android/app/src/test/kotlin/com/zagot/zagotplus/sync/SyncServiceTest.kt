package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
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
    private lateinit var saleBatchDao: SaleBatchDao
    private lateinit var locationDao: LocationDao
    private lateinit var productDao: ProductDao
    private lateinit var expenseCategoryDao: ExpenseCategoryDao
    private lateinit var cashOperationDao: CashOperationDao
    private lateinit var syncPreferences: SyncPreferences
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
        
        // Default getAllLocalIds for batch deduplication
        coEvery { transactionDao.getAllLocalIds() } returns emptyList()
        coEvery { purchaseBatchDao.getAllLocalIds() } returns emptyList()
        coEvery { saleBatchDao.getAllLocalIds() } returns emptyList()
        coEvery { expenseCategoryDao.getAllLocalIds() } returns emptyList()
        coEvery { cashOperationDao.getAllLocalIds() } returns emptyList()

        syncService = SyncService(
            syncDataSource = syncDataSource,
            transactionDao = transactionDao,
            purchaseBatchDao = purchaseBatchDao,
            saleBatchDao = saleBatchDao,
            locationDao = locationDao,
            productDao = productDao,
            expenseCategoryDao = expenseCategoryDao,
            cashOperationDao = cashOperationDao,
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
            weightKg = 10.0,
            pricePerKg = 50.0,
            totalAmount = 500.0,
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
            localId = UUID.randomUUID().toString(),
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
            weightKg = 5.0,
            pricePerKg = 50.0,
            totalAmount = 250.0,
            notes = null,
            deviceId = "this-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
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
        coEvery { syncDataSource.pushExpenseCategory(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pushed >= 1)
        coVerify { syncDataSource.pushExpenseCategory(any()) }
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
    fun `sync continues when expense category push fails for individual item`() = runTest {
        // Given: Two categories, first will fail
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

        var callCount = 0
        coEvery { syncDataSource.pushExpenseCategory(any()) } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("First category push failed")
            }
        }

        // When
        val result = syncService.sync()

        // Then - Should succeed with only 1 pushed
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pushed >= 1)
        coVerify(exactly = 1) { expenseCategoryDao.markSynced(any(), any()) }
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
        coEvery { syncDataSource.pushCashOperation(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue(success.pushed >= 1)
        coVerify { syncDataSource.pushCashOperation(any()) }
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
            amount = 500.0,
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
            amount = 1000.0,
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
    fun `sync continues when cash operation push fails for individual item`() = runTest {
        // Given: Two operations, first will fail
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

        var callCount = 0
        coEvery { syncDataSource.pushCashOperation(any()) } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("First cash operation push failed")
            }
        }

        // When
        val result = syncService.sync()

        // Then - Should succeed with only 1 pushed
        assertTrue(result is SyncResult.Success)
        coVerify(exactly = 1) { cashOperationDao.markSynced(any(), any()) }
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
        coEvery { syncDataSource.pushCashOperation(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify { 
            syncDataSource.pushCashOperation(match { 
                it.type == "payment" && it.categoryId == categoryId.toString() 
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
        coEvery { syncDataSource.pushCashOperation(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify { 
            syncDataSource.pushCashOperation(match { 
                it.type == "purchase" && it.batchId == batchId.toString() 
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
            weightKg = 10.0,
            pricePerKg = 50.0,
            totalAmount = 500.0,
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
            weightKg = 10.0,
            pricePerKg = 50.0,
            totalAmount = 500.0,
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
            totalWeightKg = 100.0,
            totalAmount = 5000.0,
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
        coEvery { purchaseBatchDao.insertAll(any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then: The voided batch should be upserted, updating local record
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.insertAll(match { list ->
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
            totalWeightKg = 50.0,
            totalAmount = 3000.0,
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
        coEvery { saleBatchDao.insertAll(any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then: The voided sale batch should be upserted, updating local record
        assertTrue(result is SyncResult.Success)
        coVerify { 
            saleBatchDao.insertAll(match { list ->
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
            totalWeightKg = 95.0,  // Corrected weight
            totalAmount = 4750.0,
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
        coEvery { purchaseBatchDao.insertAll(any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then: The correction batch should be upserted with all correction metadata
        assertTrue(result is SyncResult.Success)
        coVerify { 
            purchaseBatchDao.insertAll(match { list ->
                list.size == 1 && 
                list[0].correctsBatchId == originalBatchId &&
                list[0].correctionReason == "Помилка при зважуванні"
            })
        }
    }
}
