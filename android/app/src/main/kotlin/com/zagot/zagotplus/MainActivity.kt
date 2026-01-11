package com.zagot.zagotplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatusRepository
import com.zagot.zagotplus.ui.navigation.NavGraph
import com.zagot.zagotplus.ui.theme.ZagotPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var syncStatusRepository: SyncStatusRepository
    
    @Inject
    lateinit var syncManager: SyncManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZagotPlusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        syncStatusFlow = syncStatusRepository.syncStatus,
                        onSyncClick = { syncManager.triggerManualSync() }
                    )
                }
            }
        }
    }
}
