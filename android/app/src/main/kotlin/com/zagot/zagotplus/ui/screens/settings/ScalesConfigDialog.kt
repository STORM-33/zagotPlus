package com.zagot.zagotplus.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.hardware.scales.ScalesConfig
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState

/**
 * Dialog for configuring scales connection.
 *
 * Network topology:
 * - USR-W610 operates as WiFi AP (SSID: ZAGOT-SCALES)
 * - Tablet connects to W610's WiFi for scales
 * - TCP connection to 10.10.100.254:8899
 */
@Composable
fun ScalesConfigDialog(
    currentConfig: ScalesConfig?,
    connectionState: ScalesConnectionState,
    isTestingConnection: Boolean,
    testResult: String?,
    onSave: (ScalesConfig) -> Unit,
    onTestConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var ipAddress by remember {
        mutableStateOf(currentConfig?.ipAddress ?: DevicePreferences.DEFAULT_SCALES_IP)
    }
    var port by remember {
        mutableStateOf((currentConfig?.port ?: DevicePreferences.DEFAULT_SCALES_PORT).toString())
    }
    var wifiSsid by remember {
        mutableStateOf(currentConfig?.wifiSsid ?: DevicePreferences.DEFAULT_SCALES_WIFI_SSID)
    }
    var autoConnect by remember {
        mutableStateOf(currentConfig?.autoConnect ?: true)
    }

    val isConnected = connectionState is ScalesConnectionState.Connected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Налаштування ваг") },
        text = {
            Column {
                // WiFi info box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "WiFi мережа ваг",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Підключіться до WiFi \"$wifiSsid\" перед тестуванням",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // IP Address field
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP адреса") },
                    placeholder = { Text("10.10.100.254") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Port field
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Порт") },
                    placeholder = { Text("8899") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // WiFi SSID field (informational)
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("WiFi SSID (для довідки)") },
                    placeholder = { Text("ZAGOT-SCALES") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-connect toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Автопідключення",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { autoConnect = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connection status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (connectionState) {
                            is ScalesConnectionState.Connected -> {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Підключено",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is ScalesConnectionState.Connecting,
                            is ScalesConnectionState.Reconnecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Підключення...")
                            }
                            is ScalesConnectionState.Error -> {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = connectionState.error.toDisplayMessage(),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ScalesConnectionState.Disconnected -> {
                                Text(
                                    text = "Відключено",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Test result
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.contains("Підключено") || result.contains("успішн"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test connection button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Відключити")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                // Save config first, then test
                                val portNum = port.toIntOrNull() ?: DevicePreferences.DEFAULT_SCALES_PORT
                                onSave(ScalesConfig(
                                    ipAddress = ipAddress,
                                    port = portNum,
                                    protocol = "auto",
                                    autoConnect = autoConnect,
                                    wifiSsid = wifiSsid
                                ))
                                onTestConnection()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingConnection && ipAddress.isNotBlank()
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Тестувати")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val portNum = port.toIntOrNull() ?: DevicePreferences.DEFAULT_SCALES_PORT
                    onSave(ScalesConfig(
                        ipAddress = ipAddress,
                        port = portNum,
                        protocol = "auto",
                        autoConnect = autoConnect,
                        wifiSsid = wifiSsid
                    ))
                    onDismiss()
                }
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}
