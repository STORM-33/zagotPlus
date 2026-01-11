package com.zagot.zagotplus.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.zagot.zagotplus.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Composable
fun SyncStatusIcon(
    syncStatusFlow: Flow<SyncStatus>,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syncStatus by syncStatusFlow.collectAsState(initial = SyncStatus.idle())
    
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )
    
    IconButton(onClick = onSyncClick, modifier = modifier) {
        when (syncStatus.state) {
            SyncStatus.State.SYNCING -> {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Синхронізація...",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            SyncStatus.State.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = "Помилка синхронізації",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            SyncStatus.State.IDLE -> {
                if (syncStatus.lastSyncTime != null) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = "Синхронізовано",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = "Не синхронізовано",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
