package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the main sale screen showing today's sale batches.
 */
data class SaleUiState(
    val todaysBatches: List<SaleBatch> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToNewSale: Boolean = false
)

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val saleBatchRepository: SaleBatchRepository,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()

    private var dataObservationJob: Job? = null

    init {
        observeLocationChanges()
    }

    /**
     * Observe location changes and restart data observation when location changes.
     */
    private fun observeLocationChanges() {
        viewModelScope.launch {
            devicePreferences.selectedLocationIdFlow.collect { locationId ->
                locationId?.let { observeTodaysBatches(it) }
            }
        }
    }

    private fun observeTodaysBatches(locationId: UUID) {
        // Cancel previous observation job
        dataObservationJob?.cancel()
        
        dataObservationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            saleBatchRepository.observeTodaysBatches(locationId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Помилка завантаження даних",
                            isLoading = false
                        )
                    }
                }
                .collect { batches ->
                    _uiState.update {
                        it.copy(
                            todaysBatches = batches,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onNewSaleClick() {
        _uiState.update { it.copy(navigateToNewSale = true) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToNewSale = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        devicePreferences.getSelectedLocationId()?.let { observeTodaysBatches(it) }
    }
}
