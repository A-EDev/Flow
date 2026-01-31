package com.flow.youtube.ui.screens.music.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.screens.music.MusicTrack

@Composable
fun TrackInfoDialog(
    track: MusicTrack,
    onDismiss: () -> Unit
) {
    val player = EnhancedMusicPlayerManager.player
    val audioFormat = player?.audioFormat
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_details)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(stringResource(R.string.title_label), track.title)
                InfoRow(stringResource(R.string.artist_label), track.artist)
                if (track.album.isNotEmpty()) {
                    InfoRow(stringResource(R.string.album_label), track.album)
                }
                InfoRow(stringResource(R.string.video_id_label), track.videoId)
                
                Divider()
                
                if (audioFormat != null) {
                    InfoRow(stringResource(R.string.codec_label), audioFormat.sampleMimeType ?: "Unknown")
                    InfoRow(stringResource(R.string.sample_rate_label), "${audioFormat.sampleRate} ${stringResource(R.string.hz)}")
                    if (audioFormat.bitrate > 0) {
                        InfoRow(stringResource(R.string.bitrate_label), "${audioFormat.bitrate / 1000} ${stringResource(R.string.kbps)}")
                    }
                    InfoRow(stringResource(R.string.channels_label), audioFormat.channelCount.toString())
                } else {
                    Text(
                        stringResource(R.string.audio_info_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
