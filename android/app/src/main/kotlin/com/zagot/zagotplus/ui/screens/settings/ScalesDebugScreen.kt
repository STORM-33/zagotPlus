package com.zagot.zagotplus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScalesDebugScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScalesDebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new log entries arrive
    LaunchedEffect(uiState.logEntries.size) {
        if (uiState.logEntries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logEntries.size - 1)
        }
    }

    LaunchedEffect(uiState.copySuccess) {
        if (uiState.copySuccess) {
            snackbarHostState.showSnackbar("Лог скопійовано")
            viewModel.dismissCopySuccess()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Діагностика ваг") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyLogToClipboard() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Копіювати лог")
                    }
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Очистити лог")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection status + controls
            ConnectionControlBar(
                connectionState = uiState.connectionState,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() }
            )

            HorizontalDivider()

            // Log area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 8.dp)
            ) {
                items(uiState.logEntries) { entry ->
                    val color = when {
                        entry.message.startsWith("[RX]") -> MaterialTheme.colorScheme.primary
                        entry.message.startsWith("[TX]") -> MaterialTheme.colorScheme.tertiary
                        entry.message.startsWith("[ERR]") -> MaterialTheme.colorScheme.error
                        entry.message.startsWith("[PARSE] OK") -> MaterialTheme.colorScheme.primary
                        entry.message.startsWith("[PARSE] FAIL") -> MaterialTheme.colorScheme.error
                        entry.message.startsWith("[CONN]") -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "${entry.timestamp} ${entry.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            HorizontalDivider()

            // Command input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.commandInput,
                    onValueChange = { viewModel.updateCommandInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Команда (напр. S, SI, T, Z)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendCommand() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendCommand() },
                    enabled = uiState.commandInput.isNotBlank() &&
                            uiState.connectionState is ScalesConnectionState.Connected
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Надіслати")
                }
            }
        }
    }
}

@Composable
private fun ConnectionControlBar(
    connectionState: ScalesConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (statusText, statusColor) = when (connectionState) {
                is ScalesConnectionState.Connected -> "Підключено (${connectionState.deviceInfo})" to MaterialTheme.colorScheme.primary
                is ScalesConnectionState.Connecting -> "Підключення..." to MaterialTheme.colorScheme.tertiary
                is ScalesConnectionState.Reconnecting -> "Перепідключення (спроба ${connectionState.attempt})..." to MaterialTheme.colorScheme.tertiary
                is ScalesConnectionState.Error -> "Помилка: ${connectionState.error.toDisplayMessage()}" to MaterialTheme.colorScheme.error
                ScalesConnectionState.Disconnected -> "Відключено" to MaterialTheme.colorScheme.onSurfaceVariant
            }

            val indicatorColor = when (connectionState) {
                is ScalesConnectionState.Connected -> MaterialTheme.colorScheme.primary
                is ScalesConnectionState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = indicatorColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }

        // Connect/Disconnect button
        when (connectionState) {
            is ScalesConnectionState.Connected -> {
                OutlinedButton(onClick = onDisconnect) {
                    Text("Відключити")
                }
            }
            is ScalesConnectionState.Connecting,
            is ScalesConnectionState.Reconnecting -> {
                OutlinedButton(onClick = onDisconnect) {
                    Text("Скасувати")
                }
            }
            else -> {
                FilledTonalButton(onClick = onConnect) {
                    Text("Підключити")
                }
            }
        }
    }
}
