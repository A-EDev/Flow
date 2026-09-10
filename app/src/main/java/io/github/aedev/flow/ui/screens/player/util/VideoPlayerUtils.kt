package io.github.aedev.flow.ui.screens.player.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.VideoCodecUtils
import org.schabi.newpipe.extractor.stream.VideoStream

internal object VideoPlayerUtils {
    fun codecKeyFromMimeType(mimeType: String): String = VideoCodecUtils.codecKeyFromMimeType(mimeType)

    fun codecKeyFromStream(stream: VideoStream): String = VideoCodecUtils.codecKeyFromStream(stream)

    fun codecLabelFromKey(key: String): String = VideoCodecUtils.codecLabelFromKey(key)

    fun qualityHeightFromStream(stream: VideoStream): Int = VideoCodecUtils.qualityHeightFromStream(stream)

    fun streamSizeKey(
        height: Int,
        codecKey: String,
    ): String = VideoCodecUtils.streamSizeKey(height, codecKey)

    // ─────────────────────────────────────────────────────────────────────────

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
                Toast
                    .makeText(
                        context,
                        "Grant storage access to save downloads in public folders (optional)",
                        Toast.LENGTH_LONG,
                    ).show()
                try {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
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
                    } catch (_: Exception) {
                    }
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
        fallbackQuality: String? = null,
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
                fallbackQuality = fallbackQuality,
            )

            Toast.makeText(context, context.getString(R.string.ui_started_download, video.title), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.ui_download_start_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
