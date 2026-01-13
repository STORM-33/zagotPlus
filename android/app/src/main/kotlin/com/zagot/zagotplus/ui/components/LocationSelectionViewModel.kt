package com.zagot.zagotplus.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

data class LocationSelectionUiState(
    val locations: List<Location> = emptyList(),
    val isLoading: Boolean = true,
    val locationSelected: Boolean = false
)

@HiltViewModel
class LocationSelectionViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val devicePreferences: DevicePreferences,
    private val syncManager: SyncManager,
    private val syncStatusRepository: SyncStatusRepository
) : ViewModel() {

    private val _locationSelected = MutableStateFlow(false)

    val uiState: StateFlow<LocationSelectionUiState> = combine(
        locationRepository.getAllLocations(),
        syncStatusRepository.syncStatus,
        _locationSelected
    ) { locations, syncStatus, locationSelected ->
        LocationSelectionUiState(
            locations = locations,
            isLoading = locations.isEmpty() && syncStatus.state == SyncStatus.State.SYNCING,
            locationSelected = locationSelected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LocationSelectionUiState()
    )

    init {
        // Trigger sync to ensure locations are loaded
        syncManager.triggerManualSync()
    }

    fun selectLocation(locationId: UUID) {
        devicePreferences.setSelectedLocationId(locationId)
        _locationSelected.value = true
    }
}
