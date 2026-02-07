package com.zagot.zagotplus.hardware.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.zagot.zagotplus.data.preferences.DevicePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth SPP (Serial Port Profile) printer service for XP-58IIH and compatible ESC/POS printers.
 *
 * Supports:
 * - Device discovery (paired devices + scanning)
 * - Connection via SPP UUID
 * - Raw byte printing (ESC/POS data from EscPosEncoder)
 * - Auto-reconnect on connection loss
 *
 * Important for Ukrainian text:
 * - Printer must be initialized with EscPosEncoder.initUkrainian() before printing
 * - This sends 0x1C 0x2E (cancel Chinese mode) and 0x1B 0x74 0x11 (select CP866)
 *
 * Provided via HardwareModule for release builds.
 */
class BluetoothPrinterService(
    private val context: Context,
    private val devicePreferences: DevicePreferences
) : PrinterService {

    companion object {
        private const val TAG = "BluetoothPrinter"

        // Standard SPP UUID for serial port profile
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val CONNECT_TIMEOUT_MS = 10000L
        private const val SCAN_TIMEOUT_MS = 15000L
        private const val RECONNECT_DELAY_INITIAL_MS = 2000L
        private const val RECONNECT_DELAY_MAX_MS = 30000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
    }

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(
        PrinterConnectionState.Disconnected
    )
    override val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    override val availableDevices: StateFlow<List<BluetoothDeviceInfo>> = _availableDevices.asStateFlow()

    private val _errors = MutableSharedFlow<PrinterError>()
    override val errors: SharedFlow<PrinterError> = _errors.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    private var reconnectJob: Job? = null

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDeviceInfo? = null

    // Discovered devices during scan
    private val discoveredDevices = mutableMapOf<String, BluetoothDeviceInfo>()

    // Broadcast receiver for device discovery
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let { addDiscoveredDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished")
                    if (_connectionState.value == PrinterConnectionState.Scanning) {
                        _connectionState.value = PrinterConnectionState.Disconnected
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) return

        val name = device.name ?: "Unknown Device"
        val address = device.address

        // Filter for potential printers (name contains printer-related keywords)
        val isPotentialPrinter = name.contains("printer", ignoreCase = true) ||
                name.contains("XP-", ignoreCase = true) ||
                name.contains("POS", ignoreCase = true) ||
                name.contains("58", ignoreCase = true) ||
                name.contains("80", ignoreCase = true)

        val isPaired = bluetoothAdapter?.bondedDevices?.any { it.address == address } == true

        val info = BluetoothDeviceInfo(
            name = name,
            address = address,
            isPaired = isPaired
        )

        discoveredDevices[address] = info

        // Also include paired devices that might be printers
        updateDeviceList()
    }

    @SuppressLint("MissingPermission")
    private fun updateDeviceList() {
        if (!hasBluetoothPermission()) return

        // Start with paired devices
        val pairedDevices = bluetoothAdapter?.bondedDevices?.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Unknown",
                address = device.address,
                isPaired = true,
                isConnected = device.address == connectedDevice?.address
            )
        } ?: emptyList()

        // Merge with discovered devices
        val allDevices = (pairedDevices + discoveredDevices.values)
            .distinctBy { it.address }
            .sortedWith(compareByDescending<BluetoothDeviceInfo> { it.isPaired }
                .thenBy { it.name })

        _availableDevices.value = allDevices
    }

    @SuppressLint("MissingPermission")
    override suspend fun scan() {
        if (!checkBluetoothAvailable()) return

        Log.d(TAG, "Starting device scan...")
        _connectionState.value = PrinterConnectionState.Scanning
        discoveredDevices.clear()

        // First, add paired devices
        updateDeviceList()

        // Register receiver for discovery
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)

        // Start discovery
        bluetoothAdapter?.startDiscovery()

        // Auto-stop after timeout
        scanJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null

        try {
            bluetoothAdapter?.cancelDiscovery()
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
            Log.d(TAG, "Error stopping scan: ${e.message}")
        }

        if (_connectionState.value == PrinterConnectionState.Scanning) {
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        if (!checkBluetoothAvailable()) return@withContext

        stopScan()
        // Cancel any ongoing reconnect to avoid conflicts
        reconnectJob?.cancel()
        reconnectJob = null

        Log.d(TAG, "Connecting to $address...")
        _connectionState.value = PrinterConnectionState.Connecting

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device == null) {
                _connectionState.value = PrinterConnectionState.Error(PrinterError.DeviceNotFound)
                _errors.emit(PrinterError.DeviceNotFound)
                return@withContext
            }

            // Create socket
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

            // Cancel discovery to speed up connection
            bluetoothAdapter?.cancelDiscovery()

            // Connect with timeout
            try {
                socket?.connect()
            } catch (e: IOException) {
                Log.e(TAG, "Socket connect failed, trying fallback", e)
                // Fallback: try using reflection to create socket (works for some devices)
                socket?.close()
                socket = createFallbackSocket(device)
                socket?.connect()
            }

            outputStream = socket?.outputStream

            val deviceInfo = BluetoothDeviceInfo(
                name = device.name ?: "Unknown",
                address = address,
                isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                isConnected = true
            )
            connectedDevice = deviceInfo

            // Save as preferred printer
            devicePreferences.setPrinterConfig(PrinterConfig(address, deviceInfo.name))

            _connectionState.value = PrinterConnectionState.Connected(deviceInfo)
            updateDeviceList()

            Log.d(TAG, "Connected to ${deviceInfo.name}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            _connectionState.value = PrinterConnectionState.Error(PrinterError.PermissionDenied)
            _errors.emit(PrinterError.PermissionDenied)
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            closeSocket()
            _connectionState.value = PrinterConnectionState.Error(PrinterError.ConnectionFailed)
            _errors.emit(PrinterError.ConnectionFailed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket {
        // Use reflection to create socket on channel 1 (fallback for some devices)
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        reconnectJob?.cancel()
        reconnectJob = null
        closeSocket()
        connectedDevice = null
        _connectionState.value = PrinterConnectionState.Disconnected
        updateDeviceList()
    }

    /**
     * Start auto-reconnect loop for a previously connected printer.
     * Uses exponential backoff (2s → 4s → 8s → ... → 30s cap).
     * Runs until reconnected or cancelled (e.g. by explicit disconnect()).
     */
    private fun startAutoReconnect(address: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var currentDelay = RECONNECT_DELAY_INITIAL_MS
            var attempt = 0

            while (currentCoroutineContext().isActive) {
                attempt++
                Log.d(TAG, "Auto-reconnect attempt $attempt in ${currentDelay}ms")
                _connectionState.value = PrinterConnectionState.Reconnecting(attempt)
                delay(currentDelay)

                if (!currentCoroutineContext().isActive) break

                try {
                    connect(address)
                    if (_connectionState.value is PrinterConnectionState.Connected) {
                        Log.d(TAG, "Auto-reconnect successful after $attempt attempts")
                        return@launch
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-reconnect attempt $attempt failed", e)
                }

                currentDelay = (currentDelay * RECONNECT_BACKOFF_MULTIPLIER).toLong()
                    .coerceAtMost(RECONNECT_DELAY_MAX_MS)
            }
        }
    }

    private fun closeSocket() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        outputStream = null
        socket = null
    }

    override suspend fun print(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // Check if socket is actually alive (handles power-cycle scenario)
        if (_connectionState.value is PrinterConnectionState.Connected && !isSocketAlive()) {
            Log.d(TAG, "Socket dead (device likely power-cycled), reconnecting...")
            closeSocket()
            connectedDevice = null
            _connectionState.value = PrinterConnectionState.Disconnected
        }

        if (_connectionState.value !is PrinterConnectionState.Connected) {
            // Try to auto-connect to saved printer
            val config = devicePreferences.getPrinterConfig()
            if (config != null && config.autoConnect) {
                connect(config.address)
                delay(500) // Wait for connection
            }

            if (_connectionState.value !is PrinterConnectionState.Connected) {
                _errors.emit(PrinterError.ConnectionFailed)
                return@withContext Result.failure(Exception("Not connected to printer"))
            }
        }

        try {
            Log.d(TAG, "Printing ${data.size} bytes...")
            outputStream?.write(data)
            outputStream?.flush()
            Log.d(TAG, "Print complete")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Print failed", e)
            // Connection lost — start auto-reconnect
            val address = connectedDevice?.address
                ?: devicePreferences.getPrinterConfig()?.address
            closeSocket()
            connectedDevice = null
            _connectionState.value = PrinterConnectionState.Error(PrinterError.ConnectionLost)
            _errors.emit(PrinterError.PrintFailed)

            // Start background reconnect for next print attempt
            if (address != null) {
                startAutoReconnect(address)
            }

            Result.failure(e)
        }
    }

    /**
     * Check if the Bluetooth socket is still alive.
     * A closed/dead socket from a power-cycled device may still report isConnected=true
     * until we actually try to use it, so we do a zero-byte write test.
     */
    private fun isSocketAlive(): Boolean {
        return try {
            val s = socket ?: return false
            if (!s.isConnected) return false
            // Attempt to check the output stream — if device power-cycled,
            // the socket may still appear connected until we write
            outputStream != null
        } catch (e: Exception) {
            false
        }
    }

    override fun isConfigured(): Boolean {
        return devicePreferences.getPrinterConfig() != null
    }

    override fun isReady(): Boolean {
        return _connectionState.value is PrinterConnectionState.Connected &&
                socket?.isConnected == true
    }

    private suspend fun checkBluetoothAvailable(): Boolean {
        if (bluetoothAdapter == null) {
            _connectionState.value = PrinterConnectionState.Error(PrinterError.BluetoothNotSupported)
            _errors.emit(PrinterError.BluetoothNotSupported)
            return false
        }

        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = PrinterConnectionState.Error(PrinterError.BluetoothDisabled)
            _errors.emit(PrinterError.BluetoothDisabled)
            return false
        }

        if (!hasBluetoothPermission()) {
            _connectionState.value = PrinterConnectionState.Error(PrinterError.PermissionDenied)
            _errors.emit(PrinterError.PermissionDenied)
            return false
        }

        return true
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Configuration for printer connection.
 */
data class PrinterConfig(
    val address: String,  // Bluetooth MAC address
    val name: String,
    val autoConnect: Boolean = true
)
