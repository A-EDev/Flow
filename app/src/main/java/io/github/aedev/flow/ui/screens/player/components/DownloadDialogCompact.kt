package io.github.aedev.flow.ui.screens.player.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

private const val MIN_THREADS = 1
private const val MAX_THREADS = 8

private val CODEC_PRIORITY = mapOf("vp9" to 0, "h264" to 1, "av1" to 2, "vp8" to 3, "hevc" to 4)

private fun containerForCodec(codecKey: String): String = when (codecKey) {
    "vp9", "vp8" -> "WebM"
    else -> "MP4"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityDialogCompact(
    streamInfo: StreamInfo?,
    streamSizes: Map<String, Long>,
    innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
    video: Video,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { PlayerPreferences(context) }
    val preferredLang by prefs.preferredAudioLanguage.collectAsState(initial = "")
    val defaultThreads by prefs.downloadThreads.collectAsState(initial = 3)
    val lastType by prefs.lastDownloadType.collectAsState(initial = null)
    val lastHeight by prefs.lastDownloadHeight.collectAsState(initial = null)
    val lastCodec by prefs.lastDownloadCodec.collectAsState(initial = null)
    val lastAudioLabel by prefs.lastDownloadAudioLabel.collectAsState(initial = null)

    val videoStreams = remember(innerTubeVideoFormats, streamInfo) {
        val itVideo = InnerTubeStreamBridge.convertVideoFormats(innerTubeVideoFormats)
        val exVideoOnly = streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList()
        val exMuxed = streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList()
        (itVideo + exVideoOnly + exMuxed)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { "${VideoPlayerUtils.qualityHeightFromStream(it)}_${VideoPlayerUtils.codecKeyFromStream(it)}" }
            .sortedWith(
                compareByDescending<VideoStream> { VideoPlayerUtils.qualityHeightFromStream(it) }
                    .thenBy { CODEC_PRIORITY[VideoPlayerUtils.codecKeyFromStream(it)] ?: 99 }
            )
    }
    val audioStreams = remember(innerTubeAudioFormats, streamInfo) {
        DownloadStreamHelpers.mergeAudioDownloadStreams(
            InnerTubeStreamBridge.convertAudioFormats(innerTubeAudioFormats),
            streamInfo?.audioStreams ?: emptyList()
        )
    }
    val heights = remember(videoStreams) {
        videoStreams.map { VideoPlayerUtils.qualityHeightFromStream(it) }.distinct().sortedDescending()
    }

    val hasVideo = videoStreams.isNotEmpty()
    val hasAudio = audioStreams.isNotEmpty()

    var title by remember(video.id) { mutableStateOf(video.title) }
    var threads by remember { mutableStateOf(-1) }
    LaunchedEffect(defaultThreads) { if (threads < MIN_THREADS) threads = defaultThreads.coerceIn(MIN_THREADS, MAX_THREADS) }

    var isAudioMode by remember(lastType, hasVideo, hasAudio) {
        mutableStateOf((lastType == "AUDIO" && hasAudio) || !hasVideo)
    }
    var selectedHeight by remember(heights, lastHeight) {
        mutableStateOf(heights.firstOrNull { it == lastHeight } ?: heights.firstOrNull() ?: 0)
    }
    val codecsForHeight = videoStreams
        .filter { VideoPlayerUtils.qualityHeightFromStream(it) == selectedHeight }
        .map { VideoPlayerUtils.codecKeyFromStream(it) }
        .distinct()
        .sortedBy { CODEC_PRIORITY[it] ?: 99 }
    var selectedCodec by remember(selectedHeight, lastCodec) {
        mutableStateOf(codecsForHeight.firstOrNull { it == lastCodec } ?: codecsForHeight.firstOrNull() ?: "")
    }
    var selectedAudioIndex by remember(audioStreams, lastAudioLabel) {
        mutableStateOf(
            audioStreams.indexOfFirst { audioOptionLabel(it) == lastAudioLabel }.takeIf { it >= 0 } ?: 0
        )
    }

    fun sizeTextFor(height: Int, codec: String): String? {
        val bytes = streamSizes[VideoPlayerUtils.streamSizeKey(height, codec)] ?: return null
        if (bytes <= 0) return null
        return String.format("~%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun confirmDownload() {
        val finalTitle = title.trim().ifBlank { video.title }
        val taggedVideo = video.copy(title = finalTitle)
        if (isAudioMode) {
            val stream = audioStreams.getOrNull(selectedAudioIndex) ?: return
            val url = stream.getContent().takeIf { it.isNotBlank() } ?: return
            val bitrate = DownloadStreamHelpers.audioBitrateKbps(stream)
            VideoPlayerUtils.promptStoragePermissionIfNeeded(context)
            io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                context = context,
                video = taggedVideo,
                url = url,
                quality = "${bitrate}kbps",
                audioOnly = true,
                audioExtension = DownloadStreamHelpers.audioFileExtension(stream),
                audioMimeType = stream.format?.mimeType,
                threads = threads
            )
            Toast.makeText(context, context.getString(R.string.downloading_template, audioOptionLabel(stream)), Toast.LENGTH_SHORT).show()
            scope.launch {
                prefs.setLastDownloadAudioChoice(audioOptionLabel(stream))
                prefs.setDownloadThreads(threads)
            }
            onDismiss()
            return
        }

        val stream = videoStreams.firstOrNull {
            VideoPlayerUtils.qualityHeightFromStream(it) == selectedHeight &&
                VideoPlayerUtils.codecKeyFromStream(it) == selectedCodec
        } ?: return
        val downloadUrl = stream.getContent().takeIf { it.isNotBlank() } ?: return
        val codecLabel = VideoPlayerUtils.codecLabelFromKey(selectedCodec)
        val qualityLabel = "$codecLabel ${selectedHeight}p"

        var audioUrl: String? = null
        if (stream.isVideoOnly) {
            val compatible = DownloadStreamHelpers.pickCompatibleAudioForVideo(selectedCodec, audioStreams, preferredLang)
            audioUrl = compatible?.getContent()?.takeIf { it.isNotBlank() }
            if (audioUrl == null) {
                Toast.makeText(context, context.getString(R.string.download_no_compatible_audio), Toast.LENGTH_LONG).show()
                return
            }
        }

        VideoPlayerUtils.startDownload(
            context = context,
            video = taggedVideo,
            url = downloadUrl,
            qualityLabel = qualityLabel,
            audioUrl = audioUrl,
            videoCodec = when (selectedCodec) {
                "vp9", "vp8", "av1" -> selectedCodec
                else -> null
            },
            threads = threads
        )
        Toast.makeText(context, context.getString(R.string.downloading_template, qualityLabel), Toast.LENGTH_SHORT).show()
        scope.launch {
            prefs.setLastDownloadVideoChoice(selectedHeight, selectedCodec)
            prefs.setDownloadThreads(threads)
        }
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.download_video),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
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
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                if (hasVideo && hasAudio) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isAudioMode,
                            onClick = { isAudioMode = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.video)) }
                        SegmentedButton(
                            selected = isAudioMode,
                            onClick = { isAudioMode = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.download_audio)) }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (!isAudioMode && hasVideo) {
                    DownloadDropdownRow(
                        label = stringResource(R.string.quality),
                        value = "${selectedHeight}p" + (sizeTextFor(selectedHeight, selectedCodec)?.let { "  ·  $it" } ?: ""),
                        options = heights.map { h ->
                            "${h}p" to { selectedHeight = h }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    DownloadDropdownRow(
                        label = stringResource(R.string.download_format_label),
                        value = "${VideoPlayerUtils.codecLabelFromKey(selectedCodec)} · ${containerForCodec(selectedCodec)}",
                        options = codecsForHeight.map { codec ->
                            "${VideoPlayerUtils.codecLabelFromKey(codec)} · ${containerForCodec(codec)}" to { selectedCodec = codec }
                        }
                    )
                } else if (isAudioMode && hasAudio) {
                    DownloadDropdownRow(
                        label = stringResource(R.string.download_audio),
                        value = audioStreams.getOrNull(selectedAudioIndex)?.let { audioOptionLabel(it) } ?: "",
                        options = audioStreams.mapIndexed { index, stream ->
                            audioOptionLabel(stream) to { selectedAudioIndex = index }
                        }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_download_streams),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.download_threads_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    val currentThreads = threads.coerceIn(MIN_THREADS, MAX_THREADS)
                    FilledIconButton(
                        onClick = { threads = (currentThreads - 1).coerceAtLeast(MIN_THREADS) },
                        enabled = currentThreads > MIN_THREADS,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Icon(Icons.Default.Remove, contentDescription = "-") }
                    Text(
                        text = currentThreads.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    FilledIconButton(
                        onClick = { threads = (currentThreads + 1).coerceAtMost(MAX_THREADS) },
                        enabled = currentThreads < MAX_THREADS,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Icon(Icons.Default.Add, contentDescription = "+") }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { confirmDownload() },
                        enabled = (isAudioMode && hasAudio) || (!isAudioMode && hasVideo && selectedCodec.isNotEmpty())
                    ) { Text(stringResource(R.string.download)) }
                }
            }
        }
    }
}

private fun audioOptionLabel(stream: AudioStream): String {
    val format = DownloadStreamHelpers.audioFormatLabel(stream)
    val bitrate = DownloadStreamHelpers.audioBitrateKbps(stream)
    val lang = DownloadStreamHelpers.audioLanguageLabel(stream)
    val base = "$format · ${bitrate}kbps"
    return if (lang != null) "$base · $lang" else base
}

@Composable
private fun DownloadDropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(88.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = value, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optLabel, onSelect) ->
                    DropdownMenuItem(
                        text = { Text(optLabel) },
                        onClick = {
                            onSelect()
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
