package com.flow.youtube.ui.screens.player.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Download
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
                    val videoStreams = uiState.streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList()
                    val videoOnlyStreams = uiState.streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList()
                    
                    // Prioritize combined streams, then append video-only ones
                    val allStreams = videoStreams + videoOnlyStreams
                    val distinctStreams = allStreams.distinctBy { it.height }.sortedByDescending { it.height }
                    
                    if (distinctStreams.isEmpty()) {
                         item {
                             Text(stringResource(R.string.no_download_streams), modifier = Modifier.padding(16.dp))
                         }
                    }

                    items(distinctStreams) { stream ->
                        val isVideoOnly = videoOnlyStreams.any { it.url == stream.url }
                        val qualityLabel = "${stream.height}p"
                        
                        val sizeInBytes = uiState.streamSizes[stream.height]
                        val formatName = stream.format?.name ?: "MP4"
                        
                        val sizeText = if (sizeInBytes != null && sizeInBytes > 0) {
                            val mb = sizeInBytes / (1024 * 1024.0)
                            "$formatName • ${String.format("%.1f MB", mb)}"
                        } else {
                            "$formatName • $qualityLabel"
                        }

                        Surface(
                            onClick = {
                                onDismiss()
                                val downloadUrl = stream.url
                                if (downloadUrl != null) {
                                    // Find best audio stream if DASH
                                    var audioUrl: String? = null
                                    if (isVideoOnly) {
                                        audioUrl = uiState.streamInfo?.audioStreams?.maxByOrNull { it.bitrate }?.url
                                    }
                                    
                                    VideoPlayerUtils.startDownload(context, video, downloadUrl, qualityLabel, audioUrl)
                                    Toast.makeText(context, context.getString(R.string.downloading_template, qualityLabel), Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qualityLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (stream.height >= 1080) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "HD",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
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

@Composable
fun QualitySelectorDialog(
    availableQualities: List<QualityOption>,
    currentQuality: Int,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_quality_title)) },
        text = {
            LazyColumn {
                items(availableQualities.sortedByDescending { it.height }) { quality ->
                    Surface(
                        onClick = {
                            onQualitySelected(quality.height)
                            onDismiss()
                        },
                        color = if (quality.height == currentQuality) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            if (quality.height == currentQuality) {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun AudioTrackSelectorDialog(
    availableAudioTracks: List<AudioTrackOption>,
    currentAudioTrack: Int,
    onDismiss: () -> Unit,
    onTrackSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_track)) },
        text = {
            LazyColumn {
                items(availableAudioTracks.size) { index ->
                    val track = availableAudioTracks[index]
                    Surface(
                        onClick = {
                            onTrackSelected(index)
                            onDismiss()
                        },
                        color = if (index == currentAudioTrack) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = track.language,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (index == currentAudioTrack) {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun SubtitleSelectorDialog(
    availableSubtitles: List<SubtitleOption>,
    selectedSubtitleUrl: String?,
    subtitlesEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int, String) -> Unit,
    onDisableSubtitles: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_subtitles)) },
        text = {
            LazyColumn {
                // Off option
                item {
                    Surface(
                        onClick = {
                            onDisableSubtitles()
                            onDismiss()
                        },
                        color = if (!subtitlesEnabled) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.off),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            if (!subtitlesEnabled) {
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
                    Surface(
                        onClick = {
                            onSubtitleSelected(index, subtitle.url)
                            onDismiss()
                        },
                        color = if (subtitle.url == selectedSubtitleUrl) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = subtitle.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = subtitle.language,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (subtitle.url == selectedSubtitleUrl) {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_settings)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    onClick = {
                        onDismiss()
                        onShowQuality()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.quality)) },
                        supportingContent = { Text("${playerState.currentQuality}p") },
                        leadingContent = {
                            Icon(Icons.Filled.HighQuality, contentDescription = null)
                        }
                    )
                }
                
                Surface(
                    onClick = {
                        onDismiss()
                        onShowAudio()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.audio_track)) },
                        supportingContent = { 
                            Text("Track ${playerState.currentAudioTrack + 1}") 
                        },
                        leadingContent = {
                            Icon(Icons.Filled.AudioFile, contentDescription = null)
                        }
                    )
                }
                
                Surface(
                    onClick = {
                        onDismiss()
                        onShowSpeed()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.playback_speed)) },
                        supportingContent = { 
                            Text("${playerState.playbackSpeed}x") 
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Speed, contentDescription = null)
                        }
                    )
                }

                // Loop Toggle
                Surface(
                    onClick = {
                        onLoopToggle(!playerState.isLooping)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.loop_video)) },
                        supportingContent = { 
                            Text(if (playerState.isLooping) stringResource(R.string.on) else stringResource(R.string.off)) 
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Repeat, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = playerState.isLooping,
                                onCheckedChange = onLoopToggle
                            )
                        }
                    )
                }

                // Auto-play Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.autoplay_next)) },
                        supportingContent = { 
                            Text(if (autoplayEnabled) stringResource(R.string.on) else stringResource(R.string.off)) 
                        },
                        leadingContent = {
                            Icon(Icons.Filled.SkipNext, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = autoplayEnabled,
                                onCheckedChange = onAutoplayToggle
                            )
                        }
                    )
                }

                Surface(
                    onClick = {
                        onDismiss()
                        onShowSubtitles()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.filter_subtitles)) },
                        supportingContent = { 
                            Text(if (subtitlesEnabled) stringResource(R.string.on) else stringResource(R.string.off)) 
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Subtitles, contentDescription = null)
                        },
                        trailingContent = {
                            if (subtitlesEnabled) {
                                IconButton(onClick = { 
                                    onDismiss()
                                    onShowSubtitleStyle()
                                }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Subtitle Style")
                                }
                            }
                        }
                    )
                }

                Surface(
                    onClick = {
                        onSkipSilenceToggle(!playerState.isSkipSilenceEnabled)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.player_settings_skip_silence)) },
                        supportingContent = { 
                            Text(if (playerState.isSkipSilenceEnabled) stringResource(R.string.on) else stringResource(R.string.off)) 
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = playerState.isSkipSilenceEnabled,
                                onCheckedChange = onSkipSilenceToggle
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun PlaybackSpeedSelectorDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_speed)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(speeds) { speed ->
                    Surface(
                        onClick = {
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                        color = if (speed == currentSpeed) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (speed == 1.0f) stringResource(R.string.normal) else "${speed}x",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            if (speed == currentSpeed) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun SubtitleStyleCustomizerDialog(
    subtitleStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            SubtitleCustomizer(
                currentStyle = subtitleStyle,
                onStyleChange = onStyleChange
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}
