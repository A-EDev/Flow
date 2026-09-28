package io.github.aedev.flow.ui.components.shared

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts the downloads both download dialogs offer, asking for storage access once on the way. */
internal object DownloadLauncher {
    // The dialog is dismissed before the SABR session resolves, so the work cannot live in its scope.
    private val sabrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private const val STORAGE_PREFS = "flow_storage_prefs"
    private const val STORAGE_PERMISSION_ASKED = "storage_permission_asked"

    /**
     * Asks once for MANAGE_EXTERNAL_STORAGE on Android 11+. Optional: downloads still work without
     * it because VideoDownloadManager falls back to app-private storage.
     */
    fun promptStoragePermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) return
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(STORAGE_PERMISSION_ASKED, false)) return
        prefs.edit { putBoolean(STORAGE_PERMISSION_ASKED, true) }
        Toast.makeText(context, context.getString(R.string.download_storage_access_prompt), Toast.LENGTH_LONG).show()
        if (context !is Activity) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                },
            )
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }

    fun startVideoDownload(
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
            FlowDownloadService.startDownload(
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

    /** Starts an audio-only download for [format], returning false when it carries no URL and nothing started. */
    fun startAudioOnlyDownload(
        context: Context,
        video: Video,
        format: PlayerResponse.StreamingData.Format,
        threads: Int? = null,
    ): Boolean {
        val url = format.url?.takeIf { it.isNotBlank() } ?: return false
        promptStoragePermissionIfNeeded(context)
        FlowDownloadService.startDownload(
            context = context,
            video = video,
            url = url,
            quality = "${DownloadStreamPolicy.audioBitrateKbps(format)}${context.getString(R.string.kbps)}",
            audioOnly = true,
            audioExtension = DownloadStreamPolicy.audioFileExtension(format),
            audioMimeType = DownloadStreamPolicy.audioContainerMimeType(format),
            threads = threads,
        )
        return true
    }

    /** The dialog's last resort when no direct format is offered: the same SABR session a failed download retries with. */
    fun startSabrDownload(
        context: Context,
        video: Video,
    ) {
        val appContext = context.applicationContext
        Toast.makeText(appContext, appContext.getString(R.string.toast_trying_sabr_download), Toast.LENGTH_SHORT).show()
        sabrScope.launch {
            try {
                val sabrInfo = InnerTubeVideoStreamExtractor.resolveSabrDownload(videoId = video.id)
                if (sabrInfo == null) {
                    Toast.makeText(appContext, appContext.getString(R.string.toast_no_download_source), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val codecKey = VideoCodecUtils.codecKeyFromMimeType(sabrInfo.videoMimeType)
                FlowDownloadService.startSabrDownload(
                    context = appContext,
                    video = video,
                    quality = appContext.getString(R.string.download_quality_best),
                    sabrStreamingUrl = sabrInfo.streamingUrl,
                    audioItag = sabrInfo.audioItag,
                    audioLmt = sabrInfo.audioLmt,
                    videoItag = sabrInfo.videoItag,
                    videoLmt = sabrInfo.videoLmt,
                    poToken = sabrInfo.poToken,
                    visitorId = sabrInfo.visitorId,
                    ustreamerConfig = sabrInfo.ustreamerConfig,
                    durationMs = sabrInfo.durationMs,
                    videoCodec = codecKey.takeIf { it == "vp9" || it == "vp8" || it == "av1" },
                    audioMimeType = sabrInfo.audioMimeType.takeIf { it.isNotBlank() },
                )
                Toast.makeText(appContext, appContext.getString(R.string.toast_sabr_download_started), Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(appContext, appContext.getString(R.string.toast_sabr_download_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
