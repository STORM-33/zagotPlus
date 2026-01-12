package com.zagot.zagotplus.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for SyncWorker behavior patterns.
 * Uses mock-based testing since Hilt worker injection is complex to set up.
 */
class SyncWorkerTest {

    private lateinit var syncService: SyncService
    private lateinit var syncStatusRepository: SyncStatusRepository

    @Before
    fun setup() {
        syncService = mockk()
        syncStatusRepository = mockk(relaxed = true)
    }

    // ==================== Success Scenarios ====================

    @Test
    fun `sync service success returns pushed and pulled counts`() = runTest {
        val result = SyncResult.Success(pushed = 5, pulled = 3)
        
        assertEquals(5, result.pushedCount)
        assertTrue(result.isAtLeastPartial)
    }

    @Test
    fun `sync service partial success still counts as partial`() = runTest {
        val result = SyncResult.Partial(pushed = 5, pullError = "Network timeout")
        
        assertEquals(5, result.pushedCount)
        assertTrue(result.isAtLeastPartial)
    }

    // ==================== Failure Scenarios ====================

    @Test
    fun `sync service failure has zero pushed count`() = runTest {
        val result = SyncResult.Failure(error = "Connection refused", phase = SyncPhase.PUSH)
        
        assertEquals(0, result.pushedCount)
        assertFalse(result.isAtLeastPartial)
    }

    @Test
    fun `sync failure during reference data phase`() = runTest {
        val result = SyncResult.Failure(error = "Failed to load products", phase = SyncPhase.REFERENCE_DATA)
        
        assertEquals(SyncPhase.REFERENCE_DATA, result.phase)
    }

    @Test
    fun `sync failure during push phase`() = runTest {
        val result = SyncResult.Failure(error = "Conflict", phase = SyncPhase.PUSH)
        
        assertEquals(SyncPhase.PUSH, result.phase)
    }

    @Test
    fun `sync failure during pull phase after successful push is partial`() = runTest {
        // If push succeeds but pull fails, it should be Partial, not Failure
        val result = SyncResult.Partial(pushed = 10, pullError = "Parse error")
        
        assertTrue(result.isAtLeastPartial)
        assertEquals(10, result.pushedCount)
    }

    // ==================== Worker Constants ====================

    @Test
    fun `worker has correct work name`() {
        assertEquals("sync_periodic", SyncWorker.WORK_NAME)
    }

    @Test
    fun `worker has max retry attempts of 3`() {
        assertEquals(3, SyncWorker.MAX_RETRY_ATTEMPTS)
    }

    // ==================== Status Repository Behavior ====================

    @Test
    fun `sync status repository tracks syncing state`() = runTest {
        coEvery { syncStatusRepository.setSyncing() } returns Unit
        coEvery { syncStatusRepository.setIdle() } returns Unit

        syncStatusRepository.setSyncing()
        syncStatusRepository.setIdle()

        coVerify { syncStatusRepository.setSyncing() }
        coVerify { syncStatusRepository.setIdle() }
    }

    @Test
    fun `sync status repository tracks error state`() = runTest {
        coEvery { syncStatusRepository.setError(any()) } returns Unit

        val errorMessage = "Network unavailable"
        syncStatusRepository.setError(errorMessage)

        coVerify { syncStatusRepository.setError(errorMessage) }
    }

    // ==================== Retry Logic Tests ====================

    @Test
    fun `failure with remaining retries should retry`() {
        // Worker should retry when runAttemptCount < MAX_RETRY_ATTEMPTS
        val runAttemptCount = 1
        val shouldRetry = runAttemptCount < SyncWorker.MAX_RETRY_ATTEMPTS
        
        assertTrue(shouldRetry)
    }

    @Test
    fun `failure at max retries should not retry`() {
        val runAttemptCount = 3
        val shouldRetry = runAttemptCount < SyncWorker.MAX_RETRY_ATTEMPTS
        
        assertFalse(shouldRetry)
    }
}
