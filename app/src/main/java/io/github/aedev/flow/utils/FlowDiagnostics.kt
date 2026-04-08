package io.github.aedev.flow.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
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
    fun readSessionLogs(maxLines: Int = 600): String {
        return try {
            val pid = android.os.Process.myPid()
            // --pid restricts output to this process only; *:W = WARN level and above.
            val process = ProcessBuilder(
                "logcat", "--pid=$pid", "-d", "-t", maxLines.toString(), "*:W"
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            process.waitFor()
            output.ifBlank { "No warnings or errors found in this session." }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session logcat", e)
            "Unable to read session logs: ${e.message}\n\n" +
                    "This can happen on some heavily customized Android skins."
        }
    }

    /**
     * Returns crash reports written to disk by [FlowCrashHandler].
     * Call on any thread — file I/O is minimal (single small text file).
     */
    fun getCrashLogs(context: Context): String =
        FlowCrashHandler.getCrashLogs(context)

    /** Deletes the on-disk crash log file. */
    fun clearCrashLogs(context: Context) =
        FlowCrashHandler.clearCrashLogs(context)

    /**
     * Assembles a single shareable text report containing device metadata,
     * session logs, and any persisted crash reports.
     */
    fun buildFullReport(context: Context, sessionLogs: String): String = buildString {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
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
        val crashes = getCrashLogs(context)
        if (crashes != "No crash logs") {
            appendLine()
            appendLine("=".repeat(60))
            appendLine("CRASH REPORTS  (persisted across sessions)")
            appendLine("=".repeat(60))
            appendLine(crashes)
        }
    }

    /** one-liner block of device + app version metadata. */
    fun buildDeviceInfo(context: Context): String = buildString {
        appendLine("Manufacturer : ${Build.MANUFACTURER}")
        appendLine("Model        : ${Build.MODEL}")
        appendLine("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Brand        : ${Build.BRAND}")
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("App version  : ${pi.versionName} (${pi.longVersionCode})")
        } catch (_: Exception) {
            appendLine("App version  : unknown")
        }
    }.trimEnd()
}
