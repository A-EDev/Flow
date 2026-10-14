package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowToggleOption

/** What the side pane beside the wide player shows. */
internal enum class PlayerSidePaneTab { UP_NEXT, LYRICS }

/**
 * The pane beside the player on wide windows: Up next or Lyrics, switched by a connected toggle
 * group. Lyrics draw on [lyricsBackdrop], the same dark backdrop as the full-screen lyrics sheet,
 * which [onOpenFullLyrics] opens for sources, timing and editing.
 */
@Composable
internal fun PlayerSidePane(
    tab: PlayerSidePaneTab,
    onTabChange: (PlayerSidePaneTab) -> Unit,
    onOpenFullLyrics: () -> Unit,
    lyricsBackdrop: Color,
    upNext: @Composable () -> Unit,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowConnectedToggleGroup(
                    options =
                        listOf(
                            FlowToggleOption(
                                PlayerSidePaneTab.UP_NEXT,
                                stringResource(R.string.music_player_tab_up_next),
                                Icons.AutoMirrored.Rounded.QueueMusic,
                            ),
                            FlowToggleOption(
                                PlayerSidePaneTab.LYRICS,
                                stringResource(R.string.music_player_tab_lyrics),
                                Icons.Rounded.Lyrics,
                            ),
                        ),
                    selected = tab,
                    onSelected = onTabChange,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenFullLyrics) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = stringResource(R.string.music_player_lyrics_full_screen),
                    )
                }
            }
            when (tab) {
                PlayerSidePaneTab.UP_NEXT -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { upNext() }
                }

                PlayerSidePaneTab.LYRICS -> {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(lyricsBackdrop),
                    ) { lyrics() }
                }
            }
        }
    }
}
