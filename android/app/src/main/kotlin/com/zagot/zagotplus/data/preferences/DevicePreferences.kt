package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages device-specific preferences using SharedPreferences.
 * Generates and persists a unique device ID for transaction tracking.
 */
@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _selectedLocationIdFlow = MutableStateFlow(getSelectedLocationId())

    /**
     * Observable flow of selected location ID.
     * Emits new value whenever location is changed via setSelectedLocationId.
     */
    val selectedLocationIdFlow: StateFlow<UUID?> = _selectedLocationIdFlow.asStateFlow()

    /**
     * Get or generate a unique device ID.
     * Generated once on first access and persisted for the lifetime of the app installation.
     */
    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) {
            return existing
        }
        
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    /**
     * Get the selected location ID for this device.
     * Returns null if no location is selected.
     */
    fun getSelectedLocationId(): UUID? {
        val existing = prefs.getString(KEY_SELECTED_LOCATION, null)
        return existing?.let { UUID.fromString(it) }
    }

    /**
     * Set the selected location ID for this device.
     * Updates both SharedPreferences and the observable flow.
     */
    fun setSelectedLocationId(locationId: UUID) {
        prefs.edit().putString(KEY_SELECTED_LOCATION, locationId.toString()).apply()
        _selectedLocationIdFlow.value = locationId
    }

    companion object {
        private const val PREFS_NAME = "zagot_device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SELECTED_LOCATION = "selected_location"
    }
}
