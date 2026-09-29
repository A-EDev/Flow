package io.github.aedev.flow.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * On-demand diagnostics helper.
 *
 * Reads logcat output for the current process (no special permissions required —
 * apps may always read their own PID's logs since API 18) and surfaces crash
 * reports that FlowCrashHandler persisted to disk.
 */
object FlowDiagnostics {
    private const val TAG = "FlowDiagnostics"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads recent Logcat entries for this app's process at WARNING level and
     * above.  Blocks the calling thread; run on [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param maxLines Maximum number of log lines to return (default 600).
     */
    fun readSessionLogs(maxLines: Int = 600): String =
        try {
            val pid = android.os.Process.myPid()
            // --pid restricts output to this process only; *:W = WARN level and above.
            val process =
                ProcessBuilder(
                    "logcat",
                    "--pid=$pid",
                    "-d",
                    "-t",
                    maxLines.toString(),
                    "*:W",
                ).redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            process.waitFor()
            output.ifBlank { "No warnings or errors found in this session." }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session logcat", e)
            "Unable to read session logs: ${e.message}\n\n" +
                "This can happen on some heavily customized Android skins."
        }

    /**
     * Returns crash reports written to disk by [FlowCrashHandler].
     * Call on any thread — file I/O is minimal (single small text file).
     */
    fun getCrashLogs(context: Context): String = FlowCrashHandler.getCrashLogs(context)

    /** Deletes the on-disk crash log file. */
    fun clearCrashLogs(context: Context) = FlowCrashHandler.clearCrashLogs(context)

    /**
     * Assembles a single shareable text report containing device metadata,
     * session logs, and any persisted crash reports.
     */
    fun buildFullReport(
        context: Context,
        sessionLogs: String,
    ): String =
        buildString {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
            appendLine("=".repeat(60))
            appendLine("FLOW DIAGNOSTICS REPORT")
            appendLine("Generated: $ts")
            appendLine("=".repeat(60))
            appendLine()
            appendLine(buildDeviceInfo(context))
            appendLine()
            appendLine("=".repeat(60))
            appendLine("SESSION LOGS  (W/E level, current session)")
            appendLine("=".repeat(60))
            appendLine(sessionLogs)
            val crashes = crashLogsOrNull(context)
            if (crashes != null) {
                appendLine()
                appendLine("=".repeat(60))
                appendLine("CRASH REPORTS  (persisted across sessions)")
                appendLine("=".repeat(60))
                appendLine(crashes)
            }
        }

    /** Persisted crash reports, or null when there are none. */
    fun crashLogsOrNull(context: Context): String? = getCrashLogs(context).takeUnless { it.isBlank() || it.trim() == NO_CRASH_LOGS }

    fun deviceInfo(context: Context): DeviceInfo {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.longVersionCode,
        )
    }

    /** one-liner block of device + app version metadata. */
    fun buildDeviceInfo(context: Context): String =
        deviceInfo(context).let { info ->
            buildString {
                appendLine("Manufacturer : ${info.manufacturer}")
                appendLine("Model        : ${info.model}")
                appendLine("Android      : ${info.androidRelease} (SDK ${info.sdk})")
                appendLine("Brand        : ${info.brand}")
                appendLine("App version  : ${info.versionName?.let { "$it (${info.versionCode})" } ?: "unknown"}")
            }.trimEnd()
        }

    private const val NO_CRASH_LOGS = "No crash logs"
}

/** The device and build a diagnostics report describes. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidRelease: String,
    val sdk: Int,
    val versionName: String?,
    val versionCode: Long?,
)
