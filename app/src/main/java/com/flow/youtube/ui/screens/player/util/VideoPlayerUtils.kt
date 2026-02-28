package com.flow.youtube.ui.screens.player.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.flow.youtube.data.model.Video
import org.schabi.newpipe.extractor.stream.VideoStream

object VideoPlayerUtils {

    // ─────────────────────────────────────────────────────────────────────────
    // Codec helpers — shared between the download dialog and the ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Known YouTube video itags, grouped by codec.
     * New itags are occasionally added by YouTube; unknown ones fall through to
     * the format-name heuristic below.
     */
    private val AV1_ITAGS = setOf(394, 395, 396, 397, 398, 399, 400, 401, 571, 694, 695, 696, 697, 698, 699, 700, 701)
    private val VP9_ITAGS = setOf(
        242, 243, 244, 245, 246, 247, 248, 271, 272,
        302, 303, 308, 313, 315,
        330, 331, 332, 333, 334, 335, 336, 337
    )
    private val H264_ITAGS = setOf(
        133, 134, 135, 136, 137, 138, 160,
        264, 266, 298, 299, 300, 301, 304, 305,
        // Muxed H264 streams
        18, 22, 43, 59
    )

    /**
     * Derive a short, lowercase codec key from an InnerTube `mimeType` string.
     * Examples: `"video/mp4; codecs=\"av01.0.08M.08\""` → `"av1"`.
     *
     * Used in the ViewModel when building `streamSizes` from the InnerTube player response.
     */
    fun codecKeyFromMimeType(mimeType: String): String {
        val m = mimeType.lowercase()
        val codecs = m.substringAfter("codecs=\"", "").substringBefore("\"")
        return when {
            "av01" in codecs                    -> "av1"
            "vp09" in codecs || "vp9" in codecs -> "vp9"
            "vp08" in codecs || "vp8" in codecs -> "vp8"
            "hev1" in codecs || "hvc1" in codecs -> "hevc"
            "avc1" in codecs                    -> "h264"
            "webm" in m                         -> "vp9"   // WebM without explicit codec tag
            else                                -> "h264"  // Default: assume H264/mp4
        }
    }

    /**
     * Derive a short, lowercase codec key from a NewPipe [VideoStream].
     *
     * Priority:
     * 1. `itag` query parameter in the stream URL — most reliable.
     * 2. Format MIME type / name heuristic — fallback for unknown itags.
     */
    fun codecKeyFromStream(stream: VideoStream): String {
        val url = try { stream.content ?: "" } catch (_: Exception) { "" }
        val itag = try {
            Uri.parse(url).getQueryParameter("itag")?.toIntOrNull()
        } catch (_: Exception) { null }

        when (itag) {
            in AV1_ITAGS  -> return "av1"
            in VP9_ITAGS  -> return "vp9"
            in H264_ITAGS -> return "h264"
            else -> Unit
        }

        // Fallback: inspect the MediaFormat
        val fmtMime = try { stream.format?.mimeType?.lowercase() ?: "" } catch (_: Exception) { "" }
        val fmtName = try { stream.format?.name?.lowercase() ?: "" } catch (_: Exception) { "" }
        return when {
            "webm" in fmtName || "webm" in fmtMime -> "vp9"
            else -> "h264"
        }
    }

    /**
     * Human-readable codec label from a short codec key.
     * E.g., `"av1"` → `"AV1"`, `"h264"` → `"H264"`.
     */
    fun codecLabelFromKey(key: String): String = when (key) {
        "av1"  -> "AV1"
        "vp9"  -> "VP9"
        "vp8"  -> "VP8"
        "hevc" -> "HEVC"
        "h264" -> "H264"
        else   -> key.uppercase()
    }

    /**
     * Composite key used in `VideoPlayerUiState.streamSizes` and in the
     * download dialog to look up the total size of a (resolution, codec) pair.
     *
     * Format: `"${height}_${codecKey}"`, e.g., `"2160_av1"`, `"1080_vp9"`.
     */
    fun streamSizeKey(height: Int, codecKey: String): String = "${height}_${codecKey}"

    // ─────────────────────────────────────────────────────────────────────────
    fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Check whether MANAGE_EXTERNAL_STORAGE permission has been granted (Android 11+).
     * If not, prompt the user to grant it via Settings — but downloads still work
     * because VideoDownloadManager falls back to app-private storage.
     */
    fun promptStoragePermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val prefs = context.getSharedPreferences("flow_storage_prefs", Context.MODE_PRIVATE)
            val alreadyAsked = prefs.getBoolean("storage_permission_asked", false)
            if (!alreadyAsked) {
                prefs.edit().putBoolean("storage_permission_asked", true).apply()
                Toast.makeText(
                    context,
                    "Grant storage access to save downloads in public folders (optional)",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    if (context is Activity) {
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        if (context is Activity) {
                            context.startActivity(intent)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    fun startDownload(context: Context, video: Video, url: String, qualityLabel: String, audioUrl: String? = null, videoCodec: String? = null) {
        try {
            promptStoragePermissionIfNeeded(context)

            // Start the optimized parallel download service
            com.flow.youtube.data.video.downloader.FlowDownloadService.startDownload(
                context, 
                video, 
                url, 
                qualityLabel,
                audioUrl,
                videoCodec = videoCodec
            )
            
            Toast.makeText(context, "Started download: ${video.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
