package com.zagot.zagotplus.sync.engine

import java.time.Instant

/**
 * Events that drive sync-state transitions and internal sync log.
 */
sealed interface SyncEvent {
    /** Network became available (after debounce + validation ping). */
    data object ConnectivityRestored : SyncEvent

    /** Network lost. */
    data object ConnectivityLost : SyncEvent

    /** Catch-up cycle completed successfully. */
    data object CatchUpCompleted : SyncEvent

    /** Catch-up cycle failed (will retry). */
    data class CatchUpFailed(val error: Throwable) : SyncEvent

    // --- Observability events (do not drive state transitions) ---

    data class PushSuccess(val table: String, val recordId: String? = null) : SyncEvent
    data class PushFailed(val table: String, val error: String) : SyncEvent
    data class PullComplete(val table: String, val count: Int) : SyncEvent
    data class RealtimeEventApplied(val table: String, val recordId: String?) : SyncEvent
    data class RealtimeEventBuffered(val table: String, val recordId: String?) : SyncEvent
    data class RealtimeEventSkipped(val table: String, val recordId: String?) : SyncEvent
    data object SafetySync : SyncEvent
    data class StateChange(val from: SyncState, val to: SyncState) : SyncEvent
}

/**
 * Log entry for sync observability (spec Section 14.6).
 */
data class SyncLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val event: SyncEvent,
    val table: String? = null,
    val recordId: String? = null,
    val details: String? = null,
)
