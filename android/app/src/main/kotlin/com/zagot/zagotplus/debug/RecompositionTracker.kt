package com.zagot.zagotplus.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Compose recomposition to identify excessive recompositions.
 * 
 * Usage in Composables:
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel) {
 *     RecompositionTracker("MyScreen")
 *     // ... rest of composable
 * }
 * ```
 */
@Singleton
class RecompositionTracker @Inject constructor() {
    
    companion object {
        private const val TAG = "RecompositionTracker"
        private const val EXCESSIVE_THRESHOLD = 10 // recompositions per second
    }
    
    private val recompositionCounts = ConcurrentHashMap<String, RecompositionData>()
    private var isEnabled = true
    
    data class RecompositionData(
        var count: Int = 0,
        var lastResetTime: Long = System.currentTimeMillis(),
        var excessiveWarningShown: Boolean = false
    )
    
    /**
     * Record a recomposition for a named component.
     */
    fun recordRecomposition(name: String) {
        if (!isEnabled) return
        
        val data = recompositionCounts.getOrPut(name) { RecompositionData() }
        val now = System.currentTimeMillis()
        val elapsed = now - data.lastResetTime
        
        // Reset counter every second
        if (elapsed >= 1000) {
            // Check if excessive before reset
            if (data.count >= EXCESSIVE_THRESHOLD && !data.excessiveWarningShown) {
                Log.w(TAG, "🟡 Excessive recomposition: $name - ${data.count} times in ${elapsed}ms")
                data.excessiveWarningShown = true
            }
            data.count = 0
            data.lastResetTime = now
            data.excessiveWarningShown = false
        }
        
        data.count++
    }
    
    /**
     * Get recomposition statistics for all tracked components.
     */
    fun getStats(): Map<String, Int> {
        return recompositionCounts.mapValues { it.value.count }
    }
    
    /**
     * Print a summary of recomposition activity.
     */
    fun printSummary() {
        val stats = recompositionCounts.entries
            .sortedByDescending { it.value.count }
            .take(10)
        
        Log.i(TAG, buildString {
            appendLine("╔════════════════════════════════════════════════════════════")
            appendLine("║ RECOMPOSITION SUMMARY")
            appendLine("╠════════════════════════════════════════════════════════════")
            if (stats.isEmpty()) {
                appendLine("║ No recompositions tracked")
            } else {
                stats.forEach { (name, data) ->
                    val status = if (data.count >= EXCESSIVE_THRESHOLD) "⚠️" else "✓"
                    appendLine("║ $status $name: ${data.count} recompositions")
                }
            }
            appendLine("╚════════════════════════════════════════════════════════════")
        })
    }
    
    fun clear() {
        recompositionCounts.clear()
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

/**
 * Composable helper to track recompositions.
 * Place at the top of any Composable you want to monitor.
 * 
 * @param name Identifier for this composable (use function name)
 * @param tracker Optional tracker instance (defaults to creating one per call)
 */
@Composable
fun RecompositionTracker(
    name: String,
    tracker: RecompositionTracker? = null
) {
    // Track each recomposition
    val actualTracker = tracker ?: remember { RecompositionTracker() }
    actualTracker.recordRecomposition(name)
    
    // Log on first composition
    DisposableEffect(name) {
        Log.v("RecompositionTracker", "⚪ $name composed")
        onDispose {
            Log.v("RecompositionTracker", "⚪ $name disposed")
        }
    }
}

/**
 * Debug composable that shows recomposition count visually.
 * Useful during development to spot hot recomposition areas.
 */
@Composable
fun RecompositionCounter(name: String) {
    val count = remember { mutableMapOf(name to 0) }
    count[name] = (count[name] ?: 0) + 1
    
    LaunchedEffect(count[name]) {
        if ((count[name] ?: 0) > 1) {
            Log.d("RecompositionCounter", "$name recomposed ${count[name]} times")
        }
    }
}
