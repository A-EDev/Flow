package io.github.aedev.flow.ui.components.shared

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import org.schabi.newpipe.extractor.stream.AudioStream

/**
 * Approximate download size for one quality, or null when no estimate is available — the caller
 * omits the line entirely rather than showing a placeholder.
 */
@Composable
fun approxDownloadSizeLabel(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val context = LocalContext.current
    return stringResource(R.string.download_size_estimate, Formatter.formatShortFileSize(context, bytes))
}

/**
 * Starts an audio-only download for [stream], returning false when the stream carries no URL and
 * nothing was started. Both download dialogs go through here so the storage-permission prompt —
 * which [VideoPlayerUtils.startDownload] already does for a video download — is asked for once on
 * the audio path too.
 */
internal fun startAudioOnlyDownload(
    context: Context,
    video: Video,
    stream: AudioStream,
    threads: Int? = null,
): Boolean {
    val url = stream.getContent().takeIf { it.isNotBlank() } ?: return false
    VideoPlayerUtils.promptStoragePermissionIfNeeded(context)
    FlowDownloadService.startDownload(
        context = context,
        video = video,
        url = url,
        quality = "${DownloadStreamPolicy.audioBitrateKbps(stream)}${context.getString(R.string.kbps)}",
        audioOnly = true,
        audioExtension = DownloadStreamPolicy.audioFileExtension(stream),
        audioMimeType = stream.format?.mimeType,
        threads = threads,
    )
    return true
}
