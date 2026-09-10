package io.github.aedev.flow.ui.components.shared

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.*
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.VideoStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadDialog(
    streamInfo: org.schabi.newpipe.extractor.stream.StreamInfo?,
    streamSizes: Map<String, Long>,
    innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audioLangPref =
        remember(context) {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val preferredLang by audioLangPref.preferredAudioLanguage.collectAsState(initial = "")

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.download_video),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.select_quality),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                val innerTubeVideoStreams =
                    remember(innerTubeVideoFormats) {
                        io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                            .convertVideoFormats(innerTubeVideoFormats)
                    }
                val innerTubeAudioStreams =
                    remember(innerTubeAudioFormats) {
                        io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                            .convertAudioFormats(innerTubeAudioFormats)
                    }

                val effectiveAudioForDownload: List<org.schabi.newpipe.extractor.stream.AudioStream> =
                    DownloadStreamPolicy.mergeAudioDownloadStreams(innerTubeAudioStreams, streamInfo?.audioStreams ?: emptyList())

                val distinctStreams =
                    DownloadStreamPolicy.buildDownloadVideoStreams(
                        innerTubeStreams = innerTubeVideoStreams,
                        videoOnlyStreams = streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList(),
                        muxedStreams = streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList(),
                    )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    if (distinctStreams.isEmpty()) {
                        item {
                            Text(stringResource(R.string.no_download_streams), modifier = Modifier.padding(16.dp))
                        }
                        item {
                            val scope = rememberCoroutineScope()
                            Button(
                                onClick = {
                                    onDismiss()
                                    scope.launch {
                                        trySabrDownloadFromDialog(context, video)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.ui_try_sabr_download))
                            }
                        }
                    }

                    items(distinctStreams) { stream ->
                        val codecKey = VideoPlayerUtils.codecKeyFromStream(stream)
                        val codecLabel = VideoPlayerUtils.codecLabelFromKey(codecKey)
                        val qualityHeight = VideoPlayerUtils.qualityHeightFromStream(stream)
                        val qualityLabel = "$codecLabel ${qualityHeight}p"

                        val sizeText =
                            approxDownloadSizeLabel(streamSizes[VideoPlayerUtils.streamSizeKey(qualityHeight, codecKey)])

                        val resBadge =
                            when {
                                qualityHeight >= 2160 -> R.string.filter_4k to MaterialTheme.colorScheme.tertiary
                                qualityHeight >= 1440 -> R.string.quality_badge_2k to MaterialTheme.colorScheme.secondary
                                qualityHeight >= 1080 -> R.string.filter_hd to MaterialTheme.colorScheme.primary
                                else -> null
                            }

                        Surface(
                            onClick = downloadVideo@{
                                onDismiss()
                                val downloadUrl = stream.getContent().takeIf { it.isNotBlank() }
                                if (downloadUrl != null) {
                                    var audioUrl: String? = null
                                    if (stream.isVideoOnly) {
                                        val compatibleAudio =
                                            DownloadStreamPolicy.pickCompatibleAudioForVideo(
                                                videoCodecKey = codecKey,
                                                allAudio = effectiveAudioForDownload,
                                                preferredLang = preferredLang,
                                            )
                                        if (compatibleAudio == null) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.download_no_compatible_audio),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            return@downloadVideo
                                        }
                                        audioUrl = compatibleAudio.getContent().takeIf { it.isNotBlank() }
                                    }

                                    VideoPlayerUtils.startDownload(
                                        context,
                                        video,
                                        downloadUrl,
                                        qualityLabel,
                                        audioUrl,
                                        videoCodec =
                                            when (codecKey) {
                                                "vp9", "vp8", "av1" -> codecKey
                                                else -> null
                                            },
                                    )
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.downloading_template, qualityLabel),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qualityLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (sizeText != null) {
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (resBadge != null) {
                                    val (badgeLabel, badgeColor) = resBadge
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = badgeColor,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ) {
                                        Text(
                                            text = stringResource(badgeLabel),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== Audio-Only Section =====
                    val audioStreams = effectiveAudioForDownload.sortedByDescending { it.averageBitrate }
                    if (audioStreams.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ui_audio_only),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }

                        items(audioStreams) { audioStream ->
                            val bitrate = DownloadStreamPolicy.audioBitrateKbps(audioStream)
                            val bitrateLabel = "$bitrate${stringResource(R.string.kbps)}"
                            val audioFormat =
                                DownloadStreamPolicy.audioFormatLabel(audioStream, stringResource(R.string.audio_format_unknown))
                            val languageLabel = DownloadStreamPolicy.audioLanguageLabel(audioStream)
                            val trackTypeLabel =
                                DownloadStreamPolicy.audioTrackTypeLabel(
                                    stream = audioStream,
                                    originalLabel = stringResource(R.string.audio_track_original),
                                    dubbedLabel = stringResource(R.string.audio_track_dubbed),
                                )

                            Surface(
                                onClick = {
                                    onDismiss()
                                    if (startAudioOnlyDownload(context, video, audioStream)) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.ui_download_audio, bitrate, audioFormat),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.secondary,
                                                    CircleShape,
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$audioFormat $bitrateLabel",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text =
                                                listOfNotNull(languageLabel, trackTypeLabel, stringResource(R.string.ui_audio_only))
                                                    .joinToString(stringResource(R.string.list_separator_dot)),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

private suspend fun trySabrDownloadFromDialog(
    context: Context,
    video: Video,
) {
    try {
        Toast.makeText(context, context.getString(R.string.toast_trying_sabr_download), Toast.LENGTH_SHORT).show()
        val sabrInfo =
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(8000L) {
                    val playerResponse =
                        YouTube
                            .player(video.id, client = YouTubeClient.ANDROID)
                            .getOrNull() ?: return@withTimeoutOrNull null
                    SabrUrlResolver.resolve(playerResponse)
                }
            }
        if (sabrInfo != null) {
            val codecHint = if (sabrInfo.videoItag in listOf(313, 271, 308, 248, 303, 247, 302, 244, 243, 242)) "vp9" else null
            io.github.aedev.flow.data.video.downloader.FlowDownloadService.startSabrDownload(
                context = context,
                video = video,
                quality = context.getString(R.string.download_quality_best),
                sabrStreamingUrl = sabrInfo.streamingUrl,
                audioItag = sabrInfo.audioItag,
                audioLmt = sabrInfo.audioLmt,
                videoItag = sabrInfo.videoItag,
                videoLmt = sabrInfo.videoLmt,
                poToken = sabrInfo.poToken,
                visitorId = sabrInfo.visitorId,
                ustreamerConfig = sabrInfo.ustreamerConfig,
                durationMs = sabrInfo.durationMs,
                videoCodec = codecHint,
            )
            Toast.makeText(context, context.getString(R.string.toast_sabr_download_started), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_no_download_source), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_sabr_download_failed, e.message), Toast.LENGTH_SHORT).show()
    }
}
