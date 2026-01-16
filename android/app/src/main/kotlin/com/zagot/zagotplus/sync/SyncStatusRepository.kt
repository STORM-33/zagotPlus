package com.zagot.zagotplus.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for tracking and observing sync status.
 * Provides Flow for UI to observe sync state changes.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    private val syncPreferences: SyncPreferences
) {
    private val _syncStatus = MutableStateFlow(
        SyncStatus.idle(syncPreferences.getLastSyncTimestamp().takeIf { it != Instant.EPOCH })
    )
    
    val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()
    
    /** Current sync state for synchronous access (e.g., rate limiting decisions) */
    val currentState: SyncStatus.State
        get() = _syncStatus.value.state
    
    fun setSyncing() {
        _syncStatus.value = SyncStatus.syncing()
    }
    
    fun setIdle() {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        _syncStatus.value = SyncStatus.idle(lastSync.takeIf { it != Instant.EPOCH })
    }
    
    /**
     * Set success state - indicates sync just completed successfully.
     * UI should show checkmark briefly before transitioning to idle cloud.
     */
    fun setSuccess() {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        _syncStatus.value = SyncStatus.success(lastSync)
    }
    
    fun setWarning(message: String) {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        _syncStatus.value = SyncStatus.warning(message, lastSync.takeIf { it != Instant.EPOCH })
    }
    
    fun setError(message: String) {
        _syncStatus.value = SyncStatus.error(message)
    }
}
