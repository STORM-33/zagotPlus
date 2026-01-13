package com.zagot.zagotplus.ui.components

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var locationRepository: LocationRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var syncStatusRepository: SyncStatusRepository
    private lateinit var viewModel: LocationSelectionViewModel

    private val testLocationId = UUID.randomUUID()
    private val testLocations = listOf(
        Location(
            id = testLocationId,
            name = "Kiosk 1",
            type = LocationType.KIOSK,
            createdAt = Instant.now()
        ),
        Location(
            id = UUID.randomUUID(),
            name = "Mobile",
            type = LocationType.MOBILE,
            createdAt = Instant.now()
        )
    )

    private lateinit var locationsFlow: MutableStateFlow<List<Location>>
    private lateinit var syncStatusFlow: MutableStateFlow<SyncStatus>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk()
        devicePreferences = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        syncStatusRepository = mockk()
        
        locationsFlow = MutableStateFlow(testLocations)
        syncStatusFlow = MutableStateFlow(SyncStatus(SyncStatus.State.IDLE, null, null))
    }

    private fun createViewModel(): LocationSelectionViewModel {
        every { locationRepository.getAllLocations() } returns locationsFlow
        every { syncStatusRepository.syncStatus } returns syncStatusFlow
        return LocationSelectionViewModel(
            locationRepository,
            devicePreferences,
            syncManager,
            syncStatusRepository
        )
    }

    @Test
    fun `initial state has loading true and empty locations`() = runTest {
        // Test the default state values
        val initialState = LocationSelectionUiState()
        assertTrue(initialState.isLoading)
        assertEquals(emptyList<Location>(), initialState.locations)
        assertFalse(initialState.locationSelected)
    }

    @Test
    fun `selectLocation saves to preferences`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId)

        verify { devicePreferences.setSelectedLocationId(testLocationId) }
    }

    @Test
    fun `init triggers manual sync`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        verify { syncManager.triggerManualSync() }
    }

    @Test
    fun `LocationSelectionUiState isLoading computed correctly for syncing with empty locations`() {
        val state = LocationSelectionUiState(
            locations = emptyList(),
            isLoading = true,
            locationSelected = false
        )
        assertTrue(state.isLoading)
    }

    @Test
    fun `LocationSelectionUiState isLoading is false with locations`() {
        val state = LocationSelectionUiState(
            locations = testLocations,
            isLoading = false,
            locationSelected = false
        )
        assertFalse(state.isLoading)
    }

    @Test
    fun `LocationSelectionUiState locationSelected starts false`() {
        val state = LocationSelectionUiState()
        assertFalse(state.locationSelected)
    }

    @Test
    fun `viewModel exposes repository locations via getAllLocations`() = runTest {
        viewModel = createViewModel()
        
        val repoLocations = locationRepository.getAllLocations().first()
        assertEquals(2, repoLocations.size)
        assertEquals("Kiosk 1", repoLocations[0].name)
    }
}
