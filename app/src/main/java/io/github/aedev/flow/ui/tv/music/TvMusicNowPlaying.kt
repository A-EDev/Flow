package io.github.aedev.flow.ui.tv.music

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.musicplayer.common.rememberMusicPlayerAppearance
import io.github.aedev.flow.ui.components.musicplayer.full.FullMusicPlayerContent

/**
 * TV's full music player: the phone's player, which a 960 dp landscape window lays out wide, artwork
 * and controls beside the Up next and Lyrics pane, in the user's background style. Back closes it.
 */
@Composable
fun TvMusicNowPlaying(
    track: MusicTrack,
    onCollapse: () -> Unit,
) {
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val appearance = rememberMusicPlayerAppearance((currentTrack ?: track).highResThumbnailUrl)

    BackHandler(onBack = onCollapse)
    MaterialTheme(colorScheme = appearance.playerScheme, motionScheme = MotionScheme.expressive()) {
        FullMusicPlayerContent(
            track = track,
            isPlayerSheetExpanded = true,
            palette = appearance.palette,
            backdrop = appearance.backdrop,
            backgroundStyle = appearance.backgroundStyle,
            hideArtwork = appearance.hideArtwork,
            sleepTimerButton = appearance.sleepTimerButton,
            controlScheme = appearance.controlScheme,
        )
    }
}
