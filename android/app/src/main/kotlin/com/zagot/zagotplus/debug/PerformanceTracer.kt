package com.zagot.zagotplus.debug

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Traces and times operations to identify slow code paths.
 * 
 * Usage:
 * ```kotlin
 * performanceTracer.trace("loadProducts") {
 *     // Your code here
 * }
 * ```
 * 
 * For coroutines:
 * ```kotlin
 * performanceTracer.traceAsync("syncData") {
 *     // Suspend function calls
 * }
 * ```
 */
@Singleton
class PerformanceTracer @Inject constructor() {
    
    companion object {
        private const val TAG = "PerformanceTracer"
        private const val SLOW_THRESHOLD_MS = 16L // One frame at 60fps
        private const val VERY_SLOW_THRESHOLD_MS = 100L
        private const val MAX_TRACES = 1000
    }
    
    private val traces = ConcurrentLinkedQueue<TraceEvent>()
    @PublishedApi
    internal var isEnabled = true
    private var reportJob: Job? = null
    
    data class TraceEvent(
        val name: String,
        val durationMs: Long,
        val thread: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isMainThread: Boolean = Thread.currentThread().name == "main"
    )
    
    /**
     * Trace a synchronous block of code.
     */
    inline fun <T> trace(name: String, block: () -> T): T {
        if (!isEnabled) return block()
        
        val startTime = System.nanoTime()
        return try {
            block()
        } finally {
            val durationNanos = System.nanoTime() - startTime
            val durationMs = durationNanos / 1_000_000
            recordTrace(name, durationMs)
        }
    }
    
    /**
     * Trace a suspending block of code.
     */
    suspend inline fun <T> traceAsync(name: String, crossinline block: suspend () -> T): T {
        if (!isEnabled) return block()
        
        val startTime = System.nanoTime()
        return try {
            block()
        } finally {
            val durationNanos = System.nanoTime() - startTime
            val durationMs = durationNanos / 1_000_000
            recordTrace(name, durationMs)
        }
    }
    
    /**
     * Time a block and return both result and duration.
     */
    inline fun <T> measureAndTrace(name: String, block: () -> T): Pair<T, Long> {
        val duration = measureTimeMillis { }
        var result: T
        val actualDuration = measureTimeMillis {
            result = trace(name, block)
        }
        return result to actualDuration
    }
    
    @PublishedApi
    internal fun recordTrace(name: String, durationMs: Long) {
        val isMainThread = Thread.currentThread().name == "main"
        val event = TraceEvent(
            name = name,
            durationMs = durationMs,
            thread = Thread.currentThread().name,
            isMainThread = isMainThread
        )
        
        // Keep queue bounded
        while (traces.size >= MAX_TRACES) {
            traces.poll()
        }
        traces.add(event)
        
        // Log slow operations immediately
        if (isMainThread) {
            when {
                durationMs >= VERY_SLOW_THRESHOLD_MS -> {
                    Log.e(TAG, "🔴 SLOW ON MAIN: $name took ${durationMs}ms (>${VERY_SLOW_THRESHOLD_MS}ms)")
                }
                durationMs >= SLOW_THRESHOLD_MS -> {
                    Log.w(TAG, "🟡 Slow on main: $name took ${durationMs}ms (>${SLOW_THRESHOLD_MS}ms)")
                }
            }
        } else if (durationMs >= VERY_SLOW_THRESHOLD_MS * 10) {
            // Very slow background operations might still be relevant
            Log.d(TAG, "⚪ Slow background: $name took ${durationMs}ms")
        }
    }
    
    /**
     * Start periodic reporting of slow traces.
     */
    fun startPeriodicReport(intervalMs: Long = 30_000L) {
        reportJob?.cancel()
        reportJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(intervalMs)
                printSlowTracesSummary()
            }
        }
    }
    
    fun stopPeriodicReport() {
        reportJob?.cancel()
        reportJob = null
    }
    
    /**
     * Get traces that exceeded the slow threshold on main thread.
     */
    fun getSlowMainThreadTraces(): List<TraceEvent> {
        return traces.filter { it.isMainThread && it.durationMs >= SLOW_THRESHOLD_MS }
            .sortedByDescending { it.durationMs }
    }
    
    /**
     * Get all traces grouped by name with statistics.
     */
    fun getTraceStats(): Map<String, TraceStats> {
        return traces.groupBy { it.name }
            .mapValues { (_, events) ->
                TraceStats(
                    name = events.first().name,
                    count = events.size,
                    avgMs = events.map { it.durationMs }.average(),
                    maxMs = events.maxOf { it.durationMs },
                    minMs = events.minOf { it.durationMs },
                    mainThreadCount = events.count { it.isMainThread }
                )
            }
    }
    
    data class TraceStats(
        val name: String,
        val count: Int,
        val avgMs: Double,
        val maxMs: Long,
        val minMs: Long,
        val mainThreadCount: Int
    )
    
    /**
     * Print summary of slow operations.
     */
    fun printSlowTracesSummary() {
        val slowTraces = getSlowMainThreadTraces()
        if (slowTraces.isEmpty()) {
            Log.d(TAG, "No slow main thread traces recorded")
            return
        }
        
        val stats = getTraceStats()
        val slowStats = stats.values
            .filter { it.mainThreadCount > 0 && it.avgMs >= SLOW_THRESHOLD_MS }
            .sortedByDescending { it.avgMs }
            .take(10)
        
        Log.i(TAG, buildString {
            appendLine("╔════════════════════════════════════════════════════════════")
            appendLine("║ SLOW OPERATIONS SUMMARY (main thread)")
            appendLine("╠════════════════════════════════════════════════════════════")
            slowStats.forEach { stat ->
                appendLine("║ ${stat.name}")
                appendLine("║   Count: ${stat.count}, Avg: ${"%.1f".format(stat.avgMs)}ms, Max: ${stat.maxMs}ms")
            }
            appendLine("╚════════════════════════════════════════════════════════════")
        })
    }
    
    /**
     * Clear all recorded traces.
     */
    fun clear() {
        traces.clear()
    }
    
    fun setTracingEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "PerformanceTracer ${if (enabled) "enabled" else "disabled"}")
    }
}
