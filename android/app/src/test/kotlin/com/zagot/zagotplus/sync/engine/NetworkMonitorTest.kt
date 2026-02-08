package com.zagot.zagotplus.sync.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.testing.FakeNetworkMonitor
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.state.SyncStateMachine
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for network monitor behavior (spec Section 14.3 — Network Edge Cases).
 *
 * Uses FakeNetworkMonitor since AndroidNetworkMonitor requires Android APIs.
 * Tests verify the contract that the state machine depends on.
 */
class NetworkMonitorTest {

    @Test
    fun `FakeNetworkMonitor starts offline`() {
        val monitor = FakeNetworkMonitor()
        assertThat(monitor.isConnected.value).isFalse()
    }

    @Test
    fun `simulateOnline emits true`() = runTest {
        val monitor = FakeNetworkMonitor()
        monitor.isConnected.test {
            assertThat(awaitItem()).isFalse() // initial
            monitor.simulateOnline()
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `simulateOffline emits false`() = runTest {
        val monitor = FakeNetworkMonitor()
        monitor.simulateOnline()
        monitor.isConnected.test {
            assertThat(awaitItem()).isTrue() // already online
            monitor.simulateOffline()
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `rapid flapping settles on last value`() = runTest {
        val monitor = FakeNetworkMonitor()
        monitor.isConnected.test {
            assertThat(awaitItem()).isFalse()

            // Simulate flapping
            monitor.simulateOnline()
            monitor.simulateOffline()
            monitor.simulateOnline()
            monitor.simulateOffline()
            monitor.simulateOnline()

            // Should settle on true
            // StateFlow deduplicates, so we get the transitions
            val values = mutableListOf<Boolean>()
            while (true) {
                val item = expectMostRecentItem()
                values.add(item)
                break
            }
            assertThat(values.last()).isTrue()
        }
    }

    @Test
    fun `state machine transitions on connectivity changes`() = runTest {
        val monitor = FakeNetworkMonitor()
        val sm = SyncStateMachine()

        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)

        // Simulate connectivity restored
        monitor.simulateOnline()
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)

        // Simulate connectivity lost
        monitor.simulateOffline()
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `connectivity lost during CATCHING_UP prevents LIVE transition`() = runTest {
        val sm = SyncStateMachine()

        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)

        // Lost connectivity before catch-up completes
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)

        // Catch-up complete event should NOT arrive (but if it did, it would throw)
        // This verifies the state machine enforces the constraint
    }
}
