package io.github.aedev.flow.player.error

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.player.EnhancedPlayerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Player diagnostics utility.
 *
 * Maintains an in-memory circular log buffer of player events and errors so that
 * when a user encounters a playback problem the developer can ask them to tap
 * "Copy Logs" and paste the result directly into a bug report — no ADB required.
 *
 * Usage:
 *   PlayerDiagnostics.log("TAG", "Something happened")
 *   PlayerDiagnostics.logError("TAG", "Error occurred", throwable)
 *   PlayerDiagnostics.buildReport(context)  // → formatted string
 *   PlayerDiagnostics.copyToClipboard(context)
 */
@UnstableApi
object PlayerDiagnostics {

    private const val TAG = "PlayerDiagnostics"
    private const val MAX_LOG_ENTRIES = 300
    private val DATE_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // ── In-memory ring buffer ──────────────────────────────────────────────────

    data class LogEntry(
        val timestampMs: Long,
        val level: String,    // "D", "I", "W", "E"
        val tag: String,
        val message: String,
        val errorCode: Int? = null,   // PlaybackException error code, if applicable
        val throwableType: String? = null,
        val throwableMsg: String? = null
    ) {
        override fun toString(): String {
            val ts = DATE_FORMAT.format(Date(timestampMs))
            val ex = when {
                throwableType != null && throwableMsg != null -> " [$throwableType: $throwableMsg]"
                throwableType != null -> " [$throwableType]"
                else -> ""
            }
            val ec = if (errorCode != null) " (errCode=$errorCode)" else ""
            return "$ts $level/$tag: $message$ec$ex"
        }
    }

    private val buffer = ConcurrentLinkedDeque<LogEntry>()

    // ── Logging helpers ────────────────────────────────────────────────────────

    fun log(tag: String, message: String) {
        append(LogEntry(System.currentTimeMillis(), "D", tag, message))
    }

    fun logInfo(tag: String, message: String) {
        append(LogEntry(System.currentTimeMillis(), "I", tag, message))
    }

    fun logWarning(tag: String, message: String) {
        append(LogEntry(System.currentTimeMillis(), "W", tag, message))
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        append(LogEntry(
            timestampMs    = System.currentTimeMillis(),
            level          = "E",
            tag            = tag,
            message        = message,
            throwableType  = throwable?.javaClass?.simpleName,
            throwableMsg   = throwable?.message?.take(200)
        ))
    }

    fun logPlaybackError(tag: String, error: PlaybackException) {
        append(LogEntry(
            timestampMs    = System.currentTimeMillis(),
            level          = "E",
            tag            = tag,
            message        = "PlaybackException: ${error.message?.take(200)}",
            errorCode      = error.errorCode,
            throwableType  = error.cause?.javaClass?.simpleName,
            throwableMsg   = error.cause?.message?.take(200)
        ))
    }

    fun logRefocusGlitch(tag: String, detail: String) {
        append(LogEntry(
            timestampMs = System.currentTimeMillis(),
            level = "W",
            tag = tag,
            message = "REFOCUS_GLITCH: $detail"
        ))
    }

    private fun append(entry: LogEntry) {
        buffer.addLast(entry)
        // Trim to MAX_LOG_ENTRIES
        while (buffer.size > MAX_LOG_ENTRIES) {
            buffer.pollFirst()
        }
    }

    // ── Clear ──────────────────────────────────────────────────────────────────

    fun clear() = buffer.clear()

    // ── Report builder ─────────────────────────────────────────────────────────

    /**
     * Builds a human-readable diagnostic report string ready for sharing.
     */
    fun buildReport(context: Context): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("       FLOW PLAYER DIAGNOSTICS")
        sb.appendLine("  ${DATE_FORMAT.format(Date())} — ${Build.MODEL}")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        // Device info
        sb.appendLine("── Device ─────────────────────────")
        sb.appendLine("Manufacturer : ${Build.MANUFACTURER}")
        sb.appendLine("Model        : ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("Board        : ${Build.BOARD}")
        sb.appendLine("ABI          : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Android SDK  : ${Build.VERSION.SDK_INT}")
        sb.appendLine("Android ver  : ${Build.VERSION.RELEASE}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
        }
        sb.appendLine()

        // App version
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            sb.appendLine("── App ────────────────────────────")
            sb.appendLine("Package   : ${context.packageName}")
            sb.appendLine("Version   : ${pi.versionName} (${
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode
            })")
            sb.appendLine()
        } catch (e: Exception) {
        }

        sb.appendLine("── Player State ───────────────────")
        try {
            val mgr = EnhancedPlayerManager.getInstance()
            val state = mgr.playerState.value
            val player = mgr.getPlayer()
            sb.appendLine("VideoId       : ${state.currentVideoId ?: "none"}")
            sb.appendLine("isPlaying     : ${state.isPlaying}")
            sb.appendLine("playWhenReady : ${state.playWhenReady}")
            sb.appendLine("isBuffering   : ${state.isBuffering}")
            sb.appendLine("isPrepared    : ${state.isPrepared}")
            sb.appendLine("hasEnded      : ${state.hasEnded}")
            sb.appendLine("currentQuality: ${state.currentQuality}p  (effective: ${state.effectiveQuality}p)")
            sb.appendLine("error         : ${state.error ?: "none"}")
            sb.appendLine("recoveryAttempted: ${state.recoveryAttempted}")
            sb.appendLine("queueSize     : ${state.queueSize}")
            if (player != null) {
                val pbState = when (player.playbackState) {
                    Player.STATE_IDLE      -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY     -> "READY"
                    Player.STATE_ENDED     -> "ENDED"
                    else                   -> "UNKNOWN(${player.playbackState})"
                }
                val pos  = player.currentPosition
                val dur  = player.duration.let { if (it == Long.MIN_VALUE) -1L else it }
                sb.appendLine("ExoState      : $pbState")
                sb.appendLine("Position      : ${formatMs(pos)}")
                sb.appendLine("Duration      : ${if (dur < 0) "UNSET" else formatMs(dur)}")
                sb.appendLine("BufferedPct   : ${player.bufferedPercentage}%")
            }
        } catch (e: Exception) {
            sb.appendLine("(could not read player state: ${e.message})")
        }
        sb.appendLine()

        // Log entries
        sb.appendLine("── Event Log (last ${buffer.size} entries) ──")
        if (buffer.isEmpty()) {
            sb.appendLine("(no log entries)")
        } else {
            val entries = buffer.toList()
            entries.forEach { sb.appendLine(it.toString()) }
        }
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════")

        return sb.toString()
    }

    /**
     * Copy the diagnostic report to the system clipboard.
     * Returns true on success.
     */
    fun copyToClipboard(context: Context): Boolean {
        return try {
            val report = buildReport(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Flow Player Diagnostics", report)
            clipboard.setPrimaryClip(clip)
            Log.i(TAG, "Diagnostics copied to clipboard (${report.length} chars)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy diagnostics", e)
            false
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun formatMs(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else               "%d:%02d".format(minutes, seconds)
    }
}
