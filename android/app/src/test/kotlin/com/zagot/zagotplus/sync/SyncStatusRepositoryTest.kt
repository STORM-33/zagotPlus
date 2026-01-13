package com.zagot.zagotplus.sync

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for SyncStatusRepository.
 */
class SyncStatusRepositoryTest {

    private lateinit var syncPreferences: SyncPreferences
    private lateinit var repository: SyncStatusRepository

    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Before
    fun setup() {
        syncPreferences = mockk(relaxed = true)
    }

    @Test
    fun `initial state is IDLE when no previous sync`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        repository = SyncStatusRepository(syncPreferences)
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertNull(status.lastSyncTime)
    }

    @Test
    fun `initial state includes last sync time when available`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns testInstant

        repository = SyncStatusRepository(syncPreferences)
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertEquals(testInstant, status.lastSyncTime)
    }

    @Test
    fun `setSyncing changes state to SYNCING`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        repository = SyncStatusRepository(syncPreferences)
        repository.setSyncing()
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.SYNCING, status.state)
    }

    @Test
    fun `setIdle changes state to IDLE with last sync time`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns testInstant

        repository = SyncStatusRepository(syncPreferences)
        repository.setSyncing()
        repository.setIdle()
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertEquals(testInstant, status.lastSyncTime)
    }

    @Test
    fun `setIdle returns null lastSyncTime when EPOCH`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        repository = SyncStatusRepository(syncPreferences)
        repository.setIdle()
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertNull(status.lastSyncTime)
    }

    @Test
    fun `setError changes state to ERROR with message`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        repository = SyncStatusRepository(syncPreferences)
        repository.setError("Network error")
        val status = repository.syncStatus.first()

        assertEquals(SyncStatus.State.ERROR, status.state)
        assertEquals("Network error", status.errorMessage)
    }

    @Test
    fun `state transitions work correctly`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns testInstant

        repository = SyncStatusRepository(syncPreferences)

        // Initial -> Syncing
        repository.setSyncing()
        assertEquals(SyncStatus.State.SYNCING, repository.syncStatus.first().state)

        // Syncing -> Error
        repository.setError("Failed")
        assertEquals(SyncStatus.State.ERROR, repository.syncStatus.first().state)

        // Error -> Syncing (retry)
        repository.setSyncing()
        assertEquals(SyncStatus.State.SYNCING, repository.syncStatus.first().state)

        // Syncing -> Idle (success)
        repository.setIdle()
        assertEquals(SyncStatus.State.IDLE, repository.syncStatus.first().state)
    }

    @Test
    fun `error message can contain unicode`() = runTest {
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        repository = SyncStatusRepository(syncPreferences)
        repository.setError("Помилка синхронізації")
        val status = repository.syncStatus.first()

        assertEquals("Помилка синхронізації", status.errorMessage)
    }
}
