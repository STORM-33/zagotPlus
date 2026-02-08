package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.state.SyncStateMachine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for SyncStateMachine transitions (spec Section 14.3 — State Machine).
 */
class SyncStateMachineTest {

    private lateinit var sm: SyncStateMachine

    @Before
    fun setup() {
        sm = SyncStateMachine()
    }

    // === Happy path ===

    @Test
    fun `initial state is OFFLINE`() {
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `OFFLINE to CATCHING_UP on connectivity restored`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
    }

    @Test
    fun `CATCHING_UP to LIVE on catch-up completed`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)
    }

    @Test
    fun `full happy path OFFLINE to CATCHING_UP to LIVE`() = runTest {
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)
    }

    // === Connectivity loss ===

    @Test
    fun `LIVE to OFFLINE on connectivity lost`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `CATCHING_UP to OFFLINE on connectivity lost`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `CATCHING_UP to OFFLINE on catch-up failed`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpFailed(RuntimeException("test")))
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    // === Idempotent / no-op ===

    @Test
    fun `connectivity lost while already OFFLINE is no-op`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)
    }

    @Test
    fun `connectivity restored while CATCHING_UP is no-op`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
        sm.onEvent(SyncEvent.ConnectivityRestored) // duplicate
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
    }

    @Test
    fun `connectivity restored while LIVE is no-op`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        sm.onEvent(SyncEvent.ConnectivityRestored) // duplicate
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)
    }

    @Test
    fun `catch-up completed while LIVE is no-op`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        sm.onEvent(SyncEvent.CatchUpCompleted) // duplicate
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)
    }

    // === Invalid transitions ===

    @Test(expected = IllegalStateException::class)
    fun `catch-up completed while OFFLINE throws`() = runTest {
        sm.onEvent(SyncEvent.CatchUpCompleted)
    }

    // === Full reconnect cycle ===

    @Test
    fun `full offline to live to offline to live cycle`() = runTest {
        // Start offline
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)

        // Go online
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)

        // Go offline
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)

        // Come back
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)
        sm.onEvent(SyncEvent.CatchUpCompleted)
        assertThat(sm.state.value).isEqualTo(SyncState.LIVE)
    }

    // === Sync log ===

    @Test
    fun `state transitions are logged`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.CatchUpCompleted)

        val log = sm.syncLog.value
        // Should have: StateChange(OFFLINE→CATCHING_UP), ConnectivityRestored,
        //              StateChange(CATCHING_UP→LIVE), CatchUpCompleted
        val stateChanges = log.filter { it.event is SyncEvent.StateChange }
        assertThat(stateChanges).hasSize(2)

        val first = stateChanges[0].event as SyncEvent.StateChange
        assertThat(first.from).isEqualTo(SyncState.OFFLINE)
        assertThat(first.to).isEqualTo(SyncState.CATCHING_UP)

        val second = stateChanges[1].event as SyncEvent.StateChange
        assertThat(second.from).isEqualTo(SyncState.CATCHING_UP)
        assertThat(second.to).isEqualTo(SyncState.LIVE)
    }

    @Test
    fun `observability events are logged without state transitions`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        sm.onEvent(SyncEvent.PullComplete(table = "products", count = 5))

        val pullLogs = sm.syncLog.value.filter { it.event is SyncEvent.PullComplete }
        assertThat(pullLogs).hasSize(1)
        assertThat(pullLogs[0].table).isEqualTo("products")
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP) // state unchanged
    }

    // === Interrupted catch-up (spec: app killed during CATCHING_UP) ===

    @Test
    fun `catch-up interrupted by connectivity loss returns to OFFLINE safely`() = runTest {
        sm.onEvent(SyncEvent.ConnectivityRestored)
        assertThat(sm.state.value).isEqualTo(SyncState.CATCHING_UP)

        // Simulate connectivity loss mid-catch-up
        sm.onEvent(SyncEvent.ConnectivityLost)
        assertThat(sm.state.value).isEqualTo(SyncState.OFFLINE)

        // Must NOT have transitioned to LIVE at any point
        val stateChanges = sm.syncLog.value
            .filter { it.event is SyncEvent.StateChange }
            .map { it.event as SyncEvent.StateChange }
        assertThat(stateChanges.none { it.to == SyncState.LIVE }).isTrue()
    }
}
