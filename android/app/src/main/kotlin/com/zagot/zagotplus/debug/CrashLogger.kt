package com.zagot.zagotplus.debug

import android.content.Context
import android.os.Build
import android.util.Log
import com.zagot.zagotplus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs crashes and errors to a local file that user can share for debugging.
 * File location: /Android/data/com.zagot.zagotplus/files/crash_log.txt
 */
@Singleton
class CrashLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logFile: File by lazy {
        File(context.getExternalFilesDir(null), LOG_FILE_NAME)
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    
    /**
     * Install as the global uncaught exception handler.
     * Call this once in Application.onCreate() BEFORE logDeviceInfo().
     */
    fun install() {
        if (defaultHandler != null) return // Already installed
        
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCrash(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash", e)
            } finally {
                // Pass to default handler (usually system crash dialog)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.d(TAG, "Crash handler installed")
    }
    
    private fun logCrash(thread: Thread, throwable: Throwable) {
        val timestamp = dateFormat.format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        
        val crashEntry = buildString {
            appendLine()
            appendLine("!!! CRASH !!!")
            appendLine("Time: $timestamp")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine("Stack trace:")
            appendLine(sw.toString())
            appendLine("!!! END CRASH !!!")
            appendLine()
        }
        
        // Write synchronously - app is about to die
        try {
            logFile.appendText(crashEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash to file", e)
        }
    }
    
    /**
     * Log an error with exception details.
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val entry = buildLogEntry("ERROR", tag, message, throwable)
        appendToFile(entry)
        Log.e(tag, message, throwable)
    }
    
    /**
     * Log debug info (useful for tracing flow before crash).
     */
    fun logDebug(tag: String, message: String) {
        val entry = buildLogEntry("DEBUG", tag, message, null)
        appendToFile(entry)
        Log.d(tag, message)
    }
    
    /**
     * Log warning.
     */
    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        val entry = buildLogEntry("WARN", tag, message, throwable)
        appendToFile(entry)
        Log.w(tag, message, throwable)
    }
    
    private fun buildLogEntry(level: String, tag: String, message: String, throwable: Throwable?): String {
        val timestamp = dateFormat.format(Date())
        val sb = StringBuilder()
        sb.appendLine("[$timestamp] $level/$tag: $message")
        
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.appendLine(sw.toString())
        }
        
        return sb.toString()
    }
    
    @Synchronized
    private fun appendToFile(entry: String) {
        try {
            // Keep log file under 500KB by trimming old entries
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
                trimLogFile()
            }
            logFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to crash log", e)
        }
    }
    
    private fun trimLogFile() {
        try {
            val lines = logFile.readLines()
            val trimmedLines = lines.takeLast(lines.size / 2)
            logFile.writeText("--- Log trimmed at ${dateFormat.format(Date())} ---\n")
            logFile.appendText(trimmedLines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim log file", e)
        }
    }
    
    /**
     * Get the log file path for sharing.
     */
    fun getLogFilePath(): String = logFile.absolutePath
    
    /**
     * Get log file for sharing via Intent.
     */
    fun getLogFileForSharing(): File = logFile
    
    /**
     * Read recent log entries (last N lines).
     */
    fun getRecentLogs(lines: Int = 100): String {
        return try {
            if (!logFile.exists()) return "No logs available"
            logFile.readLines().takeLast(lines).joinToString("\n")
        } catch (e: Exception) {
            "Failed to read logs: ${e.message}"
        }
    }
    
    /**
     * Clear the log file.
     */
    fun clearLogs() {
        try {
            logFile.writeText("--- Log cleared at ${dateFormat.format(Date())} ---\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
    
    /**
     * Write device info header (call once on app start).
     */
    fun logDeviceInfo() {
        val info = buildString {
            appendLine("=== App Start ===")
            appendLine("Time: ${dateFormat.format(Date())}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("================")
        }
        appendToFile(info)
    }
    
    companion object {
        private const val TAG = "CrashLogger"
        private const val LOG_FILE_NAME = "crash_log.txt"
        private const val MAX_FILE_SIZE = 500 * 1024L // 500KB
    }
}
