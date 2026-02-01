package com.zagot.zagotplus.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.hardware.printer.PrinterConnectionState
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.ui.components.AdminPinDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProducts: () -> Unit,
    authPreferences: AuthPreferences,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
    
    // State for admin PIN dialogs
    var showAdminPinForMode by remember { mutableStateOf(false) }
    var showAdminPinForLocation by remember { mutableStateOf(false) }
    var showAdminPinForProducts by remember { mutableStateOf(false) }
    var pendingLocationId by remember { mutableStateOf<java.util.UUID?>(null) }
    var pendingModeChange by remember { mutableStateOf<Boolean?>(null) }

    // State for hardware dialogs
    var showScalesConfig by remember { mutableStateOf(false) }
    var showPrinterConfig by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.copySuccess) {
        if (uiState.copySuccess) {
            snackbarHostState.showSnackbar("ID скопійовано")
            viewModel.dismissCopySuccess()
        }
    }

    LaunchedEffect(uiState.exportSuccess) {
        if (uiState.exportSuccess) {
            snackbarHostState.showSnackbar("Базу даних експортовано")
            viewModel.dismissExportSuccess()
        }
    }

    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let { error ->
            snackbarHostState.showSnackbar("Помилка: $error")
            viewModel.dismissExportError()
        }
    }
    
    // Admin PIN dialog for mode change
    if (showAdminPinForMode && pendingModeChange != null) {
        AdminPinDialog(
            authPreferences = authPreferences,
            onSuccess = {
                viewModel.setRestrictedMode(pendingModeChange!!)
                showAdminPinForMode = false
                pendingModeChange = null
            },
            onDismiss = {
                showAdminPinForMode = false
                pendingModeChange = null
            }
        )
    }
    
    // Admin PIN dialog for location change (in restricted mode)
    if (showAdminPinForLocation && pendingLocationId != null) {
        AdminPinDialog(
            authPreferences = authPreferences,
            onSuccess = {
                viewModel.selectLocation(pendingLocationId!!)
                showAdminPinForLocation = false
                pendingLocationId = null
            },
            onDismiss = {
                showAdminPinForLocation = false
                pendingLocationId = null
            }
        )
    }
    
    // Admin PIN dialog for Products access (in restricted mode)
    if (showAdminPinForProducts) {
        AdminPinDialog(
            authPreferences = authPreferences,
            onSuccess = {
                showAdminPinForProducts = false
                onNavigateToProducts()
            },
            onDismiss = {
                showAdminPinForProducts = false
            }
        )
    }

    // Scales configuration dialog
    if (showScalesConfig) {
        ScalesConfigDialog(
            currentConfig = uiState.scalesConfig,
            connectionState = uiState.scalesConnectionState,
            isTestingConnection = uiState.isTestingScales,
            testResult = uiState.hardwareTestResult,
            onSave = { config -> viewModel.saveScalesConfig(config) },
            onTestConnection = { viewModel.testScalesConnection() },
            onDisconnect = { viewModel.disconnectScales() },
            onDismiss = {
                showScalesConfig = false
                viewModel.dismissHardwareTestResult()
            }
        )
    }

    // Printer configuration dialog
    if (showPrinterConfig) {
        PrinterConfigDialog(
            currentConfig = uiState.printerConfig,
            connectionState = uiState.printerConnectionState,
            availableDevices = uiState.availablePrinters,
            isTestingPrint = uiState.isTestingPrinter,
            testResult = uiState.hardwareTestResult,
            onScan = { viewModel.scanForPrinters() },
            onStopScan = { viewModel.stopPrinterScan() },
            onConnect = { address -> viewModel.connectPrinter(address) },
            onDisconnect = { viewModel.disconnectPrinter() },
            onTestPrint = { viewModel.testPrint() },
            onSave = { config -> viewModel.savePrinterConfig(config) },
            onDismiss = {
                showPrinterConfig = false
                viewModel.stopPrinterScan()
                viewModel.dismissHardwareTestResult()
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Налаштування") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Sync section
            SettingsSection(title = "Синхронізація", icon = Icons.Filled.Sync) {
                // Sync status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Статус",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when (uiState.syncStatus.state) {
                                SyncStatus.State.IDLE -> {
                                    if (uiState.pendingCount > 0) {
                                        "Очікує: ${uiState.pendingCount}"
                                    } else {
                                        "Синхронізовано"
                                    }
                                }
                                SyncStatus.State.SUCCESS -> "Синхронізовано"
                                SyncStatus.State.SYNCING -> "Синхронізація..."
                                SyncStatus.State.WARNING -> uiState.syncStatus.warningMessage ?: "Часткова синхронізація"
                                SyncStatus.State.ERROR -> "Помилка"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.syncStatus.state) {
                                SyncStatus.State.ERROR -> MaterialTheme.colorScheme.error
                                SyncStatus.State.WARNING -> MaterialTheme.colorScheme.tertiary
                                SyncStatus.State.SYNCING -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    when (uiState.syncStatus.state) {
                        SyncStatus.State.SYNCING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        SyncStatus.State.ERROR -> {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Помилка синхронізації",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }

                // Last sync time
                uiState.syncStatus.lastSyncTime?.let { lastSync ->
                    val formattedTime = lastSync
                        .atZone(ZoneId.systemDefault())
                        .format(dateTimeFormatter)
                    SettingsItem(
                        title = "Остання синхронізація",
                        subtitle = formattedTime
                    )
                }

                // Error message
                uiState.syncStatus.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Sync button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.triggerSync() },
                        enabled = uiState.syncStatus.state != SyncStatus.State.SYNCING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Синхронізувати",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Синхронізувати зараз")
                    }
                }
            }

            HorizontalDivider()

            // Device section
            SettingsSection(title = "Пристрій", icon = Icons.Filled.Smartphone) {
                // Device ID
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.copyDeviceId() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ID пристрою",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = uiState.deviceId.take(8) + "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Копіювати",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Location selection
                if (uiState.locations.isNotEmpty()) {
                    Text(
                        text = "Точка",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(modifier = Modifier.selectableGroup()) {
                        uiState.locations.forEach { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = uiState.selectedLocationId == location.id,
                                        onClick = {
                                            if (uiState.isRestrictedMode) {
                                                // Require admin PIN in restricted mode
                                                pendingLocationId = location.id
                                                showAdminPinForLocation = true
                                            } else {
                                                viewModel.selectLocation(location.id)
                                            }
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.selectedLocationId == location.id,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = location.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = when (location.type) {
                                            com.zagot.zagotplus.domain.model.LocationType.KIOSK -> "Кіоск"
                                            com.zagot.zagotplus.domain.model.LocationType.MOBILE -> "Склад"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            
            // Security section - Operation Mode
            SettingsSection(title = "Безпека", icon = Icons.Filled.Lock) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Обмежений режим",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (uiState.isRestrictedMode) "Увімкнено" else "Вимкнено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isRestrictedMode,
                        onCheckedChange = { newValue ->
                            // Always require admin PIN to change mode
                            pendingModeChange = newValue
                            showAdminPinForMode = true
                        }
                    )
                }
                Text(
                    text = "В обмеженому режимі приховано прибуток та додаткові функції",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider()

            // Hardware section
            SettingsSection(title = "Обладнання", icon = Icons.Filled.Hardware) {
                // Scales configuration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showScalesConfig = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ваги",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (uiState.scalesConnectionState) {
                                    is ScalesConnectionState.Connected -> "Підключено"
                                    is ScalesConnectionState.Connecting -> "Підключення..."
                                    is ScalesConnectionState.Reconnecting -> "Перепідключення..."
                                    is ScalesConnectionState.Error -> "Помилка"
                                    ScalesConnectionState.Disconnected -> uiState.scalesConfig?.let {
                                        "${it.ipAddress}:${it.port}"
                                    } ?: "Не налаштовано"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (uiState.scalesConnectionState) {
                                    is ScalesConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                    is ScalesConnectionState.Error -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Налаштувати ваги",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Printer configuration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrinterConfig = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Принтер",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (val state = uiState.printerConnectionState) {
                                    is PrinterConnectionState.Connected -> state.device.name
                                    is PrinterConnectionState.Connecting -> "Підключення..."
                                    is PrinterConnectionState.Scanning -> "Пошук..."
                                    is PrinterConnectionState.Error -> "Помилка"
                                    PrinterConnectionState.Disconnected -> uiState.printerConfig?.name ?: "Не налаштовано"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (uiState.printerConnectionState) {
                                    is PrinterConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                    is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Налаштувати принтер",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Data section
            SettingsSection(title = "Дані", icon = Icons.Filled.Category) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (uiState.isRestrictedMode) {
                                showAdminPinForProducts = true
                            } else {
                                onNavigateToProducts()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Товари",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Перейти до товарів",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Export database button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.isExporting) {
                            viewModel.exportDatabase()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Експорт бази даних",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Зберегти дані у текстовий файл",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Експортувати",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // About section
            SettingsSection(title = "Про програму", icon = Icons.Filled.Info) {
                SettingsItem(
                    title = "Версія",
                    subtitle = uiState.appVersion
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
