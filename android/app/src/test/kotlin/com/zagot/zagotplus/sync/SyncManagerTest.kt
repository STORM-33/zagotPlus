package com.zagot.zagotplus.sync

import android.content.Context
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SyncManager.
 * Tests focus on rate limiting logic since WorkManager behavior is integration tested.
 */
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var syncStatusRepository: SyncStatusRepository
    private lateinit var workManager: WorkManager
    private lateinit var syncManager: SyncManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        syncStatusRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)

        // Mock WorkManager.getInstance() to return our mock
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager

        // Default: IDLE state (not error)
        every { syncStatusRepository.currentState } returns SyncStatus.State.IDLE

        syncManager = SyncManager(context, syncStatusRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

    // === triggerManualSync rate limiting tests ===

    @Test
    fun `first triggerManualSync returns true`() {
        val result = syncManager.triggerManualSync()
        
        assertTrue(result)
    }

    @Test
    fun `rapid successive triggerManualSync calls are rate limited`() {
        // First call succeeds
        val first = syncManager.triggerManualSync()
        assertTrue(first)

        // Immediate second call is rate-limited
        val second = syncManager.triggerManualSync()
        assertFalse(second)
    }

    @Test
    fun `triggerManualSync bypasses rate limit when in ERROR state`() {
        every { syncStatusRepository.currentState } returns SyncStatus.State.ERROR

        // First call
        val first = syncManager.triggerManualSync()
        assertTrue(first)

        // Immediate second call - should succeed because ERROR state
        val second = syncManager.triggerManualSync()
        assertTrue(second)
    }

    @Test
    fun `triggerManualSync bypasses rate limit when in WARNING state`() {
        every { syncStatusRepository.currentState } returns SyncStatus.State.WARNING

        val first = syncManager.triggerManualSync()
        assertTrue(first)

        // Should succeed due to WARNING state
        val second = syncManager.triggerManualSync()
        assertTrue(second)
    }

    @Test
    fun `triggerManualSync enqueues work request on success`() {
        syncManager.triggerManualSync()

        verify { workManager.enqueue(any<androidx.work.OneTimeWorkRequest>()) }
    }

    // === initializePeriodicSync tests ===

    @Test
    fun `initializePeriodicSync enqueues unique periodic work`() {
        syncManager.initializePeriodicSync()

        verify { 
            workManager.enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
    }

    // === cancelSync tests ===

    @Test
    fun `cancelSync cancels unique work by name`() {
        syncManager.cancelSync()

        verify { workManager.cancelUniqueWork(SyncWorker.WORK_NAME) }
    }

    // === State-based behavior tests ===

    @Test
    fun `rate limiting applies in IDLE state`() {
        every { syncStatusRepository.currentState } returns SyncStatus.State.IDLE

        syncManager.triggerManualSync()
        val rateLimited = syncManager.triggerManualSync()

        assertFalse(rateLimited)
    }

    @Test
    fun `rate limiting applies in SYNCING state`() {
        every { syncStatusRepository.currentState } returns SyncStatus.State.SYNCING

        syncManager.triggerManualSync()
        val rateLimited = syncManager.triggerManualSync()

        assertFalse(rateLimited)
    }

    @Test
    fun `rate limiting applies in SUCCESS state`() {
        every { syncStatusRepository.currentState } returns SyncStatus.State.SUCCESS

        syncManager.triggerManualSync()
        val rateLimited = syncManager.triggerManualSync()

        assertFalse(rateLimited)
    }
}
