package com.zagot.zagotplus.hardware.scales

/**
 * Connection state for scales service.
 */
sealed class ScalesConnectionState {
    data object Disconnected : ScalesConnectionState()
    data object Connecting : ScalesConnectionState()
    data class Connected(val deviceInfo: String) : ScalesConnectionState()
    data class Reconnecting(val attempt: Int) : ScalesConnectionState()
    data class Error(val error: ScalesError) : ScalesConnectionState()
}
