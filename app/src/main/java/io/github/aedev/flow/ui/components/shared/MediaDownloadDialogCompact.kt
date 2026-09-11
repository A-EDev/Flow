package io.github.aedev.flow.ui.components.shared

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

private const val MIN_THREADS = 1
private const val MAX_THREADS = 8
private val downloadPrefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val UNRANKED_CODEC = 99

private fun containerForCodec(codecKey: String): String =
    when (codecKey) {
        "vp9", "vp8" -> "WebM"
        else -> "MP4"
    }

private fun codecOptionLabel(
    codecKey: String,
    separator: String,
): String = "${VideoPlayerUtils.codecLabelFromKey(codecKey)}$separator${containerForCodec(codecKey)}"

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaDownloadDialogCompact(
    streamInfo: StreamInfo?,
    streamSizes: Map<String, Long>,
    innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
    video: Video,
    currentPlayingHeight: Int = 0,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val separatorDot = stringResource(R.string.list_separator_dot)
    val audioLabelStrings =
        AudioLabelStrings(
            unknownFormat = stringResource(R.string.audio_format_unknown),
            kbps = stringResource(R.string.kbps),
            separator = separatorDot,
        )
    val prefs = remember(context) { PlayerPreferences(context) }
    val preferredLang by prefs.preferredAudioLanguage.collectAsState(initial = "")
    val defaultThreads by prefs.downloadThreads.collectAsState(initial = 3)
    val lastType by prefs.lastDownloadType.collectAsState(initial = null)
    val lastHeight by prefs.lastDownloadHeight.collectAsState(initial = null)
    val lastCodec by prefs.lastDownloadCodec.collectAsState(initial = null)
    val lastAudioLabel by prefs.lastDownloadAudioLabel.collectAsState(initial = null)
    val defaultDownloadCodec by prefs.defaultDownloadCodec.collectAsState(initial = VideoCodec.AUTO)
    val preferredDownloadCodecKey = defaultDownloadCodec.takeIf { it != VideoCodec.AUTO }?.codecKey

    val currentPlayingCodec =
        remember {
            EnhancedPlayerManager
                .getInstance()
                .getPlayer()
                ?.videoFormat
                ?.sampleMimeType
                ?.let { VideoPlayerUtils.codecKeyFromMimeType(it) }
        }

    val videoStreams =
        remember(innerTubeVideoFormats, streamInfo) {
            DownloadStreamPolicy.buildDownloadVideoStreams(
                innerTubeStreams = InnerTubeStreamBridge.convertVideoFormats(innerTubeVideoFormats),
                videoOnlyStreams = streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList(),
                muxedStreams = streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList(),
            )
        }
    val audioStreams =
        remember(innerTubeAudioFormats, streamInfo) {
            DownloadStreamPolicy.mergeAudioDownloadStreams(
                InnerTubeStreamBridge.convertAudioFormats(innerTubeAudioFormats),
                streamInfo?.audioStreams ?: emptyList(),
            )
        }
    val heights =
        remember(videoStreams) {
            videoStreams.map { VideoPlayerUtils.qualityHeightFromStream(it) }.distinct().sortedDescending()
        }

    val hasVideo = videoStreams.isNotEmpty()
    val hasAudio = audioStreams.isNotEmpty()

    var title by remember(video.id) { mutableStateOf(video.title) }
    var threads by remember(defaultThreads) { mutableStateOf(defaultThreads.coerceIn(MIN_THREADS, MAX_THREADS)) }

    var isAudioMode by remember(lastType, hasVideo, hasAudio) {
        mutableStateOf((lastType == "AUDIO" && hasAudio) || !hasVideo)
    }
    var selectedHeight by remember(heights, lastHeight) {
        mutableStateOf(
            heights.firstOrNull { it == lastHeight }
                ?: heights.firstOrNull { it == currentPlayingHeight }
                ?: heights.firstOrNull() ?: 0,
        )
    }
    val codecsForHeight =
        videoStreams
            .filter { VideoPlayerUtils.qualityHeightFromStream(it) == selectedHeight }
            .map { VideoPlayerUtils.codecKeyFromStream(it) }
            .distinct()
            .sortedBy { DownloadStreamPolicy.DOWNLOAD_CODEC_PRIORITY[it] ?: UNRANKED_CODEC }
    var selectedCodec by remember(selectedHeight, lastCodec, preferredDownloadCodecKey) {
        mutableStateOf(
            codecsForHeight.firstOrNull { it == preferredDownloadCodecKey }
                ?: codecsForHeight.firstOrNull { it == lastCodec }
                ?: codecsForHeight.firstOrNull { it == currentPlayingCodec }
                ?: codecsForHeight.firstOrNull() ?: "",
        )
    }
    var selectedAudioIndex by remember(audioStreams, lastAudioLabel) {
        mutableStateOf(
            audioStreams.indexOfFirst { audioOptionLabel(it, audioLabelStrings) == lastAudioLabel }.takeIf { it >= 0 }
                ?: audioStreams.indices.maxByOrNull { DownloadStreamPolicy.audioBitrateKbps(audioStreams[it]) }
                ?: 0,
        )
    }

    val selectedSizeText = approxDownloadSizeLabel(streamSizes[VideoPlayerUtils.streamSizeKey(selectedHeight, selectedCodec)])

    fun confirmDownload() {
        val finalTitle = title.trim().ifBlank { video.title }
        val taggedVideo = video.copy(title = finalTitle)
        if (isAudioMode) {
            val stream = audioStreams.getOrNull(selectedAudioIndex) ?: return
            if (!startAudioOnlyDownload(context, taggedVideo, stream, threads)) return
            Toast
                .makeText(
                    context,
                    context.getString(R.string.downloading_template, audioOptionLabel(stream, audioLabelStrings)),
                    Toast.LENGTH_SHORT,
                ).show()
            downloadPrefsScope.launch {
                prefs.setLastDownloadAudioChoice(audioOptionLabel(stream, audioLabelStrings))
                prefs.setDownloadThreads(threads)
            }
            onDismiss()
            return
        }

        val stream =
            videoStreams.firstOrNull {
                VideoPlayerUtils.qualityHeightFromStream(it) == selectedHeight &&
                    VideoPlayerUtils.codecKeyFromStream(it) == selectedCodec
            } ?: return
        val downloadUrl = stream.getContent().takeIf { it.isNotBlank() } ?: return
        val codecLabel = VideoPlayerUtils.codecLabelFromKey(selectedCodec)
        val qualityLabel = "$codecLabel ${selectedHeight}p"

        var audioUrl: String? = null
        if (stream.isVideoOnly) {
            val compatible = DownloadStreamPolicy.pickCompatibleAudioForVideo(selectedCodec, audioStreams, preferredLang)
            audioUrl = compatible?.getContent()?.takeIf { it.isNotBlank() }
            if (audioUrl == null) {
                Toast.makeText(context, context.getString(R.string.download_no_compatible_audio), Toast.LENGTH_LONG).show()
                return
            }
        }

        var fallbackUrl: String? = null
        var fallbackAudioUrl: String? = null
        var fallbackCodec: String? = null
        var fallbackQuality: String? = null
        if (selectedCodec == "av1") {
            val fb =
                videoStreams.firstOrNull {
                    VideoPlayerUtils.qualityHeightFromStream(it) == selectedHeight &&
                        VideoPlayerUtils.codecKeyFromStream(it) != "av1"
                }
            val fbUrl = fb?.getContent()?.takeIf { it.isNotBlank() }
            if (fb != null && fbUrl != null) {
                val fbCodecKey = VideoPlayerUtils.codecKeyFromStream(fb)
                val fbAudio =
                    if (fb.isVideoOnly) {
                        DownloadStreamPolicy
                            .pickCompatibleAudioForVideo(fbCodecKey, audioStreams, preferredLang)
                            ?.getContent()
                            ?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                if (!fb.isVideoOnly || fbAudio != null) {
                    fallbackUrl = fbUrl
                    fallbackAudioUrl = fbAudio
                    fallbackCodec =
                        when (fbCodecKey) {
                            "vp9", "vp8" -> fbCodecKey
                            else -> null
                        }
                    fallbackQuality = "${VideoPlayerUtils.codecLabelFromKey(fbCodecKey)} ${selectedHeight}p"
                }
            }
        }

        VideoPlayerUtils.startDownload(
            context = context,
            video = taggedVideo,
            url = downloadUrl,
            qualityLabel = qualityLabel,
            audioUrl = audioUrl,
            videoCodec =
                when (selectedCodec) {
                    "vp9", "vp8", "av1" -> selectedCodec
                    else -> null
                },
            threads = threads,
            fallbackUrl = fallbackUrl,
            fallbackAudioUrl = fallbackAudioUrl,
            fallbackCodec = fallbackCodec,
            fallbackQuality = fallbackQuality,
        )
        Toast.makeText(context, context.getString(R.string.downloading_template, qualityLabel), Toast.LENGTH_SHORT).show()
        downloadPrefsScope.launch {
            prefs.setLastDownloadVideoChoice(selectedHeight, selectedCodec)
            prefs.setDownloadThreads(threads)
        }
        onDismiss()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.download_video),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.download_title_label)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                if (hasVideo && hasAudio) {
                    FlowConnectedToggleGroup(
                        options =
                            listOf(
                                FlowToggleOption(
                                    value = false,
                                    label = stringResource(R.string.video),
                                    icon = Icons.Outlined.VideoLibrary,
                                ),
                                FlowToggleOption(
                                    value = true,
                                    label = stringResource(R.string.download_audio),
                                    icon = Icons.Outlined.MusicNote,
                                ),
                            ),
                        selected = isAudioMode,
                        onSelected = { isAudioMode = it },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (!isAudioMode && hasVideo) {
                    DownloadDropdownRow(
                        label = stringResource(R.string.quality),
                        value = listOfNotNull("${selectedHeight}p", selectedSizeText).joinToString(separatorDot),
                        options =
                            heights.map { h ->
                                "${h}p" to { selectedHeight = h }
                            },
                    )
                    Spacer(Modifier.height(10.dp))
                    DownloadDropdownRow(
                        label = stringResource(R.string.download_format_label),
                        value = codecOptionLabel(selectedCodec, separatorDot),
                        options =
                            codecsForHeight.map { codec ->
                                codecOptionLabel(codec, separatorDot) to { selectedCodec = codec }
                            },
                    )
                } else if (isAudioMode && hasAudio) {
                    DownloadDropdownRow(
                        label = stringResource(R.string.download_audio),
                        value = audioStreams.getOrNull(selectedAudioIndex)?.let { audioOptionLabel(it, audioLabelStrings) } ?: "",
                        options =
                            audioStreams.mapIndexed { index, stream ->
                                audioOptionLabel(stream, audioLabelStrings) to { selectedAudioIndex = index }
                            },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_download_streams),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.download_threads_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    val currentThreads = threads.coerceIn(MIN_THREADS, MAX_THREADS)
                    val haptics = LocalHapticFeedback.current
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(
                                if (currentThreads > MIN_THREADS) HapticFeedbackType.SegmentTick else HapticFeedbackType.Reject,
                            )
                            threads = (currentThreads - 1).coerceAtLeast(MIN_THREADS)
                        },
                        enabled = currentThreads > MIN_THREADS,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.download_threads_decrease))
                    }
                    Text(
                        text = currentThreads.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(
                                if (currentThreads < MAX_THREADS) HapticFeedbackType.SegmentTick else HapticFeedbackType.Reject,
                            )
                            threads = (currentThreads + 1).coerceAtMost(MAX_THREADS)
                        },
                        enabled = currentThreads < MAX_THREADS,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.download_threads_increase))
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    val confirmHaptics = LocalHapticFeedback.current
                    Button(
                        onClick = {
                            confirmHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            confirmDownload()
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = (isAudioMode && hasAudio) || (!isAudioMode && hasVideo && selectedCodec.isNotEmpty()),
                    ) { Text(stringResource(R.string.download)) }
                }
            }
        }
    }
}

private data class AudioLabelStrings(
    val unknownFormat: String,
    val kbps: String,
    val separator: String,
)

private fun audioOptionLabel(
    stream: AudioStream,
    strings: AudioLabelStrings,
): String {
    val format = DownloadStreamPolicy.audioFormatLabel(stream, strings.unknownFormat)
    val bitrate = DownloadStreamPolicy.audioBitrateKbps(stream)
    val lang = DownloadStreamPolicy.audioLanguageLabel(stream)
    return listOfNotNull("$format${strings.separator}$bitrate${strings.kbps}", lang).joinToString(strings.separator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadDropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optLabel, onSelect) ->
                DropdownMenuItem(
                    text = { Text(optLabel, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect()
                        expanded = false
                    },
                )
            }
        }
    }
}
