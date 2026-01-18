package com.zagot.zagotplus.hardware.printer

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for communicating with thermal receipt printers via Bluetooth.
 */
interface PrinterService {
    /** Current connection state */
    val connectionState: StateFlow<PrinterConnectionState>

    /** Available Bluetooth devices during scan */
    val availableDevices: StateFlow<List<BluetoothDeviceInfo>>

    /** Stream of errors */
    val errors: SharedFlow<PrinterError>

    /** Start scanning for Bluetooth printers */
    suspend fun scan()

    /** Stop scanning */
    fun stopScan()

    /** Connect to printer by MAC address */
    suspend fun connect(address: String)

    /** Disconnect from printer */
    suspend fun disconnect()

    /** Send raw bytes to printer */
    suspend fun print(data: ByteArray): Result<Unit>

    /** Check if printer is configured */
    fun isConfigured(): Boolean

    /** Check if printer is ready to print */
    fun isReady(): Boolean
}
