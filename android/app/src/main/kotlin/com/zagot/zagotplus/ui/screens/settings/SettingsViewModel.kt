package com.zagot.zagotplus.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.BuildConfig
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val syncStatus: SyncStatus = SyncStatus.idle(),
    val pendingCount: Int = 0,
    val deviceId: String = "",
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null,
    val appVersion: String = "",
    val copySuccess: Boolean = false,
    val isRestrictedMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncManager: SyncManager,
    private val devicePreferences: DevicePreferences,
    private val authPreferences: AuthPreferences,
    private val locationRepository: LocationRepository,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _copySuccess = MutableStateFlow(false)
    private val _selectedLocationId = MutableStateFlow(devicePreferences.getSelectedLocationId())
    private val _isRestrictedMode = MutableStateFlow(authPreferences.isRestrictedMode())

    val uiState: StateFlow<SettingsUiState> = combine(
        syncStatusRepository.syncStatus,
        transactionDao.getUnsyncedCountFlow(),
        locationRepository.getAllLocations(),
        _copySuccess,
        combine(_selectedLocationId, _isRestrictedMode) { loc, restricted -> loc to restricted }
    ) { syncStatus, pendingCount, locations, copySuccess, (selectedLocationId, isRestricted) ->
        SettingsUiState(
            syncStatus = syncStatus,
            pendingCount = pendingCount,
            deviceId = devicePreferences.getDeviceId(),
            locations = locations,
            selectedLocationId = selectedLocationId,
            appVersion = BuildConfig.VERSION_NAME,
            copySuccess = copySuccess,
            isRestrictedMode = isRestricted
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            deviceId = devicePreferences.getDeviceId(),
            selectedLocationId = devicePreferences.getSelectedLocationId(),
            appVersion = BuildConfig.VERSION_NAME,
            isRestrictedMode = authPreferences.isRestrictedMode()
        )
    )

    fun triggerSync() {
        syncManager.triggerManualSync()
    }

    fun selectLocation(locationId: UUID) {
        devicePreferences.setSelectedLocationId(locationId)
        _selectedLocationId.value = locationId
    }

    fun setRestrictedMode(restricted: Boolean) {
        authPreferences.setRestrictedMode(restricted)
        _isRestrictedMode.value = restricted
    }

    fun copyDeviceId() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Device ID", devicePreferences.getDeviceId())
        clipboard.setPrimaryClip(clip)
        _copySuccess.update { true }
    }

    fun dismissCopySuccess() {
        _copySuccess.update { false }
    }
}
