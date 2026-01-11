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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.sync.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProducts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    LaunchedEffect(uiState.copySuccess) {
        if (uiState.copySuccess) {
            snackbarHostState.showSnackbar("ID скопійовано")
            viewModel.dismissCopySuccess()
        }
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
                                SyncStatus.State.SYNCING -> "Синхронізація..."
                                SyncStatus.State.ERROR -> "Помилка"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.syncStatus.state) {
                                SyncStatus.State.ERROR -> MaterialTheme.colorScheme.error
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
                                contentDescription = null,
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
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Синхронізувати зараз")
                    }
                }
            }

            Divider()

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
                                        onClick = { viewModel.selectLocation(location.id) },
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
                                            com.zagot.zagotplus.domain.model.LocationType.MOBILE -> "Мобільний"
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

            Divider()

            // Data section
            SettingsSection(title = "Дані", icon = Icons.Filled.Category) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToProducts() }
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

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
                contentDescription = null,
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
