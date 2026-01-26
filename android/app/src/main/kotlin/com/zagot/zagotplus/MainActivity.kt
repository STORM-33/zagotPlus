package com.zagot.zagotplus

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.setContent
import java.util.Locale

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.data.connectivity.ConnectivityObserver
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

private const val TAG = "MainActivity"

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

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @Inject
    lateinit var locationRepository: com.zagot.zagotplus.domain.repository.LocationRepository

    private var isAuthenticated by mutableStateOf(false)
    private var needsLocationSelection by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        // Force Ukrainian locale
        val ukrainianLocale = Locale("uk")
        Locale.setDefault(ukrainianLocale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(ukrainianLocale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore auth state from persistent storage (survives process death)
        isAuthenticated = authPreferences.isAuthenticated()
        // Check if location selection is needed (first install or location cleared)
        needsLocationSelection = isAuthenticated && devicePreferences.getSelectedLocationId() == null
        
        // Set FLAG_SECURE initially if not authenticated (prevents screenshots of PIN screen)
        updateSecureFlag()
        
        // Keep screen on while app is in foreground (prevent dimming/sleep)
        setupScreenWakeLock()

        setContent {
            ZagotPlusTheme {
                // Note: isCurrentlyConnected() returns false for VPN to be conservative.
                // The Flow will perform an active check and update the state.
                val initialConnected = connectivityObserver.isCurrentlyConnected()
                Log.d(TAG, "Initial connectivity check (sync): $initialConnected")

                val isOnline by connectivityObserver.isOnline.collectAsStateWithLifecycle(
                    initialValue = initialConnected
                )

                LaunchedEffect(isOnline) {
                    Log.d(TAG, "MainActivity isOnline state changed: $isOnline")
                }
                
                // Observe selected location for header display (full mode only)
                val selectedLocationId by devicePreferences.selectedLocationIdFlow.collectAsStateWithLifecycle()
                val locations by locationRepository.getAllLocations().collectAsStateWithLifecycle(initialValue = emptyList())
                val selectedLocationName = remember(selectedLocationId, locations) {
                    selectedLocationId?.let { id -> locations.find { it.id == id }?.name }
                }

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
                                    // Remove FLAG_SECURE after authentication
                                    updateSecureFlag()
                                }
                            )
                        }
                        needsLocationSelection -> {
                            val viewModel: LocationSelectionViewModel = hiltViewModel()
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                            // Show main UI behind the dialog
                            NavGraph(
                                syncStatusFlow = syncStatusRepository.syncStatus,
                                isOnline = isOnline,
                                onSyncClick = { if (isOnline) syncManager.triggerManualSync() },
                                authPreferences = authPreferences,
                                selectedLocationName = selectedLocationName
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
                                isOnline = isOnline,
                                onSyncClick = { if (isOnline) syncManager.triggerManualSync() },
                                authPreferences = authPreferences,
                                selectedLocationName = selectedLocationName
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Updates FLAG_SECURE based on authentication state.
     * When not authenticated (showing PIN screen), prevents screenshots/screen recording.
     */
    private fun updateSecureFlag() {
        if (!isAuthenticated) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    
    /**
     * Keeps the screen on while the app is in foreground.
     * Uses FLAG_KEEP_SCREEN_ON which is managed by lifecycle observers.
     */
    private fun setupScreenWakeLock() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // Keep screen on when app is in foreground
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            
            override fun onPause(owner: LifecycleOwner) {
                // Allow screen to dim when app goes to background
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        })
    }
}
