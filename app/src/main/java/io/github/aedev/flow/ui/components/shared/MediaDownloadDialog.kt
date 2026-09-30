package io.github.aedev.flow.ui.components.shared

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.github.aedev.flow.data.video.downloader.request.DownloadSubtitle
import io.github.aedev.flow.player.state.SubtitleOption
import io.github.aedev.flow.player.stream.VideoCodecUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadDialog(
    streamSizes: Map<String, Long>,
    innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    video: Video,
    subtitles: List<SubtitleOption> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audioLangPref =
        remember(context) {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val preferredLang by audioLangPref.preferredAudioLanguage.collectAsState(initial = "")
    val lastSubtitleLanguage by audioLangPref.lastDownloadSubtitleLanguage.collectAsState(initial = null)
    val subtitleOptions = remember(subtitles) { downloadableSubtitles(subtitles) }
    var subtitleChoice by remember(subtitleOptions, lastSubtitleLanguage) {
        mutableStateOf(defaultDownloadSubtitle(subtitleOptions, lastSubtitleLanguage))
    }

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
                modifier =
                    Modifier.padding(
                        start = FlowDialogDefaults.ContentPadding,
                        top = FlowDialogDefaults.ContentPadding,
                        end = FlowDialogDefaults.ContentPadding,
                        bottom = FlowDialogDefaults.BottomPadding,
                    ),
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

                if (subtitleOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DownloadSubtitleRow(
                        options = subtitleOptions,
                        selected = subtitleChoice,
                        onSelect = { subtitleChoice = it },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val effectiveAudioForDownload =
                    remember(innerTubeAudioFormats) { DownloadStreamPolicy.buildDownloadAudioFormats(innerTubeAudioFormats) }
                val audioFormats =
                    remember(effectiveAudioForDownload) {
                        effectiveAudioForDownload.sortedByDescending { it.averageBitrate ?: it.bitrate }
                    }
                val distinctFormats =
                    remember(innerTubeVideoFormats) { DownloadStreamPolicy.buildDownloadVideoFormats(innerTubeVideoFormats) }
                val hdrLabel = stringResource(R.string.download_quality_hdr)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    if (distinctFormats.isEmpty()) {
                        item {
                            Text(stringResource(R.string.no_download_streams), modifier = Modifier.padding(16.dp))
                        }
                        item {
                            Button(
                                onClick = {
                                    onDismiss()
                                    DownloadLauncher.startDefaultDownload(context, video)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.ui_try_sabr_download))
                            }
                        }
                    }

                    itemsIndexed(distinctFormats) { streamIndex, format ->
                        val codecKey = DownloadStreamPolicy.videoCodecKey(format)
                        val qualityHeight = DownloadStreamPolicy.videoHeight(format)
                        val qualityLabel = DownloadStreamPolicy.videoQualityLabel(format, hdrLabel)

                        val sizeText =
                            approxDownloadSizeLabel(streamSizes[VideoCodecUtils.streamSizeKey(qualityHeight, codecKey)])

                        val resBadge =
                            when {
                                qualityHeight >= 2160 -> R.string.filter_4k
                                qualityHeight >= 1440 -> R.string.quality_badge_2k
                                qualityHeight >= 1080 -> R.string.filter_hd
                                else -> null
                            }

                        Surface(
                            onClick = downloadVideo@{
                                onDismiss()
                                val compatibleAudio =
                                    DownloadStreamPolicy.pickAacAudio(
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
                                DownloadLauncher.startVideoDownload(
                                    context,
                                    video,
                                    format,
                                    compatibleAudio,
                                    subtitle =
                                        subtitleChoice?.toDownloadSubtitle() ?: DownloadSubtitle.OFF.takeIf {
                                            subtitleOptions.isNotEmpty()
                                        },
                                )
                                if (subtitleOptions.isNotEmpty()) rememberDownloadSubtitleChoice(audioLangPref, subtitleChoice)
                            },
                            shape = flowRowGroupShape(streamIndex, distinctFormats.size),
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MediaTextBadge(
                                        text = stringResource(resBadge),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // ===== Audio-Only Section =====
                    if (audioFormats.isNotEmpty()) {
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

                        itemsIndexed(audioFormats) { audioIndex, audioStream ->
                            val bitrate = DownloadStreamPolicy.audioBitrateKbps(audioStream)
                            val bitrateLabel = "$bitrate${stringResource(R.string.kbps)}"
                            val audioFormat =
                                DownloadStreamPolicy.audioFormatLabel(audioStream, stringResource(R.string.audio_format_unknown))
                            val languageLabel = DownloadStreamPolicy.audioLanguageLabel(audioStream)
                            val trackTypeLabel =
                                DownloadStreamPolicy.audioTrackTypeLabel(
                                    format = audioStream,
                                    originalLabel = stringResource(R.string.audio_track_original),
                                    dubbedLabel = stringResource(R.string.audio_track_dubbed),
                                )

                            Surface(
                                onClick = {
                                    onDismiss()
                                    if (DownloadLauncher.startAudioOnlyDownload(context, video, audioStream)) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.ui_download_audio, bitrate, audioFormat),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                shape = flowRowGroupShape(audioIndex, audioFormats.size),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                    CircleShape,
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
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

                Spacer(modifier = Modifier.height(FlowDialogDefaults.ActionsSpacing))

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
