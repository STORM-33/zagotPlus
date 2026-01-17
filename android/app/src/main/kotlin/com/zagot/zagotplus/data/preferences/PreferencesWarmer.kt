package com.zagot.zagotplus.data.preferences

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Pre-warms SharedPreferences on a background thread to avoid main thread disk I/O.
 * 
 * EncryptedSharedPreferences and regular SharedPreferences perform disk reads
 * on first access. This class triggers that initialization during app startup
 * on a background thread, so subsequent access from the main thread is fast.
 * 
 * Usage: Call warmUp() as early as possible in Application.onCreate().
 */
@Singleton
class PreferencesWarmer @Inject constructor(
    private val devicePreferences: DevicePreferences,
    private val productOrderPreferences: ProductOrderPreferences,
    private val authPreferences: AuthPreferences
) {
    companion object {
        private const val TAG = "PreferencesWarmer"
        private const val WARMUP_TIMEOUT_MS = 5000L
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmupMutex = Mutex()
    
    @Volatile
    private var isWarmedUp = false
    
    @Volatile
    private var warmupLatch: CountDownLatch? = null
    
    /**
     * Starts background warmup of all preferences.
     * Non-blocking - returns immediately.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    fun warmUp() {
        if (isWarmedUp) return
        
        warmupLatch = CountDownLatch(1)
        
        scope.launch {
            warmupMutex.withLock {
                if (isWarmedUp) {
                    warmupLatch?.countDown()
                    return@launch
                }
                
                val totalTime = measureTimeMillis {
                    try {
                        // Warm up DevicePreferences (EncryptedSharedPreferences - slowest)
                        val deviceTime = measureTimeMillis {
                            devicePreferences.getDeviceId()
                            devicePreferences.getSelectedLocationId()
                        }
                        Log.d(TAG, "DevicePreferences warmed in ${deviceTime}ms")
                        
                        // Warm up ProductOrderPreferences
                        val productOrderTime = measureTimeMillis {
                            productOrderPreferences.getProductOrder()
                        }
                        Log.d(TAG, "ProductOrderPreferences warmed in ${productOrderTime}ms")
                        
                        // Warm up AuthPreferences
                        val authTime = measureTimeMillis {
                            authPreferences.isPinSet()
                            authPreferences.isLockedOut()
                        }
                        Log.d(TAG, "AuthPreferences warmed in ${authTime}ms")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Warmup failed", e)
                    }
                }
                
                isWarmedUp = true
                warmupLatch?.countDown()
                Log.i(TAG, "✓ All preferences warmed up in ${totalTime}ms (background thread)")
            }
        }
    }
    
    /**
     * Blocks until warmup is complete or timeout is reached.
     * Use sparingly - prefer designing flows that don't need to block.
     * 
     * @return true if warmup completed, false if timed out
     */
    fun awaitWarmup(timeoutMs: Long = WARMUP_TIMEOUT_MS): Boolean {
        if (isWarmedUp) return true
        
        val latch = warmupLatch ?: run {
            warmUp()
            warmupLatch
        }
        
        return try {
            latch?.await(timeoutMs, TimeUnit.MILLISECONDS) ?: false
        } catch (e: InterruptedException) {
            Log.w(TAG, "Warmup await interrupted")
            false
        }
    }
    
    /**
     * Check if preferences have been warmed up.
     */
    fun isReady(): Boolean = isWarmedUp
}
