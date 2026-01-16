package com.zagot.zagotplus.sync

import java.time.Instant

/**
 * Represents the current state of background sync.
 */
data class SyncStatus(
    val state: State,
    val lastSyncTime: Instant?,
    val errorMessage: String?,
    val warningMessage: String? = null
) {
    enum class State {
        IDLE,      // Not syncing, no errors
        SYNCING,   // Currently syncing
        SUCCESS,   // Sync just completed successfully (temporary, transitions to IDLE)
        WARNING,   // Last sync partial (push succeeded, pull failed)
        ERROR      // Last sync failed
    }
    
    companion object {
        fun idle(lastSync: Instant? = null) = SyncStatus(State.IDLE, lastSync, null, null)
        fun syncing() = SyncStatus(State.SYNCING, null, null, null)
        fun success(lastSync: Instant) = SyncStatus(State.SUCCESS, lastSync, null, null)
        fun warning(message: String, lastSync: Instant? = null) = SyncStatus(State.WARNING, lastSync, null, message)
        fun error(message: String) = SyncStatus(State.ERROR, null, message, null)
    }
}
