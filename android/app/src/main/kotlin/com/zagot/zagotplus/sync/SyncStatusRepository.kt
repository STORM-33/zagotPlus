package com.zagot.zagotplus.sync

import com.zagot.zagotplus.sync.engine.SyncEngine
import com.zagot.zagotplus.sync.engine.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge: maps [SyncState] from the new engine to the UI-facing [SyncStatus].
 * Existing ViewModels observe [syncStatus] unchanged.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    /** Tracks the wall-clock time of the most recent successful catch-up. */
    @Volatile
    private var lastLiveTransitionTime: Instant? = null

    val syncStatus: Flow<SyncStatus> = syncEngine.state.map { state ->
        when (state) {
            SyncState.OFFLINE -> SyncStatus.idle(lastLiveTransitionTime)
            SyncState.CATCHING_UP -> SyncStatus.syncing()
            SyncState.LIVE -> {
                lastLiveTransitionTime = Instant.now()
                SyncStatus.success(lastLiveTransitionTime!!)
            }
        }
    }

    val currentState: SyncStatus.State
        get() = when (syncEngine.state.value) {
            SyncState.OFFLINE -> SyncStatus.State.IDLE
            SyncState.CATCHING_UP -> SyncStatus.State.SYNCING
            SyncState.LIVE -> SyncStatus.State.SUCCESS
        }
}
