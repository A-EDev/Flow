package io.github.aedev.flow.ui.screens.player.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.*
import io.github.aedev.flow.ui.components.SubtitleCustomizer
import io.github.aedev.flow.ui.components.SubtitleStyle
import io.github.aedev.flow.ui.components.rememberFlowSheetState
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
                    DownloadStreamHelpers.mergeAudioDownloadStreams(innerTubeAudioStreams, streamInfo?.audioStreams ?: emptyList())

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
                            val bitrate = DownloadStreamHelpers.audioBitrateKbps(audioStream)
                            val audioFormat = DownloadStreamHelpers.audioFormatLabel(audioStream)
                            val audioUrl = audioStream.getContent().takeIf { it.isNotBlank() }
                            val languageLabel = DownloadStreamHelpers.audioLanguageLabel(audioStream)
                            val trackTypeLabel = DownloadStreamHelpers.audioTrackTypeLabel(audioStream)

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
                                            audioExtension = DownloadStreamHelpers.audioFileExtension(audioStream),
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
