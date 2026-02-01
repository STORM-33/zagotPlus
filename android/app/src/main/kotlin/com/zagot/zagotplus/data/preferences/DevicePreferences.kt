package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zagot.zagotplus.hardware.printer.PrinterConfig
import com.zagot.zagotplus.hardware.scales.ScalesConfig
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
 * IMPORTANT: Throws RuntimeException if encryption fails - never silently degrades to unencrypted storage.
 */
@Singleton
open class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val encryptedPrefs: SharedPreferences by lazy { createEncryptedPrefs() }
    
    // Test-only: allows injecting mock SharedPreferences
    @Volatile
    internal var testPrefsOverride: SharedPreferences? = null
    
    private val prefs: SharedPreferences
        get() = testPrefsOverride ?: encryptedPrefs
    
    private fun createEncryptedPrefs(): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // SECURITY: Never silently degrade to unencrypted storage
            // This could expose sensitive device data
            android.util.Log.e("DevicePreferences", "SECURITY ERROR: Encrypted prefs failed!", e)
            throw RuntimeException("Security initialization failed: encrypted preferences unavailable", e)
        }
    }

    private val _selectedLocationIdFlow by lazy { MutableStateFlow(getSelectedLocationId()) }

    /**
     * Observable flow of selected location ID.
     * Emits new value whenever location is changed via setSelectedLocationId.
     */
    val selectedLocationIdFlow: StateFlow<UUID?> by lazy { _selectedLocationIdFlow.asStateFlow() }

    /**
     * Get or generate a unique device ID.
     * Uses Android ID as base combined with a random component for uniqueness.
     * Generated once on first access and persisted for the lifetime of the app installation.
     */
    open fun getDeviceId(): String {
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
    open fun getSelectedLocationId(): UUID? {
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

    // ==================== Scales Configuration ====================

    /**
     * Get scales configuration.
     * Returns null if scales are not configured.
     */
    fun getScalesConfig(): ScalesConfig? {
        val ip = prefs.getString(KEY_SCALES_IP, null)
        if (ip.isNullOrBlank()) return null

        return ScalesConfig(
            ipAddress = ip,
            port = prefs.getInt(KEY_SCALES_PORT, DEFAULT_SCALES_PORT),
            protocol = prefs.getString(KEY_SCALES_PROTOCOL, "auto") ?: "auto",
            autoConnect = prefs.getBoolean(KEY_SCALES_AUTO_CONNECT, true),
            wifiSsid = prefs.getString(KEY_SCALES_WIFI_SSID, DEFAULT_SCALES_WIFI_SSID)
        )
    }

    /**
     * Save scales configuration.
     */
    fun setScalesConfig(config: ScalesConfig) {
        prefs.edit()
            .putString(KEY_SCALES_IP, config.ipAddress)
            .putInt(KEY_SCALES_PORT, config.port)
            .putString(KEY_SCALES_PROTOCOL, config.protocol)
            .putBoolean(KEY_SCALES_AUTO_CONNECT, config.autoConnect)
            .putString(KEY_SCALES_WIFI_SSID, config.wifiSsid)
            .apply()
    }

    /**
     * Initialize with default AP mode settings if not configured.
     * USR-W610 operates as AP at 10.10.100.254:8899.
     */
    fun initializeDefaultScalesConfig() {
        if (prefs.getString(KEY_SCALES_IP, null) == null) {
            setScalesConfig(ScalesConfig(
                ipAddress = DEFAULT_SCALES_IP,
                port = DEFAULT_SCALES_PORT,
                protocol = "auto",
                autoConnect = true,
                wifiSsid = DEFAULT_SCALES_WIFI_SSID
            ))
        }
    }

    /**
     * Clear scales configuration.
     */
    fun clearScalesConfig() {
        prefs.edit()
            .remove(KEY_SCALES_IP)
            .remove(KEY_SCALES_PORT)
            .remove(KEY_SCALES_PROTOCOL)
            .remove(KEY_SCALES_AUTO_CONNECT)
            .remove(KEY_SCALES_WIFI_SSID)
            .apply()
    }

    // ==================== Printer Configuration ====================

    /**
     * Get printer configuration.
     * Returns null if printer is not configured.
     */
    fun getPrinterConfig(): PrinterConfig? {
        val address = prefs.getString(KEY_PRINTER_ADDRESS, null)
        if (address.isNullOrBlank()) return null

        return PrinterConfig(
            address = address,
            name = prefs.getString(KEY_PRINTER_NAME, "Printer") ?: "Printer",
            autoConnect = prefs.getBoolean(KEY_PRINTER_AUTO_CONNECT, true)
        )
    }

    /**
     * Save printer configuration.
     */
    fun setPrinterConfig(config: PrinterConfig) {
        prefs.edit()
            .putString(KEY_PRINTER_ADDRESS, config.address)
            .putString(KEY_PRINTER_NAME, config.name)
            .putBoolean(KEY_PRINTER_AUTO_CONNECT, config.autoConnect)
            .apply()
    }

    /**
     * Clear printer configuration.
     */
    fun clearPrinterConfig() {
        prefs.edit()
            .remove(KEY_PRINTER_ADDRESS)
            .remove(KEY_PRINTER_NAME)
            .remove(KEY_PRINTER_AUTO_CONNECT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "zagot_device_prefs_encrypted"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SELECTED_LOCATION = "selected_location"

        // Hardware settings keys - Scales
        private const val KEY_SCALES_IP = "scales_ip"
        private const val KEY_SCALES_PORT = "scales_port"
        private const val KEY_SCALES_PROTOCOL = "scales_protocol"
        private const val KEY_SCALES_AUTO_CONNECT = "scales_auto_connect"
        private const val KEY_SCALES_WIFI_SSID = "scales_wifi_ssid"

        // Hardware settings keys - Printer
        private const val KEY_PRINTER_ADDRESS = "printer_address"
        private const val KEY_PRINTER_NAME = "printer_name"
        private const val KEY_PRINTER_AUTO_CONNECT = "printer_auto_connect"

        // Default values for AP mode (USR-W610 as Access Point, no router)
        const val DEFAULT_SCALES_IP = "10.10.100.254"
        const val DEFAULT_SCALES_PORT = 8899
        const val DEFAULT_SCALES_WIFI_SSID = "ZAGOT-SCALES"
        
        /**
         * Create a DevicePreferences instance for testing with mock SharedPreferences.
         */
        internal fun createForTest(context: Context, testPrefs: SharedPreferences): DevicePreferences {
            return DevicePreferences(context).also {
                it.testPrefsOverride = testPrefs
            }
        }
    }
}
