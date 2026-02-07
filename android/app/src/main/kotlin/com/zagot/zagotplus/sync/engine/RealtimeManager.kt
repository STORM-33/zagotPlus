package com.zagot.zagotplus.sync.engine

import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime subscription manager (spec Section 6).
 *
 * - In CATCHING_UP: subscribes and buffers events (does NOT apply)
 * - In LIVE: applies events immediately (deduplicated)
 * - On disconnect: triggers re-subscribe + safety sync
 */
@Singleton
class RealtimeManager @Inject constructor(
    private val stateMachine: SyncStateMachine,
    private val realtimeBuffer: RealtimeBuffer,
) {

    companion object {
        private const val TAG = "RealtimeManager"
    }

    private var channel: RealtimeChannelContract? = null
    private var collectionJob: Job? = null
    private var scope: CoroutineScope? = null

    /** Callback invoked when a realtime event should be applied to Room in LIVE state. */
    var onLiveEvent: (suspend (RealtimeChangeEvent) -> Unit)? = null

    /**
     * Start listening on the given channel for the given tables.
     */
    fun start(channel: RealtimeChannelContract, tables: List<String>, scope: CoroutineScope) {
        this.channel = channel
        this.scope = scope

        scope.launch {
            channel.subscribe(tables, scope)
            Log.d(TAG, "Subscribed to realtime for tables: $tables")
        }

        // Collect events from the channel
        collectionJob = scope.launch {
            channel.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    /**
     * Stop listening and clean up.
     */
    suspend fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        channel?.unsubscribe()
        channel = null
    }

    private suspend fun handleEvent(event: RealtimeChangeEvent) {
        when (stateMachine.state.value) {
            SyncState.CATCHING_UP -> {
                // Buffer during catch-up — don't apply yet
                realtimeBuffer.add(event)
                stateMachine.onEvent(
                    SyncEvent.RealtimeEventBuffered(event.table, event.record["id"]?.toString())
                )
                Log.d(TAG, "Buffered event: ${event.table}/${event.record["id"]}")
            }

            SyncState.LIVE -> {
                // Apply immediately in LIVE state
                try {
                    onLiveEvent?.invoke(event)
                    stateMachine.onEvent(
                        SyncEvent.RealtimeEventApplied(event.table, event.record["id"]?.toString())
                    )
                    Log.d(TAG, "Applied live event: ${event.table}/${event.record["id"]}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply live event: ${e.message}")
                }
            }

            SyncState.OFFLINE -> {
                // Shouldn't receive events while offline, but discard if we do
                Log.w(TAG, "Received event while OFFLINE, discarding")
            }
        }
    }
}
