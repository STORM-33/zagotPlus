package com.zagot.zagotplus.sync

import java.time.Instant

/**
 * Represents the current state of background sync.
 */
data class SyncStatus(
    val state: State,
    val lastSyncTime: Instant?,
    val errorMessage: String?
) {
    enum class State {
        IDLE,      // Not syncing, no errors
        SYNCING,   // Currently syncing
        ERROR      // Last sync failed
    }
    
    companion object {
        fun idle(lastSync: Instant? = null) = SyncStatus(State.IDLE, lastSync, null)
        fun syncing() = SyncStatus(State.SYNCING, null, null)
        fun error(message: String) = SyncStatus(State.ERROR, null, message)
    }
}
