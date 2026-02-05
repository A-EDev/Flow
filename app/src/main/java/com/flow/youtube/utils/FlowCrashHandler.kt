package com.flow.youtube.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global exception handler for crash monitoring and logging.
 * Catches uncaught exceptions and logs them
 */
class FlowCrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "FlowCrashHandler"
        private const val CRASH_LOG_FILE = "flow_crashes.log"
        private const val MAX_CRASH_LOG_SIZE = 500_000L // 500KB
        
        @Volatile
        private var instance: FlowCrashHandler? = null
        
        /**
         * Install the crash handler. Call from Application.onCreate()
         */
        fun install(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                        instance = FlowCrashHandler(context.applicationContext, defaultHandler)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                        Log.i(TAG, "Crash handler installed")
                    }
                }
            }
        }
        
        /**
         * Get recent crash logs
         */
        fun getCrashLogs(context: Context): String {
            return try {
                val file = File(context.filesDir, CRASH_LOG_FILE)
                if (file.exists()) file.readText() else "No crash logs"
            } catch (e: Exception) {
                "Error reading crash logs: ${e.message}"
            }
        }
        
        /**
         * Clear crash logs
         */
        fun clearCrashLogs(context: Context) {
            try {
                File(context.filesDir, CRASH_LOG_FILE).delete()
                Log.i(TAG, "Crash logs cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing crash logs", e)
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log to logcat
            Log.e(TAG, "=== UNCAUGHT EXCEPTION ===")
            Log.e(TAG, "Thread: ${thread.name} (id=${thread.id})")
            Log.e(TAG, "Exception: ${throwable.javaClass.simpleName}: ${throwable.message}")
            Log.e(TAG, getStackTraceString(throwable))
            
            // Save to file for later analysis
            saveCrashToFile(thread, throwable)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            // Pass to default handler (which may show "app has stopped" dialog and kill process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun saveCrashToFile(thread: Thread, throwable: Throwable) {
        try {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            
            // Rotate if too large
            if (file.exists() && file.length() > MAX_CRASH_LOG_SIZE) {
                val backup = File(context.filesDir, "${CRASH_LOG_FILE}.old")
                file.renameTo(backup)
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val deviceInfo = buildDeviceInfo()
            val stackTrace = getStackTraceString(throwable)
            
            val crashReport = buildString {
                appendLine("=" .repeat(60))
                appendLine("CRASH REPORT - $timestamp")
                appendLine("=" .repeat(60))
                appendLine()
                appendLine("Device Info:")
                appendLine(deviceInfo)
                appendLine()
                appendLine("Thread: ${thread.name} (id=${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace)
                appendLine()
            }
            
            file.appendText(crashReport)
            Log.i(TAG, "Crash saved to ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash to file", e)
        }
    }
    
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
    
    private fun buildDeviceInfo(): String {
        return buildString {
            appendLine("  Model: ${Build.MODEL}")
            appendLine("  Manufacturer: ${Build.MANUFACTURER}")
            appendLine("  Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("  Brand: ${Build.BRAND}")
            appendLine("  Device: ${Build.DEVICE}")
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appendLine("  App Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})")
            } catch (e: Exception) {
                appendLine("  App Version: Unknown")
            }
        }
    }
}
