package com.zagot.zagotplus.hardware.printer

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock printer service for development and testing.
 * Logs print data to Logcat instead of sending to actual printer.
 * 
 * Use in debug builds when real printer is not available.
 */
@Singleton
class MockPrinterService @Inject constructor() : PrinterService {

    companion object {
        private const val TAG = "MockPrinter"
    }

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(
        PrinterConnectionState.Disconnected
    )
    override val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val availableDevices: StateFlow<List<BluetoothDeviceInfo>> = _availableDevices.asStateFlow()

    private val _errors = MutableSharedFlow<PrinterError>()
    override val errors: SharedFlow<PrinterError> = _errors.asSharedFlow()

    // Simulation settings
    var simulatePrintFailure: Boolean = false
    var simulateConnectionFailure: Boolean = false

    // Mock devices for scanning
    private val mockDevices = listOf(
        BluetoothDeviceInfo(
            name = "XP-58IIH (Mock)",
            address = "00:11:22:33:44:55",
            isPaired = true
        ),
        BluetoothDeviceInfo(
            name = "Printer-2 (Mock)",
            address = "AA:BB:CC:DD:EE:FF",
            isPaired = false
        )
    )

    override suspend fun scan() {
        _connectionState.value = PrinterConnectionState.Scanning
        Log.d(TAG, "Scanning for printers...")

        // Simulate discovery delay
        delay(1000)
        _availableDevices.value = mockDevices

        Log.d(TAG, "Found ${mockDevices.size} mock devices")
        _connectionState.value = PrinterConnectionState.Disconnected
    }

    override fun stopScan() {
        Log.d(TAG, "Stopped scanning")
        if (_connectionState.value == PrinterConnectionState.Scanning) {
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    override suspend fun connect(address: String) {
        Log.d(TAG, "Connecting to $address...")
        _connectionState.value = PrinterConnectionState.Connecting

        delay(500) // Simulate connection delay

        if (simulateConnectionFailure) {
            Log.e(TAG, "Simulated connection failure")
            _connectionState.value = PrinterConnectionState.Error(PrinterError.ConnectionFailed)
            _errors.emit(PrinterError.ConnectionFailed)
            return
        }

        val device = mockDevices.find { it.address == address }
            ?: BluetoothDeviceInfo(name = "Unknown", address = address, isPaired = false)

        _connectionState.value = PrinterConnectionState.Connected(device.copy(isConnected = true))
        Log.d(TAG, "Connected to ${device.name}")
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnected")
        _connectionState.value = PrinterConnectionState.Disconnected
    }

    override suspend fun print(data: ByteArray): Result<Unit> {
        Log.d(TAG, "Printing ${data.size} bytes")

        if (simulatePrintFailure) {
            Log.e(TAG, "Simulated print failure")
            _errors.emit(PrinterError.PrintFailed)
            return Result.failure(Exception("Simulated print failure"))
        }

        // Log hex dump for debugging
        val hexDump = data.take(200).joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Print data (hex): $hexDump${if (data.size > 200) "..." else ""}")

        // Try to decode text portions for debugging
        try {
            val textPreview = extractPrintableText(data)
            if (textPreview.isNotBlank()) {
                Log.d(TAG, "Print preview:\n$textPreview")
            }
        } catch (e: Exception) {
            // Ignore decode errors
        }

        delay(100) // Simulate print time

        Log.d(TAG, "Print complete")
        return Result.success(Unit)
    }

    override fun isConfigured(): Boolean = true

    override fun isReady(): Boolean {
        return _connectionState.value is PrinterConnectionState.Connected
    }

    /**
     * Extract printable text from ESC/POS data for debugging.
     * Strips control codes and shows approximate output.
     */
    private fun extractPrintableText(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0

        while (i < data.size) {
            val b = data[i].toInt() and 0xFF

            when {
                // ESC commands (skip next 1-2 bytes depending on command)
                b == 0x1B -> {
                    i++ // Skip ESC
                    if (i < data.size) {
                        val cmd = data[i].toInt() and 0xFF
                        i++ // Skip command byte
                        // Some commands have parameters
                        if (cmd in listOf(0x61, 0x45, 0x2D, 0x21, 0x74, 0x64)) {
                            i++ // Skip parameter
                        }
                    }
                }
                // GS commands
                b == 0x1D -> {
                    i += 2 // Skip GS and command
                    if (i < data.size) i++ // Skip parameter
                }
                // FS commands
                b == 0x1C -> {
                    i++ // Skip FS
                    if (i < data.size) i++ // Skip command
                }
                // Line feed
                b == 0x0A -> {
                    sb.append('\n')
                    i++
                }
                // Printable ASCII
                b in 0x20..0x7E -> {
                    sb.append(b.toChar())
                    i++
                }
                // CP866 Cyrillic (rough approximation)
                b in 0x80..0xFF -> {
                    sb.append('?') // Placeholder for Cyrillic
                    i++
                }
                else -> i++
            }
        }

        return sb.toString()
    }
}
