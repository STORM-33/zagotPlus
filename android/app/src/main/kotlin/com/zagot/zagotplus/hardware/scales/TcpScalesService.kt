package com.zagot.zagotplus.hardware.scales

import android.util.Log
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.hardware.scales.protocol.DniprovesyProtocol
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
        private const val RECONNECT_DELAY_INITIAL_MS = 2000L
        private const val RECONNECT_DELAY_MAX_MS = 30000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
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

    private val _debugLog = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    override val debugLog: SharedFlow<String> = _debugLog.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    // Protocol detection - try Dniprovesy first (actual hardware), then Mettler Toledo, then Generic ASCII
    private val protocols: List<ScalesProtocol> = listOf(
        DniprovesyProtocol(),
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
            _connectionState.value is ScalesConnectionState.Connecting ||
            _connectionState.value is ScalesConnectionState.Reconnecting) {
            Log.d(TAG, "Already connected, connecting, or reconnecting — skipping")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            connectWithRetry()
        }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        var currentDelay = RECONNECT_DELAY_INITIAL_MS

        while (currentCoroutineContext().isActive) {
            attempt++

            if (attempt > 1) {
                _connectionState.value = ScalesConnectionState.Reconnecting(attempt)
                Log.d(TAG, "Reconnecting in ${currentDelay}ms (attempt $attempt)")
                delay(currentDelay)
                // Exponential backoff with cap
                currentDelay = (currentDelay * RECONNECT_BACKOFF_MULTIPLIER).toLong()
                    .coerceAtMost(RECONNECT_DELAY_MAX_MS)
            } else {
                _connectionState.value = ScalesConnectionState.Connecting
            }

            try {
                if (establishConnection()) {
                    // Reset backoff on successful connection
                    attempt = 0
                    currentDelay = RECONNECT_DELAY_INITIAL_MS
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
    }

    private suspend fun establishConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cfg = config
            Log.d(TAG, "Connecting to ${cfg.ipAddress}:${cfg.port}")
            _debugLog.emit("[CONN] Connecting to ${cfg.ipAddress}:${cfg.port}...")

            socket = Socket().apply {
                soTimeout = READ_TIMEOUT_MS
                connect(InetSocketAddress(cfg.ipAddress, cfg.port), CONNECT_TIMEOUT_MS)
            }

            outputStream = socket?.getOutputStream()

            _connectionState.value = ScalesConnectionState.Connected("${cfg.ipAddress}:${cfg.port}")
            _debugLog.emit("[CONN] Connected successfully")
            Log.d(TAG, "Connected successfully")

            // Send initial weight request if protocol supports it
            sendWeightRequest()

            true
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout", e)
            _debugLog.emit("[ERR] Connection timeout")
            _errors.emit(ScalesError.Timeout)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            _debugLog.emit("[ERR] Connection failed: ${e.message}")
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
                        val hex = data.joinToString(" ") { "%02X".format(it) }
                        val ascii = data.map { b ->
                            val c = b.toInt().toChar()
                            if (c.isISOControl()) '.' else c
                        }.joinToString("")
                        _debugLog.emit("[RX] HEX: $hex")
                        _debugLog.emit("[RX] ASC: $ascii")

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
                _debugLog.emit("[ERR] Read error: ${e.message}")
                _connectionState.value = ScalesConnectionState.Error(ScalesError.ConnectionLost)
                _errors.emit(ScalesError.ConnectionLost)
            }
        }
    }

    private suspend fun processBuffer(buffer: StringBuilder) {
        // Strategy 1: Look for newline-terminated messages (\r\n or \n)
        while (true) {
            val newlineIndex = buffer.indexOfAny(charArrayOf('\n', '\r'))
            if (newlineIndex == -1) break

            var endIndex = newlineIndex
            if (endIndex + 1 < buffer.length && buffer[endIndex] == '\r' && buffer[endIndex + 1] == '\n') {
                endIndex++
            }

            val message = buffer.substring(0, newlineIndex).trim()
            buffer.delete(0, endIndex + 1)

            if (message.isNotEmpty()) {
                parseAndEmitWeight(message)
            }
        }

        // Strategy 2: Handle '='-delimited messages (Dniprovesy protocol - no newlines)
        // Split on '=' as message start marker
        while (buffer.length > 1) {
            val firstEq = buffer.indexOf('=')
            if (firstEq == -1) break

            // Discard garbage before first '='
            if (firstEq > 0) {
                buffer.delete(0, firstEq)
            }

            // Look for next '=' which marks the start of the following message
            val nextEq = buffer.indexOf('=', 1)
            if (nextEq == -1) {
                // No next '=' yet - could be incomplete message, wait for more data
                // But if buffer is long enough for a complete Dniprovesy message (8 bytes), process it
                if (buffer.length >= 8) {
                    val message = buffer.substring(0, 8)
                    buffer.delete(0, 8)
                    parseAndEmitWeight(message)
                } else {
                    break // Wait for more data
                }
            } else {
                // Extract message between two '=' markers
                val message = buffer.substring(0, nextEq)
                buffer.delete(0, nextEq)
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
                _debugLog.emit("[PARSE] OK (${protocol.name}): ${reading.weightKg} kg, stable=${reading.isStable}")
                _weightReadings.emit(reading)
                return
            }
        }

        // No protocol matched - log for debugging
        val hex = data.joinToString(" ") { "%02X".format(it) }
        _debugLog.emit("[PARSE] FAIL: \"$rawMessage\" | HEX: $hex")
        Log.w(TAG, "Could not parse weight from: $rawMessage")
    }

    private suspend fun sendWeightRequest() {
        try {
            val command = activeProtocol.buildRequestCommand() ?: return
            val hex = command.joinToString(" ") { "%02X".format(it) }
            _debugLog.emit("[TX] HEX: $hex")
            outputStream?.write(command)
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send weight request", e)
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        _debugLog.emit("[CONN] Disconnecting...")
        connectionJob?.cancel()
        connectionJob = null
        closeSocket()
        _connectionState.value = ScalesConnectionState.Disconnected
        _debugLog.emit("[CONN] Disconnected")
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

    override suspend fun sendRawCommand(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = command.toByteArray(Charsets.US_ASCII)
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            _debugLog.emit("[TX-RAW] \"$command\" | HEX: $hex")
            outputStream?.write(bytes)
            outputStream?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            _debugLog.emit("[ERR] Send failed: ${e.message}")
            Result.failure(e)
        }
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
