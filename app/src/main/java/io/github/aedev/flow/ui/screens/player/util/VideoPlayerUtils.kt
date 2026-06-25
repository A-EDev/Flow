package io.github.aedev.flow.ui.screens.player.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.VideoCodecUtils
import org.schabi.newpipe.extractor.stream.VideoStream

object VideoPlayerUtils {

    fun codecKeyFromMimeType(mimeType: String): String {
        return VideoCodecUtils.codecKeyFromMimeType(mimeType)
    }

    fun codecKeyFromStream(stream: VideoStream): String {
        return VideoCodecUtils.codecKeyFromStream(stream)
    }

    fun codecLabelFromKey(key: String): String = VideoCodecUtils.codecLabelFromKey(key)

    fun qualityHeightFromStream(stream: VideoStream): Int = VideoCodecUtils.qualityHeightFromStream(stream)

    fun qualityLabelFromStream(stream: VideoStream): String = VideoCodecUtils.qualityLabelFromStream(stream)

    /**
     * Composite key used in `VideoPlayerUiState.streamSizes` and in the
     * download dialog to look up the total size of a (resolution, codec) pair.
     *
     * Format: `"${height}_${codecKey}"`, e.g., `"2160_av1"`, `"1080_vp9"`.
     */
    fun streamSizeKey(height: Int, codecKey: String): String = "${height}_${codecKey}"

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Format a millisecond duration as `H:MM:SS` or `M:SS`.
     * @param padMinutes when true the no-hours form is zero-padded (`MM:SS`), matching the
     *   look used by the main on-video controls; otherwise minutes are unpadded (`M:SS`).
     */
    fun formatTime(timeMs: Long, padMinutes: Boolean = false): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else if (padMinutes) {
            String.format("%02d:%02d", minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Format a playback speed as a compact label, e.g. `2x`, `1.5x`, `0.75x`.
     * Trailing zeros are trimmed and the value is clamped to `0.1..maxSpeed`.
     */
    fun formatSpeedLabel(speed: Float, maxSpeed: Float = 10.0f): String {
        val clamped = speed.coerceIn(0.1f, maxSpeed)
        return if (kotlin.math.abs(clamped - clamped.toInt()) < 0.01f) {
            "${clamped.toInt()}x"
        } else {
            val rounded = kotlin.math.round(clamped * 100f) / 100f
            "${rounded.toString().trimEnd('0').trimEnd('.')}x"
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

    fun startDownload(
        context: Context,
        video: Video,
        url: String,
        qualityLabel: String,
        audioUrl: String? = null,
        videoCodec: String? = null,
        threads: Int? = null,
        fallbackUrl: String? = null,
        fallbackAudioUrl: String? = null,
        fallbackCodec: String? = null,
        fallbackQuality: String? = null
    ) {
        try {
            promptStoragePermissionIfNeeded(context)

            // Start the optimized parallel download service
            io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                context,
                video,
                url,
                qualityLabel,
                audioUrl,
                videoCodec = videoCodec,
                threads = threads,
                fallbackUrl = fallbackUrl,
                fallbackAudioUrl = fallbackAudioUrl,
                fallbackCodec = fallbackCodec,
                fallbackQuality = fallbackQuality
            )

            Toast.makeText(context, "Started download: ${video.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
