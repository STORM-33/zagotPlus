package com.zagot.zagotplus.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectivityObserver"

/**
 * Observes network connectivity state.
 * Provides a Flow that emits true when online, false when offline.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Flow that emits the current connectivity state.
     * Uses callbackFlow to listen for network changes.
     * 
     * Note: We check NET_CAPABILITY_VALIDATED to ensure actual internet connectivity,
     * not just that a network interface is available. This correctly handles cases like
     * mobile network being available but data transfer disabled.
     * 
     * VPN connectivity checks use the callbackFlow's coroutine scope to prevent memory leaks.
     * All child coroutines are automatically cancelled when the flow is closed.
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        // Track active VPN check jobs for cancellation on close
        val activeJobs = mutableListOf<Job>()
        
        /**
         * Launch a VPN connectivity check in the callbackFlow's scope.
         * This ensures the coroutine is cancelled when the flow closes.
         */
        fun launchVpnCheck(source: String) {
            val job = launch {
                val actuallyConnected = verifyActualConnectivity()
                Log.d(TAG, "[$source] VPN active check result: $actuallyConnected")
                trySend(actuallyConnected)
            }
            activeJobs.add(job)
            job.invokeOnCompletion { activeJobs.remove(job) }
        }
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "[RequestCallback] onAvailable: network=$network")
                // Don't immediately report online - wait for capabilities check
                // onCapabilitiesChanged will be called shortly after with validation status
            }

            override fun onLost(network: Network) {
                val connected = isCurrentlyConnected()
                Log.d(TAG, "[RequestCallback] onLost: network=$network, isCurrentlyConnected=$connected")
                trySend(connected)
            }

            override fun onUnavailable() {
                Log.d(TAG, "[RequestCallback] onUnavailable")
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // Only report online if network is validated (actual internet access confirmed)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                logAllCapabilities(networkCapabilities, "RequestCallback")

                // VPN connections may report VALIDATED even without actual connectivity
                if (isVpn && isValidated) {
                    Log.d(TAG, "[RequestCallback] onCapabilitiesChanged: VPN detected with VALIDATED, performing active check")
                    launchVpnCheck("RequestCallback")
                } else {
                    val result = hasInternet && isValidated
                    Log.d(TAG, "[RequestCallback] onCapabilitiesChanged: network=$network, hasInternet=$hasInternet, isValidated=$isValidated, emitting=$result")
                    trySend(result)
                }
            }
        }
        
        // Callback for default network changes
        val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val connected = isCurrentlyConnected()
                Log.d(TAG, "[DefaultCallback] onAvailable: network=$network, isCurrentlyConnected=$connected")
                // Check actual connectivity, not just network availability
                trySend(connected)
            }

            override fun onLost(network: Network) {
                val connected = isCurrentlyConnected()
                Log.d(TAG, "[DefaultCallback] onLost: network=$network, isCurrentlyConnected=$connected")
                trySend(connected)
            }

            override fun onUnavailable() {
                Log.d(TAG, "[DefaultCallback] onUnavailable")
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                logAllCapabilities(networkCapabilities, "DefaultCallback")

                // VPN connections may report VALIDATED even without actual connectivity
                if (isVpn && isValidated) {
                    Log.d(TAG, "[DefaultCallback] onCapabilitiesChanged: VPN detected with VALIDATED, performing active check")
                    launchVpnCheck("DefaultCallback")
                } else {
                    val result = hasInternet && isValidated
                    Log.d(TAG, "[DefaultCallback] onCapabilitiesChanged: network=$network, hasInternet=$hasInternet, isValidated=$isValidated, emitting=$result")
                    trySend(result)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        // Emit initial state - check for VPN and verify if needed
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        if (isVpn && isValidated) {
            Log.d(TAG, "Initial state: VPN detected, performing active connectivity check")
            trySend(false) // Emit false initially while checking
            launchVpnCheck("Initial")
        } else {
            val initialState = isCurrentlyConnected()
            Log.d(TAG, "Emitting initial state: $initialState")
            trySend(initialState)
        }

        connectivityManager.registerNetworkCallback(request, callback)
        connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)

        awaitClose {
            // Cancel any pending VPN check jobs to prevent leaks
            activeJobs.forEach { it.cancel() }
            connectivityManager.unregisterNetworkCallback(callback)
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        }
    }.distinctUntilChanged()

    /**
     * Check current connectivity state synchronously.
     * Returns true only if network has validated internet access.
     *
     * Note: For VPN connections, Android may report VALIDATED even when
     * the VPN tunnel has no actual internet connectivity. This method
     * only checks the network capabilities; for VPN scenarios, use
     * [verifyActualConnectivity] for a more reliable check.
     */
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            Log.d(TAG, "isCurrentlyConnected: activeNetwork is NULL -> false")
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            Log.d(TAG, "isCurrentlyConnected: capabilities is NULL for network=$network -> false")
            return false
        }
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        // VPN connections may falsely report VALIDATED - be conservative
        if (isVpn) {
            Log.d(TAG, "isCurrentlyConnected: VPN detected, capabilities report validated=$isValidated but VPN may not have actual connectivity")
            logAllCapabilities(capabilities, "isCurrentlyConnected")
            // For VPN, we can't trust VALIDATED alone - return false to trigger active check
            // The Flow will do async verification
            return false
        }

        val result = hasInternet && isValidated
        Log.d(TAG, "isCurrentlyConnected: network=$network, hasInternet=$hasInternet, isValidated=$isValidated -> $result")
        logAllCapabilities(capabilities, "isCurrentlyConnected")
        return result
    }

    /**
     * Check if network is VPN-only (no underlying WiFi/Cellular).
     */
    private fun isVpnOnly(capabilities: NetworkCapabilities): Boolean {
        val isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return isVpn && !hasWifi && !hasCellular && !hasEthernet
    }

    /**
     * Perform an actual HTTP request to verify internet connectivity.
     * This is needed for VPN scenarios where VALIDATED may be unreliable.
     * Uses Google's generate_204 endpoint which returns quickly.
     */
    suspend fun verifyActualConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = withTimeoutOrNull(3000L) {
                val url = URL("https://connectivitycheck.gstatic.com/generate_204")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.useCaches = false
                connection.requestMethod = "HEAD"
                try {
                    val responseCode = connection.responseCode
                    Log.d(TAG, "verifyActualConnectivity: responseCode=$responseCode")
                    responseCode == 204 || responseCode == 200
                } finally {
                    connection.disconnect()
                }
            }
            val isConnected = result == true
            Log.d(TAG, "verifyActualConnectivity: result=$isConnected")
            isConnected
        } catch (e: Exception) {
            Log.d(TAG, "verifyActualConnectivity: exception=${e.message}")
            false
        }
    }

    /**
     * Log all relevant capabilities for debugging.
     */
    private fun logAllCapabilities(caps: NetworkCapabilities, source: String) {
        val capsList = buildList {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) add("INTERNET")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("VALIDATED")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) add("NOT_METERED")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) add("NOT_RESTRICTED")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)) add("TRUSTED")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) add("NOT_VPN")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) add("NOT_ROAMING")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) add("FOREGROUND")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)) add("NOT_CONGESTED")
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) add("NOT_SUSPENDED")
        }
        val transportList = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }
        Log.d(TAG, "[$source] capabilities: $capsList, transports: $transportList")
    }
}
