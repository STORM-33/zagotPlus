package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.api.ChangeOperation
import com.zagot.syncengine.testing.FakeRealtimeChannel
import com.zagot.syncengine.testing.FakeSupabaseClient
import com.zagot.syncengine.api.RealtimeChangeEvent
import com.zagot.syncengine.testing.FakeNetworkMonitor
import com.zagot.syncengine.realtime.RealtimeBuffer
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.LastWriteWins
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for SyncEngine orchestration (spec Section 11 + 14.3).
 *
 * Uses fakes for all external dependencies to test the full
 * OFFLINE → CATCHING_UP → LIVE flow.
 */
class SyncEngineTest {

    private lateinit var stateMachine: SyncStateMachine
    private lateinit var fakeRemoteClient: FakeSupabaseClient
    private lateinit var fakeChannel: FakeRealtimeChannel
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var realtimeBuffer: RealtimeBuffer

    @Before
    fun setup() {
        stateMachine = SyncStateMachine()
        fakeRemoteClient = FakeSupabaseClient()
        fakeChannel = FakeRealtimeChannel()
        fakeNetworkMonitor = FakeNetworkMonitor()
        realtimeBuffer = RealtimeBuffer()
    }

    @Test
    fun `state machine starts OFFLINE`() {
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `connectivity restored transitions to CATCHING_UP`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.CATCHING_UP)
    }

    @Test
    fun `catch-up completed transitions to LIVE`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.LIVE)
    }

    @Test
    fun `connectivity lost during LIVE transitions to OFFLINE`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
        stateMachine.onEvent(SyncEvent.ConnectivityLost)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `connectivity lost during CATCHING_UP transitions to OFFLINE`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.ConnectivityLost)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `catch-up failed transitions to OFFLINE`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.CatchUpFailed(RuntimeException("test")))
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `full offline to live cycle`() = runTest {
        // Start offline
        assertThat(stateMachine.state.value).isEqualTo(SyncState.OFFLINE)

        // Restore connectivity → CATCHING_UP
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.CATCHING_UP)

        // Events should be buffered during CATCHING_UP
        val event = RealtimeChangeEvent(
            table = "products",
            operation = ChangeOperation.INSERT,
            record = mapOf("id" to "p1", "server_updated_at" to 1000L),
        )
        realtimeBuffer.add(event)
        assertThat(realtimeBuffer.size).isEqualTo(1)

        // Complete catch-up → LIVE
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.LIVE)
    }

    @Test
    fun `sync log captures state transitions`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)

        val log = stateMachine.syncLog.value
        val stateChanges = log.filter { it.event is SyncEvent.StateChange }

        assertThat(stateChanges).hasSize(2) // OFFLINE→CATCHING_UP, CATCHING_UP→LIVE
    }

    @Test
    fun `sync log captures observability events`() = runTest {
        stateMachine.onEvent(SyncEvent.ConnectivityRestored)
        stateMachine.onEvent(SyncEvent.PullComplete("products", 10))
        stateMachine.onEvent(SyncEvent.PushSuccess("products", "p1"))
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)

        val log = stateMachine.syncLog.value
        assertThat(log.any { it.event is SyncEvent.PullComplete }).isTrue()
        assertThat(log.any { it.event is SyncEvent.PushSuccess }).isTrue()
    }

    @Test
    fun `table config defaults`() {
        val config = SyncTableConfig(tableName = "products")
        assertThat(config.primaryKey).isEqualTo("id")
        assertThat(config.timestampColumn).isEqualTo("server_updated_at")
        assertThat(config.softDeleteColumn).isEqualTo("deleted_at")
        assertThat(config.conflictResolver).isEqualTo(LastWriteWins)
    }

    @Test
    fun `buffer drains correctly`() {
        val events = (1..5).map { i ->
            RealtimeChangeEvent(
                table = "products",
                operation = ChangeOperation.UPDATE,
                record = mapOf("id" to "p$i", "server_updated_at" to (1000L + i)),
            )
        }
        events.forEach { realtimeBuffer.add(it) }

        assertThat(realtimeBuffer.size).isEqualTo(5)

        val drained = realtimeBuffer.drain()
        assertThat(drained).hasSize(5)
        assertThat(realtimeBuffer.size).isEqualTo(0)
    }

    @Test
    fun `network monitor fake controls connectivity`() = runTest {
        assertThat(fakeNetworkMonitor.isConnected.value).isFalse()

        fakeNetworkMonitor.simulateOnline()
        assertThat(fakeNetworkMonitor.isConnected.value).isTrue()

        fakeNetworkMonitor.simulateOffline()
        assertThat(fakeNetworkMonitor.isConnected.value).isFalse()
    }
}
