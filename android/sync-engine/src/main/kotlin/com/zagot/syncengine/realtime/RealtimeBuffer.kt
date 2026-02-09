package com.zagot.syncengine.realtime

import android.util.Log
import com.zagot.syncengine.api.RealtimeChangeEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeBuffer @Inject constructor() {

    companion object {
        private const val TAG = "RealtimeBuffer"
        const val MAX_BUFFER_SIZE = 1000
    }

    private val lock = Any()
    private val _buffer = ArrayDeque<RealtimeChangeEvent>(MAX_BUFFER_SIZE)
    val buffer: List<RealtimeChangeEvent> get() = synchronized(lock) { _buffer.toList() }

    var maxBufferSize: Int = MAX_BUFFER_SIZE
        set(value) = synchronized(lock) {
            field = value.coerceAtLeast(1)
            while (_buffer.size > field) {
                overflowed = true
                _buffer.removeFirst()
            }
        }

    var overflowed: Boolean = false
        private set

    val size: Int get() = synchronized(lock) { _buffer.size }

    fun add(event: RealtimeChangeEvent) = synchronized(lock) {
        val cap = maxBufferSize
        if (_buffer.size >= cap) {
            if (!overflowed) {
                Log.w(TAG, "Buffer overflow (>$cap events), flagging re-sync")
            }
            overflowed = true
            _buffer.removeFirst()  // Circular: drop oldest, keep newest
        }
        _buffer.addLast(event)
    }

    fun drain(): List<RealtimeChangeEvent> = synchronized(lock) {
        val events = _buffer.toList()
        _buffer.clear()
        events
    }

    fun reset() = synchronized(lock) {
        _buffer.clear()
        overflowed = false
    }
}
