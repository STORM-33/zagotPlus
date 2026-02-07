package com.zagot.zagotplus.hardware.printer

/**
 * Connection state for printer service.
 */
sealed class PrinterConnectionState {
    data object Disconnected : PrinterConnectionState()
    data object Scanning : PrinterConnectionState()
    data object Connecting : PrinterConnectionState()
    data class Connected(val device: BluetoothDeviceInfo) : PrinterConnectionState()
    data class Reconnecting(val attempt: Int) : PrinterConnectionState()
    data class Error(val error: PrinterError) : PrinterConnectionState()
}
