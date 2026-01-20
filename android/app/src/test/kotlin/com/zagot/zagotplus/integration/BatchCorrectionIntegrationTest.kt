package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.repository.PurchaseBatchRepositoryImpl
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.sync.SyncManager
import io.mockk.mockk
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
import java.util.UUID

/**
 * Integration tests for batch correction flow.
 * Tests the voided batch pattern: original batch becomes voided,
 * new correction batch links to it.
 * 
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BatchCorrectionIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var purchaseBatchRepository: PurchaseBatchRepositoryImpl
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create real in-memory database
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Mock SyncManager to no-op
        syncManager = mockk(relaxed = true)
        
        // Create real repository with in-memory database
        purchaseBatchRepository = PurchaseBatchRepositoryImpl(
            database = database,
            purchaseBatchDao = database.purchaseBatchDao(),
            transactionDao = database.transactionDao(),
            productDao = database.productDao(),
            syncManager = syncManager,
            devicePreferences = mockk(relaxed = true)
        )
        
        // Insert test location and product
        runTest {
            database.locationDao().insert(
                LocationEntity(
                    id = locationId,
                    name = "Test Location",
                    type = "kiosk",
                    createdAt = testInstant,
                    localId = "loc-$locationId"
                )
            )
            database.productDao().insert(
                ProductEntity(
                    id = productId,
                    localId = "product-local-id",
                    name = "Test Product",
                    defaultBuyPrice = BigDecimal("50.00"),
                    defaultSellPrice = BigDecimal("65.00"),
                    isActive = true,
                    createdAt = testInstant
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Batch Correction Tests ====================

    @Test
    fun `correctBatch voids original and creates new batch with reference`() = runTest {
        // 1. Create original batch
        val originalBatchId = UUID.randomUUID()
        val originalBatch = PurchaseBatch(
            id = originalBatchId,
            localId = "original-local-id",
            locationId = locationId,
            notes = "Original batch",
            totalWeightKg = BigDecimal("100.00"),
            totalAmount = BigDecimal("5000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = null
        )
        val originalTransactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "tx-original",
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("100.00"),
                pricePerKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("5000.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null,
                batchId = originalBatchId
            )
        )
        
        purchaseBatchRepository.createBatchWithTransactions(originalBatch, originalTransactions)
        
        // Verify original batch was created
        val storedOriginal = purchaseBatchRepository.getById(originalBatchId)
        assertNotNull(storedOriginal)
        assertFalse(storedOriginal!!.isVoided)
        
        // 2. Correct the batch
        val correctedBatchId = UUID.randomUUID()
        val correctedBatch = PurchaseBatch(
            id = correctedBatchId,
            localId = "corrected-local-id",
            locationId = locationId,
            notes = "Corrected batch",
            totalWeightKg = BigDecimal("120.00"),
            totalAmount = BigDecimal("6000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val correctedTransactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "tx-corrected",
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("120.00"),
                pricePerKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("6000.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now(),
                syncedAt = null,
                batchId = correctedBatchId
            )
        )
        
        val result = purchaseBatchRepository.correctBatch(
            originalBatchId = originalBatchId,
            correctedBatch = correctedBatch,
            correctedTransactions = correctedTransactions,
            reason = "Wrong weight entered"
        )
        
        // 3. Verify correction result
        assertEquals(originalBatchId, result.correctsBatchId)
        assertEquals("Wrong weight entered", result.correctionReason)
        assertFalse(result.isVoided)
        
        // 4. Verify original batch is now voided
        val voidedOriginal = purchaseBatchRepository.getById(originalBatchId)
        assertNotNull(voidedOriginal)
        assertTrue(voidedOriginal!!.isVoided)
        
        // 5. Verify new correction batch exists with reference
        val correctionBatch = purchaseBatchRepository.getById(correctedBatchId)
        assertNotNull(correctionBatch)
        assertEquals(originalBatchId, correctionBatch!!.correctsBatchId)
        assertEquals("Wrong weight entered", correctionBatch.correctionReason)
        assertFalse(correctionBatch.isVoided)
        
        // 6. Verify corrected transactions were created
        val newTransactions = purchaseBatchRepository.getTransactionsForBatch(correctedBatchId)
        assertEquals(1, newTransactions.size)
        assertEquals(BigDecimal("120.00"), newTransactions[0].weightKg)
    }

    @Test
    fun `correctBatch fails for already voided batch`() = runTest {
        // 1. Create and void a batch
        val originalBatchId = UUID.randomUUID()
        val originalBatch = PurchaseBatch(
            id = originalBatchId,
            localId = "void-test-local",
            locationId = locationId,
            notes = "To be voided",
            totalWeightKg = BigDecimal("50.00"),
            totalAmount = BigDecimal("2500.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = null
        )
        val originalTransactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "tx-void-test",
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("50.00"),
                pricePerKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("2500.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null,
                batchId = originalBatchId
            )
        )
        
        purchaseBatchRepository.createBatchWithTransactions(originalBatch, originalTransactions)
        
        // First correction
        val firstCorrectedBatch = PurchaseBatch(
            id = UUID.randomUUID(),
            localId = "first-correction",
            locationId = locationId,
            notes = "First correction",
            totalWeightKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("2750.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        
        purchaseBatchRepository.correctBatch(
            originalBatchId = originalBatchId,
            correctedBatch = firstCorrectedBatch,
            correctedTransactions = emptyList(),
            reason = "First correction"
        )
        
        // 2. Try to correct the already voided batch
        val secondCorrectedBatch = PurchaseBatch(
            id = UUID.randomUUID(),
            localId = "second-correction",
            locationId = locationId,
            notes = "Should fail",
            totalWeightKg = BigDecimal("60.00"),
            totalAmount = BigDecimal("3000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        
        try {
            purchaseBatchRepository.correctBatch(
                originalBatchId = originalBatchId,
                correctedBatch = secondCorrectedBatch,
                correctedTransactions = emptyList(),
                reason = "Should fail"
            )
            fail("Expected IllegalStateException for already voided batch")
        } catch (e: IllegalStateException) {
            assertEquals("Неможливо виправити вже анульовану партію", e.message)
        }
    }

    @Test
    fun `voided batches are excluded from inventory calculations`() = runTest {
        // 1. Create original batch with transactions
        val batchId = UUID.randomUUID()
        val batch = PurchaseBatch(
            id = batchId,
            localId = "inventory-test",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("100.00"),
            totalAmount = BigDecimal("5000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = null
        )
        val transactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "tx-inventory",
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("100.00"),
                pricePerKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("5000.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null,
                batchId = batchId
            )
        )
        
        purchaseBatchRepository.createBatchWithTransactions(batch, transactions)
        
        // 2. Check inventory includes the transaction
        val inventoryBefore = database.transactionDao().getInventoryAggregatedFlow().first()
        val productInventoryBefore = inventoryBefore.find { it.productId == productId.toString() }
        assertNotNull(productInventoryBefore)
        assertTrue(productInventoryBefore!!.totalWeightKg.startsWith("100.0"))
        
        // 3. Correct the batch (which voids original)
        val correctedBatchId = UUID.randomUUID()
        val correctedBatch = PurchaseBatch(
            id = correctedBatchId,
            localId = "corrected-inventory",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("80.00"),
            totalAmount = BigDecimal("4000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val correctedTransactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "tx-corrected-inventory",
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("80.00"),
                pricePerKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("4000.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now(),
                syncedAt = null,
                batchId = correctedBatchId
            )
        )
        
        purchaseBatchRepository.correctBatch(
            originalBatchId = batchId,
            correctedBatch = correctedBatch,
            correctedTransactions = correctedTransactions,
            reason = "Weight adjustment"
        )
        
        // 4. Check inventory only includes the corrected transaction (not the voided one)
        val inventoryAfter = database.transactionDao().getInventoryAggregatedFlow().first()
        val productInventoryAfter = inventoryAfter.find { it.productId == productId.toString() }
        assertNotNull(productInventoryAfter)
        // Should be 80kg from the correction, NOT 180kg (100 + 80)
        assertTrue(productInventoryAfter!!.totalWeightKg.startsWith("80.0"))
    }

    @Test
    fun `batch counts exclude voided batches for UI display`() = runTest {
        // 1. Create a batch
        val batchId = UUID.randomUUID()
        val batch = PurchaseBatch(
            id = batchId,
            localId = "count-test",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("100.00"),
            totalAmount = BigDecimal("5000.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = null
        )
        
        purchaseBatchRepository.createBatchWithTransactions(batch, emptyList())
        
        val countBefore = purchaseBatchRepository.getTotalBatchCount()
        assertEquals(1, countBefore)
        
        // 2. Correct the batch
        val correctedBatch = PurchaseBatch(
            id = UUID.randomUUID(),
            localId = "corrected-count",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("90.00"),
            totalAmount = BigDecimal("4500.00"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        
        purchaseBatchRepository.correctBatch(
            originalBatchId = batchId,
            correctedBatch = correctedBatch,
            correctedTransactions = emptyList(),
            reason = "Test"
        )
        
        // 3. Total count should be 1 (only active batch, voided excluded)
        val countAfter = purchaseBatchRepository.getTotalBatchCount()
        assertEquals(1, countAfter)
    }
}
