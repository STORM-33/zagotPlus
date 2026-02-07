package com.zagot.zagotplus.sync.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for network monitoring. The sync engine depends on this abstraction
 * to react to connectivity changes with debounce and validation.
 */
interface NetworkMonitor {
    /** Emits true when connectivity is confirmed (after debounce + validation ping). */
    val isConnected: StateFlow<Boolean>
}

/**
 * Controllable network monitor for tests.
 */
class FakeNetworkMonitor : NetworkMonitor {
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun simulateOnline() { _isConnected.value = true }
    fun simulateOffline() { _isConnected.value = false }
}
