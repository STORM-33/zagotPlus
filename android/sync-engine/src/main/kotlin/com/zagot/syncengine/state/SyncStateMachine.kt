package com.zagot.syncengine.state

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core state machine for the sync engine (spec Section 3).
 *
 * Valid transitions:
 * - OFFLINE → CATCHING_UP  (connectivity restored)
 * - CATCHING_UP → LIVE     (catch-up completed)
 * - CATCHING_UP → OFFLINE  (connectivity lost mid-catch-up)
 * - LIVE → OFFLINE          (connectivity lost)
 *
 * Invalid transitions throw [IllegalStateException].
 * All transitions are logged in the sync log.
 */
@Singleton
class SyncStateMachine @Inject constructor() {

    companion object {
        private const val TAG = "SyncStateMachine"
        private const val MAX_LOG_SIZE = 200
    }

    private val _state = MutableStateFlow(SyncState.OFFLINE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _syncLog = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val syncLog: StateFlow<List<SyncLogEntry>> = _syncLog.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    /**
     * Process an event, potentially transitioning to a new state.
     * Thread-safe: Mutex guards the read-compute-write sequence to prevent
     * concurrent coroutines from reading stale state and computing invalid transitions.
     */
    suspend fun onEvent(event: SyncEvent) {
        mutex.withLock {
            val current = _state.value
            val next = resolveTransition(current, event)

            if (next != null && next != current) {
                Log.d(TAG, "Transition: $current → $next (event: $event)")
                _state.value = next
                logEvent(SyncEvent.StateChange(from = current, to = next))
            }

            logEvent(event)
            _events.tryEmit(event)
        }
    }

    /**
     * Resolve the target state for a given current state + event.
     * Returns null if the event does not trigger a transition.
     * Throws on invalid transitions (programmer error).
     */
    private fun resolveTransition(current: SyncState, event: SyncEvent): SyncState? {
        return when (event) {
            is SyncEvent.ConnectivityRestored -> when (current) {
                SyncState.OFFLINE -> SyncState.CATCHING_UP
                SyncState.CATCHING_UP -> null // already catching up, ignore
                SyncState.LIVE -> null // already live, ignore
            }

            is SyncEvent.ConnectivityLost -> when (current) {
                SyncState.OFFLINE -> null // already offline
                SyncState.CATCHING_UP -> SyncState.OFFLINE
                SyncState.LIVE -> SyncState.OFFLINE
            }

            is SyncEvent.CatchUpCompleted -> when (current) {
                SyncState.CATCHING_UP -> SyncState.LIVE
                SyncState.OFFLINE -> throw IllegalStateException(
                    "Cannot complete catch-up while OFFLINE"
                )
                SyncState.LIVE -> null // idempotent, ignore
            }

            is SyncEvent.CatchUpFailed -> when (current) {
                SyncState.CATCHING_UP -> SyncState.OFFLINE
                else -> null
            }

            // Non-transition events (observability only)
            else -> null
        }
    }

    private fun logEvent(event: SyncEvent) {
        val entry = SyncLogEntry(
            event = event,
            table = extractTable(event),
            recordId = extractRecordId(event),
        )
        _syncLog.value = (_syncLog.value + entry).takeLast(MAX_LOG_SIZE)
    }

    private fun extractTable(event: SyncEvent): String? = when (event) {
        is SyncEvent.PushSuccess -> event.table
        is SyncEvent.PushFailed -> event.table
        is SyncEvent.PullComplete -> event.table
        is SyncEvent.RealtimeEventApplied -> event.table
        is SyncEvent.RealtimeEventBuffered -> event.table
        is SyncEvent.RealtimeEventSkipped -> event.table
        else -> null
    }

    private fun extractRecordId(event: SyncEvent): String? = when (event) {
        is SyncEvent.PushSuccess -> event.recordId
        is SyncEvent.RealtimeEventApplied -> event.recordId
        is SyncEvent.RealtimeEventBuffered -> event.recordId
        is SyncEvent.RealtimeEventSkipped -> event.recordId
        else -> null
    }
}
