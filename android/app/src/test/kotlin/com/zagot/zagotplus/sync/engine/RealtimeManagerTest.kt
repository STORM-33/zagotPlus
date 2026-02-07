package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for RealtimeManager (spec Section 14.3 — Realtime + Buffer).
 */
class RealtimeManagerTest {

    private lateinit var manager: RealtimeManager
    private lateinit var stateMachine: SyncStateMachine
    private lateinit var buffer: RealtimeBuffer
    private lateinit var fakeChannel: FakeRealtimeChannel

    @Before
    fun setup() {
        stateMachine = SyncStateMachine()
        buffer = RealtimeBuffer()
        manager = RealtimeManager(stateMachine, buffer)
        fakeChannel = FakeRealtimeChannel()
    }

    @Test
    fun `events buffered during CATCHING_UP`() = runTest {
        // Enter CATCHING_UP
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.CATCHING_UP)

        manager.start(fakeChannel, listOf("products"), this)
        testScheduler.advanceUntilIdle() // let collector register

        // Emit an event
        fakeChannel.emitEvent(makeEvent("products", "p1"))
        testScheduler.advanceUntilIdle()

        assertThat(buffer.size).isEqualTo(1)
        manager.stop()
    }

    @Test
    fun `events applied during LIVE`() = runTest {
        // Enter LIVE
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.LIVE)

        val appliedEvents = mutableListOf<RealtimeChangeEvent>()
        manager.onLiveEvent = { event -> appliedEvents.add(event) }

        manager.start(fakeChannel, listOf("products"), this)
        testScheduler.advanceUntilIdle() // let collector register

        fakeChannel.emitEvent(makeEvent("products", "p1"))
        testScheduler.advanceUntilIdle()

        assertThat(appliedEvents).hasSize(1)
        assertThat(buffer.size).isEqualTo(0) // not buffered
        manager.stop()
    }

    @Test
    fun `events discarded during OFFLINE`() = runTest {
        // Stay OFFLINE
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)

        manager.start(fakeChannel, listOf("products"), this)
        testScheduler.advanceUntilIdle() // let collector register

        fakeChannel.emitEvent(makeEvent("products", "p1"))
        testScheduler.advanceUntilIdle()

        assertThat(buffer.size).isEqualTo(0)
        manager.stop()
    }

    @Test
    fun `buffer overflow detected by manager during CATCHING_UP`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)

        // Pre-fill buffer to near capacity
        repeat(RealtimeBuffer.MAX_BUFFER_SIZE) { i ->
            buffer.add(makeEvent("products", "prefill$i"))
        }
        assertThat(buffer.overflowed).isFalse()

        manager.start(fakeChannel, listOf("products"), this)
        testScheduler.advanceUntilIdle()

        // One more event triggers overflow via manager → buffer.add
        fakeChannel.emitEvent(makeEvent("products", "overflow"))
        testScheduler.advanceUntilIdle()

        assertThat(buffer.overflowed).isTrue()
        manager.stop()
    }

    @Test
    fun `subscribe is called on start`() = runTest {
        manager.start(fakeChannel, listOf("products", "transactions"), this)
        testScheduler.advanceUntilIdle()

        assertThat(fakeChannel.isSubscribed).isTrue()
        manager.stop()
    }

    @Test
    fun `stop unsubscribes`() = runTest {
        manager.start(fakeChannel, listOf("products"), this)
        testScheduler.advanceUntilIdle()
        manager.stop()
        testScheduler.advanceUntilIdle()

        assertThat(fakeChannel.isSubscribed).isFalse()
    }

    private fun makeEvent(table: String, id: String) = RealtimeChangeEvent(
        table = table,
        operation = ChangeOperation.INSERT,
        record = mapOf("id" to id, "updated_at" to System.currentTimeMillis()),
    )
}
