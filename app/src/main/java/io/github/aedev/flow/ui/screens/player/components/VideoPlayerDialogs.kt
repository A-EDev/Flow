package io.github.aedev.flow.ui.screens.player.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.*
import io.github.aedev.flow.ui.components.SubtitleCustomizer
import io.github.aedev.flow.ui.components.SubtitleStyle
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import org.schabi.newpipe.extractor.stream.VideoStream

@Composable
fun DownloadQualityDialog(
    streamInfo: org.schabi.newpipe.extractor.stream.StreamInfo?,
    streamSizes: Map<String, Long>,
    innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    video: Video,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioLangPref = remember(context) { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val preferredLang by audioLangPref.preferredAudioLanguage.collectAsState(initial = "")
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.download_video),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.select_quality),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val innerTubeVideoStreams = remember(innerTubeVideoFormats) {
                    io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertVideoFormats(innerTubeVideoFormats)
                }
                val innerTubeAudioStreams = remember(innerTubeAudioFormats) {
                    io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertAudioFormats(innerTubeAudioFormats)
                }

                val extractedVideoOnlyStreams = streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList()
                val extractedMuxedStreams = streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList()
                val videoOnlyStreams = innerTubeVideoStreams + extractedVideoOnlyStreams
                val muxedStreams = extractedMuxedStreams
                val effectiveAudioForDownload: List<org.schabi.newpipe.extractor.stream.AudioStream> =
                    mergeAudioDownloadStreams(innerTubeAudioStreams, streamInfo?.audioStreams ?: emptyList())

                val codecPriority = mapOf("vp9" to 0, "h264" to 1, "av1" to 2, "vp8" to 3, "hevc" to 4)
                val distinctStreams = (videoOnlyStreams + muxedStreams)
                    .distinctBy {
                        "${VideoPlayerUtils.qualityHeightFromStream(it)}_${VideoPlayerUtils.codecKeyFromStream(it)}"
                    }
                    .sortedWith(
                        compareByDescending<VideoStream> { VideoPlayerUtils.qualityHeightFromStream(it) }
                            .thenBy { codecPriority[VideoPlayerUtils.codecKeyFromStream(it)] ?: 99 }
                    )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
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
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 Text("Try SABR Download")
                             }
                         }
                    }

                    items(distinctStreams) { stream ->
                        val codecKey   = VideoPlayerUtils.codecKeyFromStream(stream)
                        val codecLabel = VideoPlayerUtils.codecLabelFromKey(codecKey)
                        val qualityHeight = VideoPlayerUtils.qualityHeightFromStream(stream)
                        val qualityLabel = "$codecLabel ${qualityHeight}p"

                        val sizeInBytes = streamSizes[VideoPlayerUtils.streamSizeKey(qualityHeight, codecKey)]
                        val sizeText = if (sizeInBytes != null && sizeInBytes > 0) {
                            val mb = sizeInBytes / (1024.0 * 1024.0)
                            String.format("~%.2f MB", mb)
                        } else null

                        // Resolution badge
                        val resBadge = when {
                            qualityHeight >= 2160 -> "4K"
                            qualityHeight >= 1440 -> "2K"
                            qualityHeight >= 1080 -> "HD"
                            else                  -> null
                        }

                        Surface(
                            onClick = downloadVideo@{
                                onDismiss()
                                val downloadUrl = stream.getContent().takeIf { it.isNotBlank() }
                                if (downloadUrl != null) {
                                    var audioUrl: String? = null
                                    if (stream.isVideoOnly) {
                                        val isMp4Container = codecKey == "h264" || codecKey == "hevc"
                                        val allAudio = effectiveAudioForDownload

                                        fun isAacCompatible(a: org.schabi.newpipe.extractor.stream.AudioStream): Boolean {
                                            val fmt  = (a.format?.name ?: "").lowercase()
                                            val mime = (a.format?.mimeType ?: "").lowercase()
                                            return !fmt.contains("opus") && !fmt.contains("vorbis") &&
                                                   !fmt.contains("webm") && !mime.contains("opus") &&
                                                   !mime.contains("vorbis") && !mime.contains("webm")
                                        }

                                        val langFilteredAudio = if (!preferredLang.isNullOrEmpty() && preferredLang != "original") {
                                            val langMatches = allAudio.filter {
                                                it.audioLocale?.language.equals(preferredLang, ignoreCase = true) ||
                                                it.audioLocale?.toLanguageTag().equals(preferredLang, ignoreCase = true)
                                            }
                                            if (langMatches.isNotEmpty()) langMatches else allAudio
                                        } else {
                                            val originals = allAudio.filter {
                                                it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                                            }
                                            if (originals.isNotEmpty()) originals else {
                                                val nonDubbed = allAudio.filter {
                                                    it.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED
                                                }
                                                if (nonDubbed.isNotEmpty()) nonDubbed else allAudio
                                            }
                                        }

                                        val compatibleAudio = if (isMp4Container) {
                                            val aacAudio =
                                                langFilteredAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                                                ?: allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                                            if (aacAudio == null && codecKey == "av1")
                                                langFilteredAudio.maxByOrNull { it.bitrate }
                                                    ?: allAudio.maxByOrNull { it.bitrate }
                                            else aacAudio
                                        } else {
                                            val opusFilter: (org.schabi.newpipe.extractor.stream.AudioStream) -> Boolean = { a ->
                                                val fmt  = a.format?.name ?: ""
                                                val mime = a.format?.mimeType ?: ""
                                                fmt.contains("webm", true) || mime.contains("audio/webm", true) ||
                                                fmt.contains("opus", true) || mime.contains("opus", true)
                                            }
                                            langFilteredAudio.filter(opusFilter).maxByOrNull { it.bitrate }
                                                ?: allAudio.filter(opusFilter).maxByOrNull { it.bitrate }
                                        }

                                        if (compatibleAudio == null) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No compatible audio stream — download cannot proceed",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            return@downloadVideo
                                        }
                                        audioUrl = compatibleAudio.getContent().takeIf { it.isNotBlank() }
                                    }

                                    VideoPlayerUtils.startDownload(
                                        context, video, downloadUrl, qualityLabel, audioUrl,
                                        videoCodec = when (codecKey) {
                                            "vp9", "vp8", "av1" -> codecKey
                                            else -> null
                                        }
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.downloading_template, qualityLabel),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qualityLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (sizeText != null) {
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (resBadge != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = when (resBadge) {
                                            "4K" -> MaterialTheme.colorScheme.tertiary
                                            "2K" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = resBadge,
                                            color = MaterialTheme.colorScheme.surface,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
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
                                text = "Audio Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        
                        items(audioStreams) { audioStream ->
                            val bitrate = audioBitrateKbps(audioStream)
                            val audioFormat = audioFormatLabel(audioStream)
                            val audioUrl = audioStream.getContent().takeIf { it.isNotBlank() }
                            val languageLabel = audioLanguageLabel(audioStream)
                            val trackTypeLabel = audioTrackTypeLabel(audioStream)

                            Surface(
                                onClick = {
                                    onDismiss()
                                    if (audioUrl != null) {
                                        io.github.aedev.flow.data.video.downloader.FlowDownloadService.startDownload(
                                            context = context,
                                            video = video,
                                            url = audioUrl,
                                            quality = "${bitrate}kbps",
                                            audioOnly = true,
                                            audioExtension = audioFileExtension(audioStream),
                                            audioMimeType = audioStream.format?.mimeType
                                        )
                                        Toast.makeText(context, "Downloading audio: ${bitrate}kbps $audioFormat", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$audioFormat ${bitrate}kbps",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = listOfNotNull(languageLabel, trackTypeLabel, "Audio only").joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

private fun mergeAudioDownloadStreams(
    innerTubeStreams: List<org.schabi.newpipe.extractor.stream.AudioStream>,
    extractorStreams: List<org.schabi.newpipe.extractor.stream.AudioStream>
): List<org.schabi.newpipe.extractor.stream.AudioStream> {
    return (innerTubeStreams + extractorStreams)
        .filter { it.getContent().isNotBlank() }
        .distinctBy { stream ->
            listOf(
                audioFormatLabel(stream),
                audioBitrateKbps(stream).toString(),
                stream.audioTrackId.orEmpty(),
                stream.audioLocale?.toLanguageTag().orEmpty(),
                stream.audioTrackType?.name.orEmpty()
            ).joinToString("|")
        }
        .sortedWith(
            compareBy<org.schabi.newpipe.extractor.stream.AudioStream> { audioFormatSortRank(it) }
                .thenByDescending { audioBitrateKbps(it) }
                .thenBy { it.audioLocale?.displayLanguage.orEmpty() }
        )
}

private fun audioBitrateKbps(stream: org.schabi.newpipe.extractor.stream.AudioStream): Int {
    val raw = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
    return if (raw > 1000) raw / 1000 else raw.coerceAtLeast(0)
}

private fun audioFormatLabel(stream: org.schabi.newpipe.extractor.stream.AudioStream): String {
    val mime = stream.format?.mimeType.orEmpty().lowercase()
    val name = stream.format?.name.orEmpty().lowercase()
    return when {
        "opus" in mime || "opus" in name -> "OPUS"
        "webm" in mime || "webm" in name -> "WEBM"
        "mp4" in mime || "m4a" in name -> "M4A"
        "mpeg" in mime || "mp3" in name -> "MP3"
        name.isNotBlank() -> name.uppercase()
        else -> "Audio"
    }
}

private fun audioFileExtension(stream: org.schabi.newpipe.extractor.stream.AudioStream): String {
    val mime = stream.format?.mimeType.orEmpty().lowercase()
    val name = stream.format?.name.orEmpty().lowercase()
    return when {
        "webm" in mime || "webm" in name -> "webm"
        "ogg" in mime || "opus" in name -> "ogg"
        "mpeg" in mime || "mp3" in name -> "mp3"
        else -> "m4a"
    }
}

private fun audioLanguageLabel(stream: org.schabi.newpipe.extractor.stream.AudioStream): String? {
    return stream.audioTrackName?.takeIf { it.isNotBlank() }
        ?: stream.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
        ?: stream.audioTrackId?.takeIf { it.isNotBlank() }
}

private fun audioTrackTypeLabel(stream: org.schabi.newpipe.extractor.stream.AudioStream): String? {
    return when (stream.audioTrackType) {
        org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL -> "Original"
        org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED -> "Dubbed"
        null -> null
        else -> stream.audioTrackType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
    }
}

private fun audioFormatSortRank(stream: org.schabi.newpipe.extractor.stream.AudioStream): Int {
    return when (audioFormatLabel(stream)) {
        "OPUS", "WEBM" -> 0
        "M4A" -> 1
        "MP3" -> 2
        else -> 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelectorDialog(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    currentQualityKey: String?,
    onDismiss: () -> Unit,
    onQualitySelected: (QualityOption) -> Unit,
    onBack: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Text(
                    text = stringResource(R.string.video_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            LazyColumn {
                items(availableQualities.sortedByDescending { it.height }) { quality ->
                    val isSelected = quality.isSelected(currentQuality, currentQualityKey)
                    Surface(
                        onClick = { onQualitySelected(quality); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (quality.height == 0) stringResource(R.string.quality_auto) else quality.displayLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSelectorDialog(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onDismiss: () -> Unit,
    onTrackSelected: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Text(
                    text = stringResource(R.string.audio_track),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            LazyColumn {
                items(availableAudioTracks.size) { index ->
                    val track = availableAudioTracks[index]
                    val isSelected = index == currentAudioTrack
                    Surface(
                        onClick = { onTrackSelected(index); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (track.language.isNotBlank()) {
                                    Text(
                                        text = track.language,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSelectorDialog(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int, String) -> Unit,
    onDisableSubtitles: () -> Unit,
    onShowStyleCustomizer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Text(
                    text = stringResource(R.string.filter_subtitles),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onShowStyleCustomizer) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
            }
            HorizontalDivider()
            LazyColumn {
                // Off option
                item {
                    val isSelected = !subtitlesEnabled
                    Surface(
                        onClick = { onDisableSubtitles(); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.off),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                // Available subtitles
                items(availableSubtitles.size) { index ->
                    val subtitle = availableSubtitles[index]
                    val isSelected = subtitle.url == selectedSubtitleUrl && subtitlesEnabled
                    Surface(
                        onClick = { onSubtitleSelected(index, subtitle.url); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subtitle.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (subtitle.language.isNotBlank()) {
                                    Text(
                                        text = subtitle.language,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenuDialog(
    playerState: EnhancedPlayerState,
    autoplayEnabled: Boolean,
    subtitlesEnabled: Boolean,
    onDismiss: () -> Unit,
    initialPage: PlayerSettingsPage = PlayerSettingsPage.Main,
    onQualitySelected: (QualityOption) -> Unit = {},
    onAudioTrackSelected: (Int) -> Unit = {},
    onSpeedSelected: (Float) -> Unit = {},
    selectedSubtitleUrl: String? = null,
    onSubtitleSelected: (Int, String) -> Unit = { _, _ -> },
    onDisableSubtitles: () -> Unit = {},
    onAutoplayToggle: (Boolean) -> Unit,
    onSkipSilenceToggle: (Boolean) -> Unit,
    onStableVolumeToggle: (Boolean) -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onCastClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    expandedHeight: Dp? = null,
    enableVerticalDismiss: Boolean = true,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val dismissThresholdPx = expandedHeightPx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(initialPage) }
    val currentTitle = when (currentPage) {
        PlayerSettingsPage.Main -> stringResource(R.string.player_settings)
        PlayerSettingsPage.Quality -> stringResource(R.string.video_quality_title)
        PlayerSettingsPage.Speed -> stringResource(R.string.playback_speed)
        PlayerSettingsPage.Audio -> stringResource(R.string.audio_track)
        PlayerSettingsPage.Subtitles -> stringResource(R.string.filter_subtitles)
    }

    fun animateToExpanded() {
        if (!enableVerticalDismiss) {
            coroutineScope.launch { sheetHeightPx.snapTo(expandedHeightPx) }
            return
        }
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun animateToDismiss(afterDismiss: () -> Unit = {}) {
        if (isAnimatingOut) return
        if (!enableVerticalDismiss) {
            latestOnDismiss()
            afterDismiss()
            return
        }
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            latestOnDismiss()
            afterDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = 0f, upperBound = expandedHeightPx)
        if (!enableVerticalDismiss) {
            sheetHeightPx.snapTo(expandedHeightPx)
            return@LaunchedEffect
        }
        if (sheetHeightPx.value == 0f) {
            sheetHeightPx.snapTo(0f)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(initialPage) {
        currentPage = initialPage
    }

    BackHandler(onBack = {
        if (currentPage == PlayerSettingsPage.Main) {
            animateToDismiss()
        } else {
            currentPage = PlayerSettingsPage.Main
        }
    })

    val headerDragModifier = if (enableVerticalDismiss) Modifier.pointerInput(expandedHeightPx, dismissThresholdPx, isAnimatingOut) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (isAnimatingOut) return@detectVerticalDragGestures
                velocityTracker.addPointerInputChange(change)
                coroutineScope.launch {
                    val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(0f, expandedHeightPx)
                    sheetHeightPx.snapTo(nextValue)
                }
            },
            onDragCancel = {
                velocityTracker.resetTracking()
                if (!isAnimatingOut) animateToExpanded()
            },
            onDragEnd = {
                val velocityY = velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                when {
                    velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                    else -> animateToExpanded()
                }
            }
        )
    } else Modifier

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // ── Sheet title ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(headerDragModifier),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(headerDragModifier)
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage != PlayerSettingsPage.Main) {
                    IconButton(
                        onClick = { currentPage = PlayerSettingsPage.Main },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { animateToDismiss() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

            // ── Quality ──
            when (currentPage) {
                PlayerSettingsPage.Main -> {
            PlayerSettingsSectionHeader(stringResource(R.string.video))
            PlayerSettingsNavRow(
                icon = Icons.Filled.HighQuality,
                label = stringResource(R.string.quality),
                value = if (playerState.currentQuality == 0) stringResource(R.string.quality_auto)
                        else "${playerState.currentQuality}p",
                onClick = {
                    currentPage = PlayerSettingsPage.Quality
                }
            )

            // ── Playback Speed ──
            PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.playback_speed),
                value = if (playerState.playbackSpeed == 1.0f) stringResource(R.string.normal)
                        else "${playerState.playbackSpeed}x",
                onClick = {
                    currentPage = PlayerSettingsPage.Speed
                }
            )

            // ── Audio Track ──
            PlayerSettingsSectionHeader(stringResource(R.string.audio_settings_title))
            PlayerSettingsNavRow(
                icon = Icons.Filled.AudioFile,
                label = stringResource(R.string.audio_track),
                value = "Track ${playerState.currentAudioTrack + 1}",
                onClick = {
                    currentPage = PlayerSettingsPage.Audio
                }
            )

            // ── Captions ──
            PlayerSettingsSectionHeader(stringResource(R.string.captions))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Subtitles,
                label = stringResource(R.string.filter_subtitles),
                value = if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off),
                onClick = {
                    currentPage = PlayerSettingsPage.Subtitles
                }
            )

            PlayerSettingsNavRow(
                icon = Icons.Filled.Tune,
                label = stringResource(R.string.subtitle_style),
                value = "",
                onClick = { onShowSubtitleStyle() }
            )

            // ── Cast to TV ──
            PlayerSettingsSectionHeader(stringResource(R.string.player_settings_overlay_controls))
            PlayerSettingsNavRow(
                icon = Icons.Filled.Cast,
                label = stringResource(R.string.cast_to_tv),
                value = "",
                onClick = { animateToDismiss(onCastClick) }
            )

            // ── Picture-in-Picture ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.PictureInPicture,
                label = stringResource(R.string.pip_mode),
                value = "",
                onClick = { animateToDismiss(onPipClick) }
            )

            // ── Sleep Timer ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.Bedtime,
                label = stringResource(R.string.sleep_timer),
                value = "",
                onClick = { animateToDismiss(onSleepTimerClick) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── Loop Video ──
            PlayerSettingsSectionHeader(stringResource(R.string.playback_header))
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.Repeat,
                label = stringResource(R.string.loop_video),
                checked = playerState.isLooping,
                onToggle = onLoopToggle
            )

            // ── Autoplay ──
            PlayerSettingsToggleRow(
                icon = Icons.Filled.SkipNext,
                label = stringResource(R.string.autoplay_next),
                checked = autoplayEnabled,
                enabled = !playerState.isLooping,
                onToggle = onAutoplayToggle
            )

            // ── Skip Silence ──
            PlayerSettingsSectionHeader(stringResource(R.string.audio_effects))
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.player_settings_skip_silence),
                checked = playerState.isSkipSilenceEnabled,
                onToggle = onSkipSilenceToggle
            )

            // ── Stable Voice ──
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.VolumeUp,
                label = stringResource(R.string.player_settings_stable_voice),
                checked = playerState.isStableVolumeEnabled,
                onToggle = onStableVolumeToggle
            )
                }
                PlayerSettingsPage.Quality -> PlayerSettingsQualityPage(
                    availableQualities = playerState.availableQualities,
                    currentQuality = playerState.currentQuality,
                    currentQualityKey = playerState.currentQualityKey,
                    onQualitySelected = {
                        onQualitySelected(it)
                        currentPage = PlayerSettingsPage.Main
                    }
                )
                PlayerSettingsPage.Speed -> PlayerSettingsSpeedPage(
                    currentSpeed = playerState.playbackSpeed,
                    onSpeedSelected = {
                        onSpeedSelected(it)
                        currentPage = PlayerSettingsPage.Main
                    }
                )
                PlayerSettingsPage.Audio -> PlayerSettingsAudioPage(
                    availableAudioTracks = playerState.availableAudioTracks,
                    currentAudioTrack = playerState.currentAudioTrack,
                    onTrackSelected = {
                        onAudioTrackSelected(it)
                        currentPage = PlayerSettingsPage.Main
                    }
                )
                PlayerSettingsPage.Subtitles -> PlayerSettingsSubtitlesPage(
                    availableSubtitles = playerState.availableSubtitles,
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    subtitlesEnabled = subtitlesEnabled,
                    onSubtitleSelected = { index, url ->
                        onSubtitleSelected(index, url)
                        currentPage = PlayerSettingsPage.Main
                    },
                    onDisableSubtitles = {
                        onDisableSubtitles()
                        currentPage = PlayerSettingsPage.Main
                    },
                    onShowStyleCustomizer = {
                        currentPage = PlayerSettingsPage.Main
                        animateToDismiss(onShowSubtitleStyle)
                    }
                )
            }
            }
        }
    }
}
}

enum class PlayerSettingsPage {
    Main,
    Quality,
    Speed,
    Audio,
    Subtitles
}

@Composable
private fun PlayerSettingsQualityPage(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    currentQualityKey: String?,
    onQualitySelected: (QualityOption) -> Unit
) {
    availableQualities.sortedByDescending { it.height }.forEach { quality ->
        val isSelected = quality.isSelected(currentQuality, currentQualityKey)
        PlayerSettingsSelectionRow(
            label = if (quality.height == 0) stringResource(R.string.quality_auto) else quality.displayLabel(),
            selected = isSelected,
            onClick = { onQualitySelected(quality) }
        )
    }
}

private fun QualityOption.displayLabel(): String {
    return label.takeIf { it.isNotBlank() } ?: "${height}p"
}

private fun QualityOption.isSelected(currentQuality: Int, currentQualityKey: String?): Boolean {
    return if (height == 0) {
        currentQuality == 0
    } else {
        streamKey != null && streamKey == currentQualityKey
    }
}

@Composable
private fun PlayerSettingsSpeedPage(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val context = LocalContext.current
    val playerPrefs = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val customSpeedsEnabled by playerPrefs.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPrefs.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPrefs.speedSliderEnabled.collectAsState(initial = false)
    val defaultSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
    val speeds = if (customSpeedsEnabled && customSpeedPresetsRaw.isNotBlank()) {
        customSpeedPresetsRaw
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in 0.1f..10.0f }
            .sortedBy { it }
            .ifEmpty { defaultSpeeds }
    } else {
        defaultSpeeds
    }

    if (speedSliderEnabled) {
        SpeedSliderContent(
            currentSpeed = currentSpeed,
            onSpeedSelected = onSpeedSelected
        )
    } else {
        speeds.forEach { speed ->
            PlayerSettingsSelectionRow(
                label = if (speed == 1.0f) stringResource(R.string.normal) else "${speed}x",
                selected = speed == currentSpeed,
                onClick = { onSpeedSelected(speed) }
            )
        }
    }
}

@Composable
private fun PlayerSettingsAudioPage(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onTrackSelected: (Int) -> Unit
) {
    availableAudioTracks.forEachIndexed { index, track ->
        PlayerSettingsSelectionRow(
            label = track.label,
            supportingText = track.language.takeIf { it.isNotBlank() },
            selected = index == currentAudioTrack,
            onClick = { onTrackSelected(index) }
        )
    }
}

@Composable
private fun PlayerSettingsSubtitlesPage(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onSubtitleSelected: (Int, String) -> Unit,
    onDisableSubtitles: () -> Unit,
    onShowStyleCustomizer: () -> Unit
) {
    PlayerSettingsSelectionRow(
        label = stringResource(R.string.off),
        selected = !subtitlesEnabled,
        onClick = onDisableSubtitles
    )
    availableSubtitles.forEachIndexed { index, subtitle ->
        PlayerSettingsSelectionRow(
            label = subtitle.label,
            supportingText = subtitle.language.takeIf { it.isNotBlank() },
            selected = subtitle.url == selectedSubtitleUrl && subtitlesEnabled,
            onClick = { onSubtitleSelected(index, subtitle.url) }
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    PlayerSettingsNavRow(
        icon = Icons.Filled.Tune,
        label = stringResource(R.string.subtitle_style),
        value = "",
        onClick = onShowStyleCustomizer
    )
}

@Composable
private fun PlayerSettingsSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun PlayerSettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun PlayerSettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerSettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { if (enabled) onToggle(!checked) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSelectorDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val playerPrefs = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val customSpeedsEnabled by playerPrefs.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPrefs.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPrefs.speedSliderEnabled.collectAsState(initial = false)
    val defaultSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)
    val speeds = if (customSpeedsEnabled && customSpeedPresetsRaw.isNotBlank()) {
        val custom = customSpeedPresetsRaw
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in 0.1f..10.0f }
            .sortedBy { it }
        custom.ifEmpty { defaultSpeeds }
    } else {
        defaultSpeeds
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Text(
                    text = stringResource(R.string.playback_speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            if (speedSliderEnabled) {
                SpeedSliderContent(
                    currentSpeed = currentSpeed,
                    onSpeedSelected = { onSpeedSelected(it); onDismiss() }
                )
            } else {
                LazyColumn {
                    items(speeds) { speed ->
                        val isSelected = speed == currentSpeed
                        Surface(
                            onClick = { onSpeedSelected(speed); onDismiss() },
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (speed == 1.0f) stringResource(R.string.normal) else "${speed}x",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quadratic slider mapping: provides finer control near 1.0x and coarser at extremes.
 */
private fun sliderToSpeed(progress: Float): Float {
    val center = 0.5f
    return if (progress <= center) {
        val t = progress / center          
        0.1f + t * t * (1.0f - 0.1f)
    } else {
        val t = (progress - center) / center
        1.0f + t * t * (4.0f - 1.0f)
    }
}

private fun speedToSlider(speed: Float): Float {
    val clamped = speed.coerceIn(0.1f, 4.0f)
    return if (clamped <= 1.0f) {
        val t = kotlin.math.sqrt((clamped - 0.1f) / (1.0f - 0.1f))
        t * 0.5f
    } else {
        val t = kotlin.math.sqrt((clamped - 1.0f) / (4.0f - 1.0f))
        0.5f + t * 0.5f
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpeedSliderContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val quickPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val step = 0.05f
    var sliderProgress by remember { mutableStateOf(speedToSlider(currentSpeed)) }
    val displaySpeed = sliderToSpeed(sliderProgress)
    val roundedSpeed = (kotlin.math.round(displaySpeed.toDouble() / step) * step)
        .toFloat().coerceIn(0.1f, 4.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current speed display
        Text(
            text = if (roundedSpeed == 1.0f) stringResource(R.string.normal)
                   else "${"%.2f".format(roundedSpeed)}×",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        // Slider with − / + buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    val newSpeed = (roundedSpeed - step).coerceIn(0.1f, 4.0f)
                    sliderProgress = speedToSlider(newSpeed)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.speed_slider_decrease)
                )
            }
            Slider(
                value = sliderProgress,
                onValueChange = { sliderProgress = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val newSpeed = (roundedSpeed + step).coerceIn(0.1f, 4.0f)
                    sliderProgress = speedToSlider(newSpeed)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.speed_slider_increase)
                )
            }
        }

        // Range labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0.1×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "4.0×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // Quick-tap preset chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            quickPresets.forEach { preset ->
                val isActive = kotlin.math.abs(roundedSpeed - preset) < 0.01f
                FilterChip(
                    selected = isActive,
                    onClick = { sliderProgress = speedToSlider(preset) },
                    label = {
                        Text(
                            text = if (preset == 1.0f) stringResource(R.string.normal)
                                   else "${preset}×"
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Confirm + Reset row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { sliderProgress = speedToSlider(1.0f) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.speed_slider_reset))
            }
            Button(
                onClick = { onSpeedSelected(roundedSpeed) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.speed_slider_apply))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleCustomizerDialog(
    subtitleStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                
                    Text(
                        text = stringResource(R.string.filter_subtitles),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                SubtitleCustomizer(
                    currentStyle = subtitleStyle,
                    onStyleChange = onStyleChange
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

private suspend fun trySabrDownloadFromDialog(context: Context, video: Video) {
    try {
        Toast.makeText(context, "Trying SABR download...", Toast.LENGTH_SHORT).show()
        val sabrInfo = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8000L) {
                val playerResponse = YouTube.player(video.id, client = YouTubeClient.ANDROID)
                    .getOrNull() ?: return@withTimeoutOrNull null
                SabrUrlResolver.resolve(playerResponse)
            }
        }
        if (sabrInfo != null) {
            val codecHint = if (sabrInfo.videoItag in listOf(313, 271, 308, 248, 303, 247, 302, 244, 243, 242)) "vp9" else null
            io.github.aedev.flow.data.video.downloader.FlowDownloadService.startSabrDownload(
                context = context,
                video = video,
                quality = "best",
                sabrStreamingUrl = sabrInfo.streamingUrl,
                audioItag = sabrInfo.audioItag,
                audioLmt = sabrInfo.audioLmt,
                videoItag = sabrInfo.videoItag,
                videoLmt = sabrInfo.videoLmt,
                durationMs = sabrInfo.durationMs,
                videoCodec = codecHint
            )
            Toast.makeText(context, "SABR download started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No download source available", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "SABR download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
