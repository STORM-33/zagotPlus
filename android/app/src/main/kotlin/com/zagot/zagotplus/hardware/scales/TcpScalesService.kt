package com.zagot.zagotplus.hardware.scales

import android.util.Log
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.hardware.scales.protocol.GenericAsciiProtocol
import com.zagot.zagotplus.hardware.scales.protocol.MettlerToledoProtocol
import com.zagot.zagotplus.hardware.scales.protocol.ScalesProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * TCP-based scales service for USR-W610 RS232→WiFi converter.
 *
 * Network topology:
 * - USR-W610 runs as WiFi AP (SSID: ZAGOT-SCALES)
 * - Tablet connects to W610's WiFi
 * - TCP connection to 10.10.100.254:8899 for scales data
 * - Mobile data (LTE) used simultaneously for Supabase sync
 *
 * The service:
 * - Maintains persistent TCP connection
 * - Auto-detects protocol (Mettler Toledo, Generic ASCII)
 * - Handles reconnection on connection loss
 * - Parses continuous weight readings
 *
 * Provided via HardwareModule for release builds.
 */
class TcpScalesService(
    private val devicePreferences: DevicePreferences
) : ScalesService {

    companion object {
        private const val TAG = "TcpScalesService"
        private const val DEFAULT_IP = "10.10.100.254"
        private const val DEFAULT_PORT = 8899
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 3000
        private const val RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BUFFER_SIZE = 256
    }

    private val _connectionState = MutableStateFlow<ScalesConnectionState>(
        ScalesConnectionState.Disconnected
    )
    override val connectionState: StateFlow<ScalesConnectionState> = _connectionState.asStateFlow()

    private val _weightReadings = MutableSharedFlow<WeightReading>(replay = 1)
    override val weightReadings: SharedFlow<WeightReading> = _weightReadings.asSharedFlow()

    private val _errors = MutableSharedFlow<ScalesError>()
    override val errors: SharedFlow<ScalesError> = _errors.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    // Protocol detection - try Mettler Toledo first, then fall back to Generic ASCII
    private val protocols: List<ScalesProtocol> = listOf(
        MettlerToledoProtocol(),
        GenericAsciiProtocol()
    )
    private var activeProtocol: ScalesProtocol = protocols.first()

    private val config: ScalesConfig
        get() = devicePreferences.getScalesConfig() ?: ScalesConfig(
            ipAddress = DEFAULT_IP,
            port = DEFAULT_PORT,
            protocol = "auto",
            autoConnect = true
        )

    override suspend fun connect() {
        if (_connectionState.value is ScalesConnectionState.Connected ||
            _connectionState.value is ScalesConnectionState.Connecting) {
            Log.d(TAG, "Already connected or connecting, skipping")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            connectWithRetry()
        }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0

        while (attempt < MAX_RECONNECT_ATTEMPTS && currentCoroutineContext().isActive) {
            attempt++

            if (attempt > 1) {
                _connectionState.value = ScalesConnectionState.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
                delay(RECONNECT_DELAY_MS)
            } else {
                _connectionState.value = ScalesConnectionState.Connecting
            }

            try {
                if (establishConnection()) {
                    readLoop()
                    // If readLoop exits normally (without exception), connection was closed
                    Log.d(TAG, "Read loop exited, will reconnect")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Connection cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connection error (attempt $attempt)", e)
                _errors.emit(ScalesError.NetworkError(e.message ?: "Unknown error"))
            }

            closeSocket()
        }

        // Max attempts reached
        _connectionState.value = ScalesConnectionState.Error(ScalesError.ConnectionFailed)
        _errors.emit(ScalesError.ConnectionFailed)
    }

    private suspend fun establishConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cfg = config
            Log.d(TAG, "Connecting to ${cfg.ipAddress}:${cfg.port}")

            socket = Socket().apply {
                soTimeout = READ_TIMEOUT_MS
                connect(InetSocketAddress(cfg.ipAddress, cfg.port), CONNECT_TIMEOUT_MS)
            }

            outputStream = socket?.getOutputStream()

            _connectionState.value = ScalesConnectionState.Connected("${cfg.ipAddress}:${cfg.port}")
            Log.d(TAG, "Connected successfully")

            // Send initial weight request if protocol supports it
            sendWeightRequest()

            true
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout", e)
            _errors.emit(ScalesError.Timeout)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            _errors.emit(ScalesError.NetworkError(e.message ?: "Connection failed"))
            false
        }
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val inputStream = BufferedInputStream(socket?.getInputStream() ?: return@withContext)
        val buffer = ByteArray(BUFFER_SIZE)
        val messageBuffer = StringBuilder()

        try {
            while (currentCoroutineContext().isActive && socket?.isConnected == true) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.d(TAG, "End of stream reached")
                        break
                    }

                    if (bytesRead > 0) {
                        val data = buffer.copyOfRange(0, bytesRead)
                        val text = data.toString(Charsets.US_ASCII)
                        messageBuffer.append(text)

                        // Process complete messages
                        processBuffer(messageBuffer)
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout is normal if scales don't send continuously
                    // Send weight request to poll for data
                    sendWeightRequest()
                }
            }
        } catch (e: IOException) {
            if (currentCoroutineContext().isActive) {
                Log.e(TAG, "Read error", e)
                _connectionState.value = ScalesConnectionState.Error(ScalesError.ConnectionLost)
                _errors.emit(ScalesError.ConnectionLost)
            }
        }
    }

    private suspend fun processBuffer(buffer: StringBuilder) {
        // Look for complete messages (terminated by \r\n or \n)
        while (true) {
            val newlineIndex = buffer.indexOfAny(charArrayOf('\n', '\r'))
            if (newlineIndex == -1) break

            // Extract message up to newline
            var endIndex = newlineIndex
            // Skip \r\n combination
            if (endIndex + 1 < buffer.length && buffer[endIndex] == '\r' && buffer[endIndex + 1] == '\n') {
                endIndex++
            }

            val message = buffer.substring(0, newlineIndex).trim()
            buffer.delete(0, endIndex + 1)

            if (message.isNotEmpty()) {
                parseAndEmitWeight(message)
            }
        }

        // Prevent buffer overflow
        if (buffer.length > 1024) {
            Log.w(TAG, "Buffer overflow, clearing")
            buffer.clear()
        }
    }

    private suspend fun parseAndEmitWeight(rawMessage: String) {
        val data = rawMessage.toByteArray(Charsets.US_ASCII)

        // Try protocols in order
        for (protocol in protocols) {
            val reading = protocol.parseReading(data)
            if (reading != null) {
                activeProtocol = protocol
                _weightReadings.emit(reading)
                return
            }
        }

        // No protocol matched - log for debugging
        Log.w(TAG, "Could not parse weight from: $rawMessage")
    }

    private suspend fun sendWeightRequest() {
        try {
            val command = activeProtocol.buildRequestCommand() ?: return
            outputStream?.write(command)
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send weight request", e)
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        connectionJob?.cancel()
        connectionJob = null
        closeSocket()
        _connectionState.value = ScalesConnectionState.Disconnected
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

    override suspend fun tare(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val command = activeProtocol.buildTareCommand()
            outputStream?.write(command)
            outputStream?.flush()
            Log.d(TAG, "Tare command sent")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Tare failed", e)
            Result.failure(e)
        }
    }

    override suspend fun requestWeight(): Result<WeightReading> = withContext(Dispatchers.IO) {
        try {
            sendWeightRequest()

            // Wait for response
            val reading = withTimeoutOrNull(READ_TIMEOUT_MS.toLong()) {
                var result: WeightReading? = null
                weightReadings.collect {
                    result = it
                    return@collect
                }
                result
            }

            if (reading != null) {
                Result.success(reading)
            } else {
                Result.failure(Exception("Timeout waiting for weight reading"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isConfigured(): Boolean {
        val cfg = devicePreferences.getScalesConfig()
        return cfg != null && cfg.ipAddress.isNotBlank()
    }
}

/**
 * Configuration for scales connection.
 */
data class ScalesConfig(
    val ipAddress: String = "10.10.100.254",
    val port: Int = 8899,
    val protocol: String = "auto",  // "auto", "mettler", "generic"
    val autoConnect: Boolean = true,
    val wifiSsid: String? = "ZAGOT-SCALES"  // For user reference
)
