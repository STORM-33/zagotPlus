package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Tests for RealtimeBuffer (spec Section 6 + Section 14.3 — Realtime Buffer).
 */
class RealtimeBufferTest {

    private lateinit var buffer: RealtimeBuffer

    @Before
    fun setup() {
        buffer = RealtimeBuffer()
    }

    @Test
    fun `buffer starts empty`() {
        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.overflowed).isFalse()
    }

    @Test
    fun `add event increases size`() {
        buffer.add(makeEvent("products", "p1"))
        assertThat(buffer.size).isEqualTo(1)
    }

    @Test
    fun `drain returns all events and clears buffer`() {
        buffer.add(makeEvent("products", "p1"))
        buffer.add(makeEvent("products", "p2"))
        val events = buffer.drain()
        assertThat(events).hasSize(2)
        assertThat(buffer.size).isEqualTo(0)
    }

    @Test
    fun `buffer overflow discards events and sets flag`() {
        repeat(RealtimeBuffer.MAX_BUFFER_SIZE + 1) { i ->
            buffer.add(makeEvent("products", "p$i"))
        }
        assertThat(buffer.overflowed).isTrue()
        assertThat(buffer.size).isEqualTo(0) // cleared on overflow
    }

    @Test
    fun `events added after overflow are discarded`() {
        repeat(RealtimeBuffer.MAX_BUFFER_SIZE + 1) { i ->
            buffer.add(makeEvent("products", "p$i"))
        }
        buffer.add(makeEvent("products", "extra"))
        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.overflowed).isTrue()
    }

    @Test
    fun `reset clears overflow flag and buffer`() {
        repeat(RealtimeBuffer.MAX_BUFFER_SIZE + 1) { i ->
            buffer.add(makeEvent("products", "p$i"))
        }
        assertThat(buffer.overflowed).isTrue()
        buffer.reset()
        assertThat(buffer.overflowed).isFalse()
        assertThat(buffer.size).isEqualTo(0)
    }

    @Test
    fun `drain after reset returns empty`() {
        buffer.add(makeEvent("products", "p1"))
        buffer.reset()
        assertThat(buffer.drain()).isEmpty()
    }

    private fun makeEvent(table: String, id: String) = RealtimeChangeEvent(
        table = table,
        operation = ChangeOperation.INSERT,
        record = mapOf("id" to id, "updated_at" to System.currentTimeMillis()),
    )
}
