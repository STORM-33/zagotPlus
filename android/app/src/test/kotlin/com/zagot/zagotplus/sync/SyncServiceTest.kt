package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Tests for SyncService.
 *
 * NOTE: These tests are currently ignored because mocking the Supabase client
 * in unit tests causes hanging/timeout issues. The Supabase Kotlin library
 * starts internal coroutines that don't properly complete with MockK.
 *
 * TODO: Options to fix:
 * 1. Extract Supabase calls to an interface and mock that instead
 * 2. Use integration tests with a local Supabase instance
 * 3. Wait for better Supabase test support
 *
 * SyncResultTest provides good coverage of the sync result handling logic.
 */
@Ignore("Supabase mocking causes test hangs - see class comment for details")
class SyncServiceTest {

    private lateinit var supabaseClient: SupabaseClient
    private lateinit var postgrest: Postgrest
    private lateinit var transactionDao: TransactionDao
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
        // Mock the extension property
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")

        supabaseClient = mockk(relaxed = true)
        postgrest = mockk(relaxed = true)
        transactionDao = mockk()
        locationDao = mockk()
        productDao = mockk()
        syncPreferences = mockk()

        every { supabaseClient.postgrest } returns postgrest

        syncService = SyncService(
            supabaseClient = supabaseClient,
            transactionDao = transactionDao,
            locationDao = locationDao,
            productDao = productDao,
            syncPreferences = syncPreferences
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    // ==================== Success Cases ====================

    @Test
    fun `sync returns Success when no pending transactions and pull succeeds`() = runTest {
        // Given: No pending transactions
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        // Mock reference data pull (empty, non-critical)
        setupEmptyReferencePull()
        // Mock transaction pull (empty)
        setupEmptyTransactionPull()

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

        // Mock reference data pull
        setupEmptyReferencePull()
        // Mock successful push
        setupSuccessfulPush()
        // Mock transaction pull (empty - no new remote transactions)
        setupEmptyTransactionPull()

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

        // Mock reference data pull
        setupEmptyReferencePull()
        // Mock successful push
        setupSuccessfulPush()
        // Mock failed transaction pull
        setupFailedTransactionPull("Connection timeout")

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
    fun `sync returns Failure when push fails`() = runTest {
        // Given: Pending transactions but push will fail
        coEvery { transactionDao.getUnsynced() } returns listOf(testTransaction)

        // Mock reference data pull
        setupEmptyReferencePull()
        // Mock failed push
        setupFailedPush("Network unavailable")

        // When
        val result = syncService.sync()

        // Then
        assertTrue(result is SyncResult.Failure)
        val failure = result as SyncResult.Failure
        assertEquals("Network unavailable", failure.error)
        assertEquals(SyncPhase.PUSH, failure.phase)
        assertFalse(failure.isAtLeastPartial)
    }

    // ==================== Reference Data Resilience ====================

    @Test
    fun `sync continues when reference data pull fails`() = runTest {
        // Given
        coEvery { transactionDao.getUnsynced() } returns emptyList()
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH
        every { syncPreferences.setLastSyncTimestamp(any()) } just Runs

        // Mock FAILED reference data pull (should not break sync)
        setupFailedReferencePull()
        // Mock transaction pull
        setupEmptyTransactionPull()

        // When
        val result = syncService.sync()

        // Then - sync should still succeed
        assertTrue(result is SyncResult.Success)
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

        // Mock reference data pull
        setupEmptyReferencePull()
        // Mock push where first fails, second succeeds
        setupPartiallySuccessfulPush()
        // Mock transaction pull
        setupEmptyTransactionPull()

        // When
        val result = syncService.sync()

        // Then - Should be Success but with only 1 pushed (not 2)
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.pushed)

        // Only second transaction should be marked as synced
        coVerify(exactly = 1) { transactionDao.markAsSynced(any(), any()) }
    }

    // ==================== Helper Methods ====================

    private fun setupEmptyReferencePull() {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        val result = mockk<PostgrestResult>(relaxed = true)

        every { postgrest["locations"] } returns queryBuilder
        every { postgrest["products"] } returns queryBuilder
        coEvery { queryBuilder.select(any<io.github.jan.supabase.postgrest.query.Columns>(), any()) } returns result
        coEvery { result.decodeList<Any>() } returns emptyList<Any>()
    }

    private fun setupFailedReferencePull() {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)

        every { postgrest["locations"] } returns queryBuilder
        every { postgrest["products"] } returns queryBuilder
        coEvery { queryBuilder.select(any<io.github.jan.supabase.postgrest.query.Columns>(), any()) } throws RuntimeException("Reference data error")
    }

    private fun setupSuccessfulPush() {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        val result = mockk<PostgrestResult>(relaxed = true)

        every { postgrest["transactions"] } returns queryBuilder
        coEvery { queryBuilder.upsert(any<Any>(), any(), any(), any(), any()) } returns result
    }

    private fun setupFailedPush(errorMessage: String) {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)

        every { postgrest["transactions"] } returns queryBuilder
        coEvery { queryBuilder.upsert(any<Any>(), any(), any(), any(), any()) } throws RuntimeException(errorMessage)
    }

    private fun setupPartiallySuccessfulPush() {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        val result = mockk<PostgrestResult>(relaxed = true)
        var callCount = 0

        every { postgrest["transactions"] } returns queryBuilder
        coEvery { queryBuilder.upsert(any<Any>(), any(), any(), any(), any()) } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("First push failed")
            }
            result
        }
    }

    private fun setupEmptyTransactionPull() {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        val result = mockk<PostgrestResult>(relaxed = true)

        every { postgrest["transactions"] } returns queryBuilder
        coEvery { queryBuilder.select(any<io.github.jan.supabase.postgrest.query.Columns>(), any()) } returns result
        coEvery { result.decodeList<Any>() } returns emptyList<Any>()
    }

    private fun setupFailedTransactionPull(errorMessage: String) {
        val queryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)

        every { postgrest["transactions"] } returns queryBuilder
        coEvery { queryBuilder.select(any<io.github.jan.supabase.postgrest.query.Columns>(), any()) } throws RuntimeException(errorMessage)
    }
}
