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
        // Override the default empty getAllLocalIds to include the existing local ID
        coEvery { transactionDao.getAllLocalIds() } returns listOf("existing-local-id")
        coEvery { transactionDao.getByLocalId("existing-local-id") } returns testTransaction

        // When
        val result = syncService.sync()

        // Then - should not insert duplicate
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(0, success.pulled)
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
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
    fun `sync skips expense categories that already exist locally`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns emptyList()

        val remoteCategory = ExpenseCategoryDto(
            id = UUID.randomUUID().toString(),
            localId = "existing-cat-id",
            name = "Пальне",
            isActive = true,
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullExpenseCategories(any()) } returns listOf(remoteCategory)
        coEvery { expenseCategoryDao.getByLocalId("existing-cat-id") } returns testExpenseCategory

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify(exactly = 0) { expenseCategoryDao.insert(any()) }
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
    fun `sync skips cash operations that already exist locally`() = runTest {
        // Given
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
            notes = null,
            deviceId = "this-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString()
        )
        coEvery { syncDataSource.pullCashOperations(any()) } returns listOf(remoteCashOp)
        coEvery { cashOperationDao.getByLocalId("existing-cash-id") } returns testCashOperation

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Success)
        coVerify(exactly = 0) { cashOperationDao.insert(any()) }
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
    fun `sync updates batch_id for existing transaction when server has it but local does not`() = runTest {
        // Given: Transaction exists locally with NULL batch_id, server has the batch_id
        val localId = "tx-existing-local-id"
        val batchId = UUID.randomUUID()
        val saleBatchId = UUID.randomUUID()
        
        val existingLocalTransaction = TransactionEntity(
            id = UUID.randomUUID(),
            localId = localId,
            locationId = UUID.randomUUID(),
            type = "purchase",
            transferLocationId = null,
            productId = UUID.randomUUID(),
            weightKg = BigDecimal("10.00"),
            pricePerKg = BigDecimal("50.00"),
            totalAmount = BigDecimal("500.00"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = Instant.now(),
            batchId = null,  // Local has NULL batch_id (e.g., from migration)
            saleBatchId = null
        )
        
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = localId,
            locationId = existingLocalTransaction.locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = existingLocalTransaction.productId.toString(),
            weightKg = 10.0,
            pricePerKg = 50.0,
            totalAmount = 500.0,
            notes = null,
            deviceId = "other-device",
            createdAt = Instant.now().toString(),
            syncedAt = Instant.now().toString(),
            serverUpdatedAt = Instant.now().toString(),
            batchId = batchId.toString(),  // Server has batch_id
            saleBatchId = saleBatchId.toString()  // Server has sale_batch_id
        )
        
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        coEvery { cashOperationDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs
        coEvery { syncDataSource.pullTransactions(any()) } returns listOf(remoteTransaction)
        // Mark the local ID as existing so it goes through the update path, not insert
        coEvery { transactionDao.getAllLocalIds() } returns listOf(localId)
        coEvery { transactionDao.getByLocalId(localId) } returns existingLocalTransaction
        coEvery { transactionDao.updateBatchIds(any(), any(), any()) } just Runs
        
        // When
        val result = syncService.sync()
        
        // Then
        assertTrue(result is SyncResult.Success)
        // Verify updateBatchIds was called with correct parameters
        coVerify { 
            transactionDao.updateBatchIds(
                localId = localId,
                batchId = batchId,
                saleBatchId = saleBatchId
            )
        }
        // Insert should NOT be called since transaction already exists
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
    }

    @Test
    fun `sync does not update batch_id when local already has it`() = runTest {
        // Given: Transaction exists locally WITH batch_id already
        val localId = "tx-with-batch-id"
        val existingBatchId = UUID.randomUUID()
        
        val existingLocalTransaction = TransactionEntity(
            id = UUID.randomUUID(),
            localId = localId,
            locationId = UUID.randomUUID(),
            type = "purchase",
            transferLocationId = null,
            productId = UUID.randomUUID(),
            weightKg = BigDecimal("10.00"),
            pricePerKg = BigDecimal("50.00"),
            totalAmount = BigDecimal("500.00"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = Instant.now(),
            batchId = existingBatchId,  // Local already has batch_id
            saleBatchId = null
        )
        
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = localId,
            locationId = existingLocalTransaction.locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = existingLocalTransaction.productId.toString(),
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
        // Mark the local ID as existing so it goes through the update path, not insert
        coEvery { transactionDao.getAllLocalIds() } returns listOf(localId)
        coEvery { transactionDao.getByLocalId(localId) } returns existingLocalTransaction
        
        // When
        val result = syncService.sync()
        
        // Then
        assertTrue(result is SyncResult.Success)
        // updateBatchIds should NOT be called since local already has batch_id
        coVerify(exactly = 0) { transactionDao.updateBatchIds(any(), any(), any()) }
        coVerify(exactly = 0) { transactionDao.insertAll(any()) }
    }
}
