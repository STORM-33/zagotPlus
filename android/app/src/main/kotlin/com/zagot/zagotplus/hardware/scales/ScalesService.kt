package com.zagot.zagotplus.hardware.scales

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for communicating with weighing scales.
 */
interface ScalesService {
    /** Current connection state */
    val connectionState: StateFlow<ScalesConnectionState>

    /** Stream of weight readings (emits continuously when connected) */
    val weightReadings: SharedFlow<WeightReading>

    /** Stream of errors */
    val errors: SharedFlow<ScalesError>

    /** Attempt to connect to configured scales */
    suspend fun connect()

    /** Disconnect from scales */
    suspend fun disconnect()

    /** Send tare command */
    suspend fun tare(): Result<Unit>

    /** Request single weight reading (for scales that don't auto-send) */
    suspend fun requestWeight(): Result<WeightReading>

    /** Check if scales are configured */
    fun isConfigured(): Boolean
}
