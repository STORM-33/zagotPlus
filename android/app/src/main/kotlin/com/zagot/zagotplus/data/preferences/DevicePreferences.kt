package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages device-specific preferences using EncryptedSharedPreferences.
 * Generates and persists a unique device ID for transaction tracking.
 * 
 * Security: Uses Android Keystore-backed encryption for sensitive preferences.
 */
@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPreferences if encryption fails
        // This can happen on some devices with Keystore issues
        android.util.Log.w("DevicePreferences", "Failed to create encrypted prefs, using fallback", e)
        context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    private val _selectedLocationIdFlow = MutableStateFlow(getSelectedLocationId())

    /**
     * Observable flow of selected location ID.
     * Emits new value whenever location is changed via setSelectedLocationId.
     */
    val selectedLocationIdFlow: StateFlow<UUID?> = _selectedLocationIdFlow.asStateFlow()

    /**
     * Get or generate a unique device ID.
     * Uses Android ID as base combined with a random component for uniqueness.
     * Generated once on first access and persisted for the lifetime of the app installation.
     */
    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) {
            return existing
        }
        
        // Generate stable device ID using Android ID + random component
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val randomPart = UUID.randomUUID().toString().take(8)
        val newId = "${androidId}_$randomPart"
        
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    /**
     * Get the selected location ID for this device.
     * Returns null if no location is selected.
     */
    fun getSelectedLocationId(): UUID? {
        val existing = prefs.getString(KEY_SELECTED_LOCATION, null)
        return existing?.let { 
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
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
        private const val PREFS_NAME = "zagot_device_prefs_encrypted"
        private const val PREFS_NAME_FALLBACK = "zagot_device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SELECTED_LOCATION = "selected_location"
    }
}
