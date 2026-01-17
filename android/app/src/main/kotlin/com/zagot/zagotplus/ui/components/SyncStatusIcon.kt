package com.zagot.zagotplus.ui.components

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.zagot.zagotplus.sync.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private const val TAG = "SyncStatusIcon"
private const val DEBUG_LOGGING = false // Set to true for debugging, false for production

@Composable
fun SyncStatusIcon(
    syncStatusFlow: Flow<SyncStatus>,
    isOnline: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syncStatus by syncStatusFlow.collectAsState(initial = SyncStatus.idle())

    // Track if we should show success checkmark (temporary state)
    var showSuccessCheckmark by remember { mutableStateOf(false) }

    // Log connectivity changes (debug only)
    LaunchedEffect(isOnline) {
        if (DEBUG_LOGGING) Log.d(TAG, "isOnline changed: $isOnline")
    }

    // When sync status changes to SUCCESS, show checkmark for 2 seconds then hide
    LaunchedEffect(syncStatus.state) {
        if (DEBUG_LOGGING) Log.d(TAG, "syncStatus changed: ${syncStatus.state}")
        if (syncStatus.state == SyncStatus.State.SUCCESS) {
            showSuccessCheckmark = true
            delay(2000)
            showSuccessCheckmark = false
        }
    }
    
    // Only run infinite animation when actually syncing to avoid wasted frames
    val isSyncing = syncStatus.state == SyncStatus.State.SYNCING
    val rotation = if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_rotation"
        ).value
    } else {
        0f
    }
    
    // When offline, disable the button
    val isEnabled = isOnline && !isSyncing

    // Log only when debug enabled (this was causing excessive logging on every recomposition)
    if (DEBUG_LOGGING) {
        val iconState = when {
            !isOnline -> "OFFLINE"
            isSyncing -> "SYNCING"
            syncStatus.state == SyncStatus.State.ERROR -> "ERROR"
            syncStatus.state == SyncStatus.State.WARNING -> "WARNING"
            showSuccessCheckmark -> "SUCCESS_CHECKMARK"
            else -> "IDLE"
        }
        Log.d(TAG, "Rendering icon: $iconState (isOnline=$isOnline, syncState=${syncStatus.state}, showSuccessCheckmark=$showSuccessCheckmark)")
    }

    IconButton(
        onClick = onSyncClick,
        modifier = modifier,
        enabled = isEnabled
    ) {
        when {
            // Offline state takes priority - show crossed-out grayed cloud
            !isOnline -> {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = "Немає з'єднання",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            syncStatus.state == SyncStatus.State.SYNCING -> {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Синхронізація...",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            syncStatus.state == SyncStatus.State.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = "Помилка синхронізації",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            syncStatus.state == SyncStatus.State.WARNING -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = syncStatus.warningMessage ?: "Часткова синхронізація",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            // SUCCESS state or IDLE with recent success
            showSuccessCheckmark -> {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = "Синхронізовано",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // IDLE state - just show cloud
            else -> {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = if (syncStatus.lastSyncTime != null) "Синхронізовано" else "Не синхронізовано",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
