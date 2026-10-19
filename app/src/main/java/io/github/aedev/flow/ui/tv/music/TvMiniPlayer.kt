package io.github.aedev.flow.ui.tv.music

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.musicplayer.common.rememberMusicPlayerAppearance
import io.github.aedev.flow.ui.components.musicplayer.mini.MiniPlayerContent
import io.github.aedev.flow.ui.components.shared.MediaMiniBarDefaults
import io.github.aedev.flow.ui.components.shared.dpadFocusGroup

/** The phone mini card's resting elevation. */
private val CardShadowElevation = 6.dp

/**
 * The phone's collapsed mini card, floating over the bottom of the TV browse screens. The cover and
 * titles open the full player, and the transport and close buttons are focus stops of their own.
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
            modifier =
                modifier
                    .widthIn(max = MediaMiniBarDefaults.MaxWidth)
                    .fillMaxWidth()
                    .height(MediaMiniBarDefaults.Height),
            shape = RoundedCornerShape(MediaMiniBarDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = CardShadowElevation,
        ) {
            Row(
                modifier = Modifier.dpadFocusGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniPlayerContent(
                    track = track,
                    showPrevious = true,
                    onOpen = onExpand,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
    }
}
