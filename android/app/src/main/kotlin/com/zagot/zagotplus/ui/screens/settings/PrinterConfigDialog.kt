package com.zagot.zagotplus.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zagot.zagotplus.hardware.printer.BluetoothDeviceInfo
import com.zagot.zagotplus.hardware.printer.PrinterConfig
import com.zagot.zagotplus.hardware.printer.PrinterConnectionState
import com.zagot.zagotplus.ui.components.BluetoothPermissions

/**
 * Dialog for configuring Bluetooth printer.
 *
 * Features:
 * - Scan for available Bluetooth devices
 * - Connect to selected printer
 * - Test print functionality
 * - Auto-connect setting
 */
@Composable
fun PrinterConfigDialog(
    currentConfig: PrinterConfig?,
    connectionState: PrinterConnectionState,
    availableDevices: List<BluetoothDeviceInfo>,
    isTestingPrint: Boolean,
    testResult: String?,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTestPrint: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAddress by remember { mutableStateOf(currentConfig?.address ?: "") }
    var selectedName by remember { mutableStateOf(currentConfig?.name ?: "") }
    var autoConnect by remember { mutableStateOf(currentConfig?.autoConnect ?: true) }

    val isScanning = connectionState == PrinterConnectionState.Scanning
    val isConnecting = connectionState == PrinterConnectionState.Connecting
    val isConnected = connectionState is PrinterConnectionState.Connected

    // Permission handling
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(BluetoothPermissions.areGranted(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasBluetoothPermission = allGranted
        if (allGranted) {
            permissionDenied = false
            onScan()
        } else {
            permissionDenied = true
        }
    }

    val requestPermissionsAndScan = {
        if (hasBluetoothPermission) {
            onScan()
        } else {
            permissionLauncher.launch(BluetoothPermissions.getRequired().toTypedArray())
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isScanning) onStopScan()
            onDismiss()
        },
        title = { Text("Налаштування принтера") },
        text = {
            Column {
                // Current connection status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Статус:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (val state = connectionState) {
                            is PrinterConnectionState.Connected -> {
                                Icon(
                                    imageVector = Icons.Filled.BluetoothConnected,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = state.device.name,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PrinterConnectionState.Connecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Підключення...")
                            }
                            PrinterConnectionState.Scanning -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Пошук...")
                            }
                            is PrinterConnectionState.Error -> {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = state.error.toDisplayMessage(),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            PrinterConnectionState.Disconnected -> {
                                Text(
                                    text = "Відключено",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Permission denied warning
                if (permissionDenied) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Потрібен дозвіл Bluetooth для пошуку принтерів",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (isScanning) onStopScan() else requestPermissionsAndScan() },
                        modifier = Modifier.weight(1f),
                        enabled = !isConnecting
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isScanning) "Зупинити" else "Пошук")
                    }

                    if (isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Відключити")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Available devices list
                Text(
                    text = "Доступні пристрої",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (availableDevices.isEmpty()) {
                    Text(
                        text = if (isScanning) "Шукаю пристрої..." else if (permissionDenied) "Надайте дозвіл для пошуку" else "Натисніть \"Пошук\" для пошуку принтерів",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(availableDevices) { device ->
                            val isSelectedOrConnected = when (val state = connectionState) {
                                is PrinterConnectionState.Connected -> state.device.address == device.address
                                else -> selectedAddress == device.address
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = !isConnecting) {
                                        selectedAddress = device.address
                                        selectedName = device.name
                                        if (!isConnected || (connectionState as? PrinterConnectionState.Connected)?.device?.address != device.address) {
                                            onConnect(device.address)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelectedOrConnected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (device.isConnected)
                                                Icons.Filled.BluetoothConnected
                                            else
                                                Icons.Filled.Bluetooth,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (device.isConnected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelectedOrConnected) FontWeight.Medium else FontWeight.Normal
                                            )
                                            Text(
                                                text = buildString {
                                                    append(device.address)
                                                    if (device.isPaired) append(" • Спарено")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (device.isConnected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Підключено",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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

                // Test print button
                if (isConnected) {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onTestPrint,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTestingPrint
                    ) {
                        if (isTestingPrint) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Тестовий друк")
                    }
                }

                // Test result
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.contains("успішн", ignoreCase = true))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedAddress.isNotBlank()) {
                        onSave(PrinterConfig(
                            address = selectedAddress,
                            name = selectedName,
                            autoConnect = autoConnect
                        ))
                    }
                    onDismiss()
                }
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isScanning) onStopScan()
                onDismiss()
            }) {
                Text("Скасувати")
            }
        }
    )
}
