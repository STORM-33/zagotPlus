package com.zagot.zagotplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatusRepository
import com.zagot.zagotplus.ui.navigation.NavGraph
import com.zagot.zagotplus.ui.screens.auth.PinScreen
import com.zagot.zagotplus.ui.theme.ZagotPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncStatusRepository: SyncStatusRepository

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var authPreferences: AuthPreferences

    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore auth state from persistent storage (survives process death)
        isAuthenticated = authPreferences.isAuthenticated()

        setContent {
            ZagotPlusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated) {
                        NavGraph(
                            syncStatusFlow = syncStatusRepository.syncStatus,
                            onSyncClick = { syncManager.triggerManualSync() }
                        )
                    } else {
                        PinScreen(
                            onAuthenticated = {
                                authPreferences.setAuthenticated(true)
                                isAuthenticated = true
                            }
                        )
                    }
                }
            }
        }
    }
}
