package com.zagot.syncengine.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for network monitoring. The sync engine depends on this abstraction
 * to react to connectivity changes with debounce and validation.
 */
interface NetworkMonitor {
    /** Emits true when connectivity is confirmed (after debounce + validation ping). */
    val isConnected: StateFlow<Boolean>

    /** Begin monitoring (register callbacks). */
    fun start()

    /** Stop monitoring (unregister callbacks). */
    fun stop()
}

// Test fake (FakeNetworkMonitor) is in testFixtures: com.zagot.syncengine.testing.SyncTestFakes
