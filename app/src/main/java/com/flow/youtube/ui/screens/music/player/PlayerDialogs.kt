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
    // Media3 Player interface doesn't expose audioFormat directly nicely like ExoPlayer did
    // We can iterate tracks to find audio format if needed, or just show null/unknown for now.
    
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
                
                // Audio format info not directly available in standard Player interface
                // Simplified for Media3 migration
                Text(
                    stringResource(R.string.audio_info_not_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
