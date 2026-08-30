package io.github.aedev.flow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.utils.DateContext

@Composable
fun MediaInfoDialog(
    track: MusicTrack? = null,
    video: Video? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mediaInfo by remember { mutableStateOf<io.github.aedev.flow.innertube.models.MediaInfo?>(null) }
    var resolvedDurationSeconds by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(track, video) {
        isLoading = true
        resolvedDurationSeconds =
            track?.duration?.takeIf { it > 0 }
                ?: video?.duration?.takeIf { it > 0 }
        val videoId = track?.videoId ?: video?.id
        if (videoId != null) {
            mediaInfo =
                io.github.aedev.flow.data.newmusic.InnertubeMusicService
                    .getMediaInfo(videoId)
            resolvedDurationSeconds =
                mediaInfo?.durationSeconds?.takeIf { it > 0 }
                    ?: resolvedDurationSeconds
                    ?: io.github.aedev.flow.data.music.YouTubeMusicService
                        .fetchVideoDuration(videoId)
                        .takeIf { it > 0 }
        }
        isLoading = false
    }

    val info = mediaInfo
    val currentTitle = track?.title ?: video?.title
    val currentArtist = track?.artist ?: video?.channelName
    val currentId = track?.videoId ?: video?.id
    val titleLabel = stringResource(R.string.title_label)
    val artistLabel = stringResource(R.string.artist_label)
    val albumLabel = stringResource(R.string.album_label)
    val viewsLabel = stringResource(R.string.views)
    val likesLabel = stringResource(R.string.likes)
    val dislikesLabel = stringResource(R.string.dislikes)
    val subscribersLabel = stringResource(R.string.subscribers)
    val videoIdLabel = stringResource(R.string.video_id_label)
    val channelIdLabel = stringResource(R.string.channel_id)
    val uploadedLabel = stringResource(R.string.uploaded)
    val dateSettings = rememberDateDisplaySettings()
    val itagLabel = stringResource(R.string.itag)
    val mimeTypeLabel = stringResource(R.string.mime_type)
    val bitrateLabel = stringResource(R.string.bitrate_label)
    val kbpsUnit = stringResource(R.string.kbps)
    val sampleRateLabel = stringResource(R.string.sample_rate_label)
    val hzUnit = stringResource(R.string.hz)
    val fileSizeLabel = stringResource(R.string.file_size)
    val qualityLabel = stringResource(R.string.quality)
    val durationLabel = stringResource(R.string.duration)
    val unknownText = stringResource(R.string.unknown)
    val details =
        remember(
            info,
            track,
            video,
            resolvedDurationSeconds,
            titleLabel,
            artistLabel,
            albumLabel,
            viewsLabel,
            likesLabel,
            dislikesLabel,
            subscribersLabel,
            videoIdLabel,
            channelIdLabel,
            uploadedLabel,
            itagLabel,
            mimeTypeLabel,
            bitrateLabel,
            kbpsUnit,
            sampleRateLabel,
            hzUnit,
            fileSizeLabel,
            qualityLabel,
            durationLabel,
            unknownText,
            dateSettings,
        ) {
            buildList {
                add(titleLabel to (info?.title ?: currentTitle))
                add(artistLabel to (info?.author ?: currentArtist))
                track?.album?.takeIf { it.isNotEmpty() }?.let { add(albumLabel to it) }

                when {
                    info?.viewCount != null -> add(viewsLabel to info.viewCount.toString())
                    track != null && track.views > 0 -> add(viewsLabel to track.views.toString())
                    video != null -> add(viewsLabel to video.viewCount.toString())
                }
                info?.like?.let { add(likesLabel to it.toString()) }
                    ?: video?.likeCount?.let { add(likesLabel to it.toString()) }
                info?.dislike?.let { add(dislikesLabel to it.toString()) }
                info?.subscribers?.let { add(subscribersLabel to it) }

                add(videoIdLabel to currentId)
                info?.authorId?.let { add(channelIdLabel to it) }
                    ?: track?.channelId?.takeIf { it.isNotEmpty() }?.let { add(channelIdLabel to it) }

                when {
                    info?.uploadDate != null -> {
                        add(uploadedLabel to dateSettings.format(info.uploadDate, DateContext.WATCH))
                    }

                    video?.uploadDate != null -> {
                        add(uploadedLabel to dateSettings.format(video.uploadDate, DateContext.WATCH, video.timestamp))
                    }
                }

                info?.videoId_tag?.let { add(itagLabel to it.toString()) }
                info?.mimeType?.let { add(mimeTypeLabel to it) }
                info?.bitrate?.let { add(bitrateLabel to "${it / 1000} $kbpsUnit") }
                info?.sampleRate?.let { add(sampleRateLabel to "$it $hzUnit") }
                info?.contentLength?.let { add(fileSizeLabel to formatFileSize(it.toLongOrNull(), unknownText)) }
                info?.qualityLabel?.let { add(qualityLabel to it) }
                resolvedDurationSeconds?.takeIf { it > 0 }?.let { add(durationLabel to formatDuration(it)) }
            }.filter { it.second != null }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = BottomSheetDefaults.ExpandedShape,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.track_info),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.track_info_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(
                    items = details,
                    key = { it.first },
                ) { (label, value) ->
                    InfoItem(label = label, value = value.orEmpty())
                }

                if (isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard, label)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.btn_copy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private fun formatFileSize(
    bytes: Long?,
    unknownText: String,
): String {
    if (bytes == null) return unknownText
    val mb = bytes / (1024.0 * 1024.0)
    return "%.2f MB".format(mb)
}
