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

    fun startDownload(context: Context, video: Video, url: String, qualityLabel: String, audioUrl: String? = null, videoCodec: String? = null, threads: Int? = null) {
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
                threads = threads
            )
            
            Toast.makeText(context, "Started download: ${video.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
