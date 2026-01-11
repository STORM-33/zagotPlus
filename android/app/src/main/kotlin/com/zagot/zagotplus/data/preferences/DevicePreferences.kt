package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        private const val PREFS_NAME = "zagot_device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
