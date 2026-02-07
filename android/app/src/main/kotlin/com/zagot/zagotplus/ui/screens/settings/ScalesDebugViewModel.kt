package com.zagot.zagotplus.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState
import com.zagot.zagotplus.hardware.scales.ScalesService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DebugLogEntry(
    val timestamp: String,
    val message: String
)

data class ScalesDebugUiState(
    val connectionState: ScalesConnectionState = ScalesConnectionState.Disconnected,
    val logEntries: List<DebugLogEntry> = emptyList(),
    val commandInput: String = "",
    val copySuccess: Boolean = false
)

@HiltViewModel
class ScalesDebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scalesService: ScalesService
) : ViewModel() {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val _uiState = MutableStateFlow(ScalesDebugUiState())
    val uiState: StateFlow<ScalesDebugUiState> = _uiState.asStateFlow()

    init {
        // Observe connection state
        viewModelScope.launch {
            scalesService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }

        // Observe debug log
        viewModelScope.launch {
            scalesService.debugLog.collect { message ->
                val entry = DebugLogEntry(
                    timestamp = LocalTime.now().format(timeFormatter),
                    message = message
                )
                _uiState.update { state ->
                    val entries = state.logEntries + entry
                    // Keep last 500 entries to avoid OOM
                    state.copy(logEntries = if (entries.size > 500) entries.takeLast(500) else entries)
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            addLogEntry("[UI] Connect requested")
            scalesService.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            addLogEntry("[UI] Disconnect requested")
            scalesService.disconnect()
        }
    }

    fun updateCommandInput(value: String) {
        _uiState.update { it.copy(commandInput = value) }
    }

    fun sendCommand() {
        val command = _uiState.value.commandInput
        if (command.isBlank()) return

        viewModelScope.launch {
            // Append \r\n if not present
            val toSend = if (command.endsWith("\r\n") || command.endsWith("\n")) {
                command
            } else {
                "$command\r\n"
            }
            scalesService.sendRawCommand(toSend)
            _uiState.update { it.copy(commandInput = "") }
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(logEntries = emptyList()) }
    }

    fun copyLogToClipboard() {
        val entries = _uiState.value.logEntries
        if (entries.isEmpty()) return

        val text = entries.joinToString("\n") { "${it.timestamp} ${it.message}" }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scales Debug Log", text))
        _uiState.update { it.copy(copySuccess = true) }
    }

    fun dismissCopySuccess() {
        _uiState.update { it.copy(copySuccess = false) }
    }

    private fun addLogEntry(message: String) {
        val entry = DebugLogEntry(
            timestamp = LocalTime.now().format(timeFormatter),
            message = message
        )
        _uiState.update { state ->
            state.copy(logEntries = state.logEntries + entry)
        }
    }
}
