package com.zagot.zagotplus.debug

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.Choreographer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive main thread performance debugger.
 * 
 * Detects and reports:
 * - Frame drops (via Choreographer monitoring)
 * - Long main thread blocks (via watchdog)
 * - Disk I/O on main thread (via StrictMode)
 * - Network on main thread (via StrictMode)
 * 
 * Usage: Inject and call initialize() in Application.onCreate() for debug builds only.
 */
@Singleton
class MainThreadDebugger @Inject constructor() {
    
    companion object {
        private const val TAG = "MainThreadDebugger"
        private const val FRAME_TIME_NANOS = 16_666_666L // 16.67ms for 60fps
        private const val BLOCK_THRESHOLD_MS = 100L // Report blocks > 100ms
        private const val WATCHDOG_INTERVAL_MS = 50L
    }
    
    private var isInitialized = false
    private val frameDropMonitor = FrameDropMonitor()
    private val blockDetector = MainThreadBlockDetector()
    
    /**
     * Initialize all debugging tools. Call from Application.onCreate() in debug builds.
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "MainThreadDebugger already initialized")
            return
        }
        isInitialized = true
        
        Log.i(TAG, "╔════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ MainThreadDebugger ENABLED")
        Log.i(TAG, "║ Monitoring: Frame drops, Thread blocks, Disk I/O, Network")
        Log.i(TAG, "╚════════════════════════════════════════════════════════════")
        
        setupStrictMode()
        frameDropMonitor.start()
        blockDetector.start()
    }
    
    /**
     * Stop all monitoring. Call when debugging is no longer needed.
     */
    fun shutdown() {
        frameDropMonitor.stop()
        blockDetector.stop()
        isInitialized = false
        Log.i(TAG, "MainThreadDebugger shutdown")
    }
    
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        detectNonSdkApiUsage()
                    }
                }
                .build()
        )
        
        Log.d(TAG, "StrictMode enabled - disk/network operations on main thread will be logged")
    }
    
    /**
     * Monitors frame rendering using Choreographer.
     * Reports when frames are dropped and provides statistics.
     */
    inner class FrameDropMonitor {
        private var isRunning = false
        private var lastFrameTimeNanos = 0L
        private var totalFrames = 0L
        private var droppedFrames = 0L
        private var maxDroppedConsecutive = 0
        
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isRunning) return
                
                if (lastFrameTimeNanos != 0L) {
                    val elapsed = frameTimeNanos - lastFrameTimeNanos
                    val expectedFrames = (elapsed / FRAME_TIME_NANOS).toInt()
                    
                    if (expectedFrames > 1) {
                        val dropped = expectedFrames - 1
                        droppedFrames += dropped
                        if (dropped > maxDroppedConsecutive) {
                            maxDroppedConsecutive = dropped
                        }
                        
                        val elapsedMs = elapsed / 1_000_000
                        
                        // Capture stack trace for severe drops
                        if (dropped >= 10) {
                            val stackTrace = captureMainThreadStack()
                            Log.e(TAG, buildString {
                                appendLine("🔴 SEVERE FRAME DROP: $dropped frames (${elapsedMs}ms)")
                                appendLine("   Current operation may be blocking the main thread:")
                                appendLine(stackTrace)
                            })
                        } else if (dropped >= 5) {
                            Log.w(TAG, "🟡 Frame drop: $dropped frames (${elapsedMs}ms) - check recent operations")
                        } else {
                            Log.d(TAG, "⚪ Minor frame drop: $dropped frames (${elapsedMs}ms)")
                        }
                    }
                    totalFrames++
                }
                lastFrameTimeNanos = frameTimeNanos
                
                // Continue monitoring
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        fun start() {
            if (isRunning) return
            isRunning = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
            Log.d(TAG, "FrameDropMonitor started")
        }
        
        fun stop() {
            isRunning = false
            Log.d(TAG, "FrameDropMonitor stopped. Stats: $totalFrames frames, $droppedFrames dropped, max consecutive: $maxDroppedConsecutive")
        }
        
        fun getStats(): FrameStats {
            val dropRate = if (totalFrames > 0) droppedFrames.toFloat() / totalFrames * 100 else 0f
            return FrameStats(totalFrames, droppedFrames, maxDroppedConsecutive, dropRate)
        }
    }
    
    data class FrameStats(
        val totalFrames: Long,
        val droppedFrames: Long,
        val maxDroppedConsecutive: Int,
        val dropRatePercent: Float
    )
    
    /**
     * Watchdog that monitors main thread responsiveness.
     * Posts tasks to main thread and measures response time.
     */
    inner class MainThreadBlockDetector {
        private var isRunning = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private val watchdogHandler = Handler(Looper.getMainLooper())
        private var lastPingTime = 0L
        private var lastPongTime = 0L
        private var blockEvents = mutableListOf<BlockEvent>()
        
        private val watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                
                val currentTime = System.currentTimeMillis()
                
                // Check if previous ping was responded to
                if (lastPingTime > lastPongTime) {
                    val blockTime = currentTime - lastPingTime
                    if (blockTime > BLOCK_THRESHOLD_MS) {
                        // Main thread is blocked - capture stack
                        val stackTrace = captureMainThreadStack()
                        val event = BlockEvent(blockTime, stackTrace, System.currentTimeMillis())
                        blockEvents.add(event)
                        
                        if (blockTime > 500) {
                            Log.e(TAG, buildString {
                                appendLine("🔴 SEVERE BLOCK: Main thread blocked for ${blockTime}ms!")
                                appendLine("   Stack trace of blocking operation:")
                                appendLine(stackTrace)
                            })
                        } else if (blockTime > 200) {
                            Log.w(TAG, "🟡 Main thread blocked for ${blockTime}ms")
                        }
                    }
                }
                
                // Send new ping
                lastPingTime = currentTime
                mainHandler.post { lastPongTime = System.currentTimeMillis() }
                
                // Schedule next check
                watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        
        fun start() {
            if (isRunning) return
            isRunning = true
            lastPongTime = System.currentTimeMillis()
            watchdogHandler.post(watchdogRunnable)
            Log.d(TAG, "MainThreadBlockDetector started (threshold: ${BLOCK_THRESHOLD_MS}ms)")
        }
        
        fun stop() {
            isRunning = false
            watchdogHandler.removeCallbacks(watchdogRunnable)
            
            if (blockEvents.isNotEmpty()) {
                Log.i(TAG, buildString {
                    appendLine("MainThreadBlockDetector stopped. Block events summary:")
                    appendLine("   Total blocks detected: ${blockEvents.size}")
                    val avgBlock = blockEvents.map { it.durationMs }.average()
                    val maxBlock = blockEvents.maxOfOrNull { it.durationMs } ?: 0
                    appendLine("   Average block: ${avgBlock.toLong()}ms, Max: ${maxBlock}ms")
                })
            } else {
                Log.d(TAG, "MainThreadBlockDetector stopped. No significant blocks detected!")
            }
        }
        
        fun getBlockEvents(): List<BlockEvent> = blockEvents.toList()
    }
    
    data class BlockEvent(
        val durationMs: Long,
        val stackTrace: String,
        val timestamp: Long
    )
    
    /**
     * Captures the current stack trace of the main thread.
     * Useful for identifying what code is blocking.
     */
    private fun captureMainThreadStack(): String {
        val mainThread = Looper.getMainLooper().thread
        val stackTrace = mainThread.stackTrace
        
        return buildString {
            // Skip framework frames, focus on app code
            var foundAppCode = false
            for (element in stackTrace) {
                val className = element.className
                
                // Skip internal watchdog/monitor frames
                if (className.contains("MainThreadDebugger")) continue
                
                // Highlight app code
                val isAppCode = className.startsWith("com.zagot.zagotplus")
                if (isAppCode) foundAppCode = true
                
                val prefix = if (isAppCode) "   → " else "     "
                appendLine("$prefix$element")
                
                // Limit stack depth
                if (length > 2000) {
                    appendLine("     ... (truncated)")
                    break
                }
            }
            
            if (!foundAppCode) {
                appendLine("   (No app code in stack - may be framework operation)")
            }
        }
    }
    
    /**
     * Manually trigger a performance report to logcat.
     */
    fun printReport() {
        val frameStats = frameDropMonitor.getStats()
        val blockEvents = blockDetector.getBlockEvents()
        
        Log.i(TAG, buildString {
            appendLine("╔════════════════════════════════════════════════════════════")
            appendLine("║ PERFORMANCE REPORT")
            appendLine("╠════════════════════════════════════════════════════════════")
            appendLine("║ Frame Statistics:")
            appendLine("║   Total frames: ${frameStats.totalFrames}")
            appendLine("║   Dropped frames: ${frameStats.droppedFrames}")
            appendLine("║   Drop rate: ${"%.2f".format(frameStats.dropRatePercent)}%")
            appendLine("║   Max consecutive drops: ${frameStats.maxDroppedConsecutive}")
            appendLine("╠════════════════════════════════════════════════════════════")
            appendLine("║ Block Events: ${blockEvents.size} detected")
            if (blockEvents.isNotEmpty()) {
                val top3 = blockEvents.sortedByDescending { it.durationMs }.take(3)
                top3.forEachIndexed { index, event ->
                    appendLine("║   ${index + 1}. ${event.durationMs}ms block")
                }
            }
            appendLine("╚════════════════════════════════════════════════════════════")
        })
    }
}
