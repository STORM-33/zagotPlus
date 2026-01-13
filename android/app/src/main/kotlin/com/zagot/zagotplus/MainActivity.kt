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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatusRepository
import com.zagot.zagotplus.ui.components.LocationSelectionDialog
import com.zagot.zagotplus.ui.components.LocationSelectionViewModel
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

    @Inject
    lateinit var devicePreferences: DevicePreferences

    private var isAuthenticated by mutableStateOf(false)
    private var needsLocationSelection by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore auth state from persistent storage (survives process death)
        isAuthenticated = authPreferences.isAuthenticated()
        // Check if location selection is needed (first install or location cleared)
        needsLocationSelection = isAuthenticated && devicePreferences.getSelectedLocationId() == null

        setContent {
            ZagotPlusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        !isAuthenticated -> {
                            PinScreen(
                                onAuthenticated = {
                                    authPreferences.setAuthenticated(true)
                                    isAuthenticated = true
                                    // Check if location needs to be selected after PIN setup
                                    needsLocationSelection = devicePreferences.getSelectedLocationId() == null
                                }
                            )
                        }
                        needsLocationSelection -> {
                            val viewModel: LocationSelectionViewModel = hiltViewModel()
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                            // Show main UI behind the dialog
                            NavGraph(
                                syncStatusFlow = syncStatusRepository.syncStatus,
                                onSyncClick = { syncManager.triggerManualSync() }
                            )

                            // Show blocking location selection dialog
                            if (!uiState.locationSelected) {
                                LocationSelectionDialog(
                                    locations = uiState.locations,
                                    isLoading = uiState.isLoading,
                                    onLocationSelected = { locationId ->
                                        viewModel.selectLocation(locationId)
                                        needsLocationSelection = false
                                    }
                                )
                            }
                        }
                        else -> {
                            NavGraph(
                                syncStatusFlow = syncStatusRepository.syncStatus,
                                onSyncClick = { syncManager.triggerManualSync() }
                            )
                        }
                    }
                }
            }
        }
    }
}
