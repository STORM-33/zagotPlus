package com.zagot.zagotplus.sync.engine.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android network monitor with flapping debounce and validation ping (spec Section 8).
 *
 * - On `onAvailable()`: wait 3s of stable connectivity before reporting online.
 *   Resets if `onLost()` fires during the debounce window.
 * - On `onLost()`: report offline immediately (fail fast).
 * - Validation ping before trusting connectivity (captive portal detection).
 */
@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    companion object {
        private const val TAG = "AndroidNetworkMonitor"
        const val DEBOUNCE_MS = 3_000L
        private const val PING_TIMEOUT_MS = 3_000L
        /** Delayed re-check after registration in case initial state was missed. */
        private const val RECHECK_DELAY_MS = 1_000L
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "onAvailable: $network")
            // Start debounce — don't report online yet
            startDebounce()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "onLost: $network")
            // onLost fires per-network, not when ALL networks are gone.
            // Only go offline if no other network is available.
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                cancelDebounce()
                _isConnected.value = false
            } else {
                Log.d(TAG, "Another network still active ($activeNetwork) — restarting debounce")
                startDebounce()
            }
        }

        override fun onUnavailable() {
            Log.d(TAG, "onUnavailable")
            cancelDebounce()
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Log.d(TAG, "onCapabilitiesChanged: validated=$validated, internet=$internet")

            if (validated && internet && !_isConnected.value) {
                startDebounce()
            }
        }
    }

    override fun start() {
        if (registered) return
        registered = true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Check initial state
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (isOnline) {
            startDebounce()
        }

        // Fallback: delayed re-check in case ConnectivityManager wasn't fully ready
        // during initial check or onAvailable/onCapabilitiesChanged never fired
        // (e.g., network was already established before callback registration).
        scope.launch {
            delay(RECHECK_DELAY_MS)
            if (!_isConnected.value && debounceJob?.isActive != true) {
                val recheckNetwork = connectivityManager.activeNetwork
                val recheckCaps = recheckNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                val nowOnline = recheckCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                        && recheckCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (nowOnline) {
                    Log.d(TAG, "Delayed re-check detected online — starting debounce")
                    startDebounce()
                }
            }
        }
    }

    override fun stop() {
        if (!registered) return
        registered = false
        cancelDebounce()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister callback", e)
        }
    }

    private fun startDebounce() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            Log.d(TAG, "Debounce started (${DEBOUNCE_MS}ms)")
            delay(DEBOUNCE_MS)
            // Debounce survived — validate with a ping
            val reachable = validateConnectivity()
            if (reachable) {
                Log.d(TAG, "Connectivity confirmed after debounce + ping")
                _isConnected.value = true
            } else {
                Log.w(TAG, "Validation ping failed — staying offline")
                _isConnected.value = false
            }
        }
    }

    private fun cancelDebounce() {
        debounceJob?.cancel()
        debounceJob = null
    }

    /**
     * Validation ping — ensures actual internet access, not just network interface.
     * Guards against captive portals and flaky WiFi.
     */
    private suspend fun validateConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(PING_TIMEOUT_MS) {
                val url = URL("https://connectivitycheck.gstatic.com/generate_204")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.useCaches = false
                connection.requestMethod = "HEAD"
                try {
                    val code = connection.responseCode
                    code == 204 || code == 200
                } finally {
                    connection.disconnect()
                }
            } ?: false
        } catch (e: Exception) {
            Log.d(TAG, "Validation ping failed: ${e.message}")
            false
        }
    }
}
