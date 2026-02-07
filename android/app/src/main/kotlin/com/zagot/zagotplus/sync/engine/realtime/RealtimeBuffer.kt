package com.zagot.zagotplus.sync.engine.realtime

import android.util.Log
import com.zagot.zagotplus.sync.engine.api.RealtimeChangeEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the realtime event buffer during CATCHING_UP state (spec Section 6).
 *
 * Events received from Realtime while in CATCHING_UP are buffered in-memory
 * and drained after the pull completes. Buffer cap prevents unbounded memory growth.
 */
@Singleton
class RealtimeBuffer @Inject constructor() {

    companion object {
        private const val TAG = "RealtimeBuffer"
        const val MAX_BUFFER_SIZE = 1000
    }

    private val _buffer = mutableListOf<RealtimeChangeEvent>()
    val buffer: List<RealtimeChangeEvent> get() = _buffer.toList()

    /** True if buffer overflowed and a re-sync is needed. */
    var overflowed: Boolean = false
        private set

    val size: Int get() = _buffer.size

    /**
     * Add an event to the buffer. If buffer exceeds MAX_BUFFER_SIZE,
     * discard all events and flag overflow for re-sync.
     */
    fun add(event: RealtimeChangeEvent) {
        if (overflowed) return // already overflowed, discard

        _buffer.add(event)
        if (_buffer.size > MAX_BUFFER_SIZE) {
            Log.w(TAG, "Buffer overflow (>${MAX_BUFFER_SIZE} events), flagging re-sync")
            _buffer.clear()
            overflowed = true
        }
    }

    /**
     * Drain the buffer, returning all events and clearing.
     * Caller must deduplicate against already-pulled data.
     */
    fun drain(): List<RealtimeChangeEvent> {
        val events = _buffer.toList()
        _buffer.clear()
        return events
    }

    /** Reset buffer state (after drain or on transition to a new catch-up). */
    fun reset() {
        _buffer.clear()
        overflowed = false
    }
}
