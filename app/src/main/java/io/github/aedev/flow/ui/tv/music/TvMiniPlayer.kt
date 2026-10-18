package io.github.aedev.flow.ui.tv.music

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.musicplayer.common.rememberMusicPlayerAppearance
import io.github.aedev.flow.ui.components.musicplayer.mini.MiniPlayerContent
import io.github.aedev.flow.ui.components.shared.MediaMiniBarDefaults

/**
 * The phone's mini player under the TV browse screens: OK on it opens the full player, its own
 * transport buttons sit to the right, and the close button ends the session.
 */
@Composable
fun TvMiniPlayer(
    track: MusicTrack,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = rememberMusicPlayerAppearance(track.highResThumbnailUrl)
    MaterialTheme(colorScheme = appearance.controlScheme) {
        Surface(
            onClick = onExpand,
            modifier =
                modifier
                    .widthIn(max = MediaMiniBarDefaults.MaxWidth)
                    .fillMaxWidth()
                    .height(MediaMiniBarDefaults.Height),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniPlayerContent(
                    track = track,
                    showPrevious = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
    }
}
