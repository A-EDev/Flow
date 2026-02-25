package com.flow.youtube.ui.screens.player.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flow.youtube.data.local.VideoQuality
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.*
import com.flow.youtube.ui.components.SubtitleCustomizer
import com.flow.youtube.ui.components.SubtitleStyle
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.util.VideoPlayerUtils
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import org.schabi.newpipe.extractor.stream.VideoStream

@Composable
fun DownloadQualityDialog(
    uiState: VideoPlayerUiState,
    video: Video,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioLangPref = remember(context) { com.flow.youtube.data.local.PlayerPreferences(context) }
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
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    val videoOnlyStreams = uiState.streamInfo?.videoOnlyStreams
                        ?.filterIsInstance<VideoStream>() ?: emptyList()
                    val muxedStreams = uiState.streamInfo?.videoStreams
                        ?.filterIsInstance<VideoStream>() ?: emptyList()

                    val codecPriority = mapOf("av1" to 0, "vp9" to 1, "h264" to 2, "hevc" to 3, "vp8" to 4)
                    val distinctStreams = (videoOnlyStreams + muxedStreams)
                        .distinctBy { "${it.height}_${VideoPlayerUtils.codecKeyFromStream(it)}" }
                        .sortedWith(
                            compareByDescending<VideoStream> { it.height }
                                .thenBy { codecPriority[VideoPlayerUtils.codecKeyFromStream(it)] ?: 99 }
                        )

                    if (distinctStreams.isEmpty()) {
                         item {
                             Text(stringResource(R.string.no_download_streams), modifier = Modifier.padding(16.dp))
                         }
                    }

                    items(distinctStreams) { stream ->
                        val codecKey   = VideoPlayerUtils.codecKeyFromStream(stream)
                        val codecLabel = VideoPlayerUtils.codecLabelFromKey(codecKey)
                        val qualityLabel = "$codecLabel ${stream.height}p"

                        val sizeInBytes = uiState.streamSizes[VideoPlayerUtils.streamSizeKey(stream.height, codecKey)]
                        val sizeText = if (sizeInBytes != null && sizeInBytes > 0) {
                            val mb = sizeInBytes / (1024.0 * 1024.0)
                            String.format("~%.2f MB", mb)
                        } else null

                        // Resolution badge
                        val resBadge = when {
                            stream.height >= 2160 -> "4K"
                            stream.height >= 1440 -> "2K"
                            stream.height >= 1080 -> "HD"
                            else                  -> null
                        }

                        Surface(
                            onClick = {
                                onDismiss()
                                val downloadUrl = stream.content ?: stream.url
                                if (downloadUrl != null) {
                                    var audioUrl: String? = null
                                    if (stream.isVideoOnly) {
                                        val isMp4Container = codecKey != "vp9" && codecKey != "vp8"
                                        val allAudio = uiState.streamInfo?.audioStreams ?: emptyList()

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
                                            langFilteredAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                                                ?: allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                                        } else {
                                            langFilteredAudio.filter { a ->
                                                val fmt  = a.format?.name ?: ""
                                                val mime = a.format?.mimeType ?: ""
                                                fmt.contains("webm", true) || mime.contains("audio/webm", true) ||
                                                fmt.contains("opus", true) || mime.contains("opus", true)
                                            }.maxByOrNull { it.bitrate }
                                                ?: allAudio.maxByOrNull { it.bitrate }
                                        }

                                        if (compatibleAudio == null && isMp4Container) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No compatible audio stream — download cannot proceed",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            return@Surface
                                        }
                                        audioUrl = compatibleAudio?.let { it.content ?: it.url }
                                    }

                                    VideoPlayerUtils.startDownload(context, video, downloadUrl, qualityLabel, audioUrl)
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
                    val audioStreams = uiState.streamInfo?.audioStreams?.sortedByDescending { it.averageBitrate } ?: emptyList()
                    if (audioStreams.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Audio Only (MP3/M4A)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        
                        // Show best audio stream
                        val bestAudio = audioStreams.first()
                        item {
                            val bitrate = bestAudio.averageBitrate / 1000
                            val audioFormat = bestAudio.format?.name ?: "M4A"
                            val audioUrl = bestAudio.url

                            Surface(
                                onClick = {
                                    onDismiss()
                                    if (audioUrl != null) {
                                        com.flow.youtube.data.video.downloader.FlowDownloadService.startDownload(
                                            context = context,
                                            video = video,
                                            url = audioUrl,
                                            quality = "${bitrate}kbps",
                                            audioOnly = true
                                        )
                                        Toast.makeText(context, "Downloading audio: ${bitrate}kbps", Toast.LENGTH_SHORT).show()
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
                                            text = "Audio ${bitrate}kbps",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$audioFormat • Audio only",
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
fun QualitySelectorDialog(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.video_quality_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            LazyColumn {
                items(availableQualities.sortedByDescending { it.height }) { quality ->
                    val isSelected = quality.height == currentQuality
                    Surface(
                        onClick = { onQualitySelected(quality.height); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                                text = quality.label,
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
    onTrackSelected: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.audio_track),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            LazyColumn {
                items(availableAudioTracks.size) { index ->
                    val track = availableAudioTracks[index]
                    val isSelected = index == currentAudioTrack
                    Surface(
                        onClick = { onTrackSelected(index); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
    onDisableSubtitles: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.filter_subtitles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            LazyColumn {
                // Off option
                item {
                    val isSelected = !subtitlesEnabled
                    Surface(
                        onClick = { onDisableSubtitles(); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
    onShowQuality: () -> Unit,
    onShowAudio: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowSubtitles: () -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onSkipSilenceToggle: (Boolean) -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onLoopToggle: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // ── Sheet title ──
            Text(
                text = stringResource(R.string.player_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()

            // ── Quality ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.HighQuality,
                label = stringResource(R.string.quality),
                value = if (playerState.currentQuality == 0) stringResource(R.string.quality_auto)
                        else "${playerState.currentQuality}p",
                onClick = { onDismiss(); onShowQuality() }
            )

            // ── Playback Speed ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.playback_speed),
                value = if (playerState.playbackSpeed == 1.0f) stringResource(R.string.normal)
                        else "${playerState.playbackSpeed}x",
                onClick = { onDismiss(); onShowSpeed() }
            )

            // ── Audio Track ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.AudioFile,
                label = stringResource(R.string.audio_track),
                value = "Track ${playerState.currentAudioTrack + 1}",
                onClick = { onDismiss(); onShowAudio() }
            )

            // ── Captions ──
            PlayerSettingsNavRow(
                icon = Icons.Filled.Subtitles,
                label = stringResource(R.string.filter_subtitles),
                value = if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off),
                onClick = { onDismiss(); onShowSubtitles() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── Loop Video ──
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
                onToggle = onAutoplayToggle
            )

            // ── Skip Silence ──
            PlayerSettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.player_settings_skip_silence),
                checked = playerState.isSkipSilenceEnabled,
                onToggle = onSkipSilenceToggle
            )
        }
    }
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
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
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
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { onToggle(!checked) },
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
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
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.playback_speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            LazyColumn {
                items(speeds) { speed ->
                    val isSelected = speed == currentSpeed
                    Surface(
                        onClick = { onSpeedSelected(speed); onDismiss() },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleCustomizerDialog(
    subtitleStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.filter_subtitles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            SubtitleCustomizer(
                currentStyle = subtitleStyle,
                onStyleChange = onStyleChange
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp)
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}
