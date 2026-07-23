package io.github.aedev.flow.ui.tv.music

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.MusicPlayerUiState
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.player.InlineLyricsPanel
import io.github.aedev.flow.ui.tv.components.TvSidePanel

/**
 * Lyrics side panel hosting the mobile lyrics canvas ([InlineLyricsPanel]):
 * same synced word-level karaoke rendering as the phone player. The canvas
 * drives its own position from EnhancedMusicPlayerManager, so it needs no
 * TV-side position loop.
 */
@Composable
fun BoxScope.TvLyricsPanel(
    visible: Boolean,
    track: MusicTrack?,
    uiState: MusicPlayerUiState,
    positionProvider: () -> Long,
    onEnsureLyrics: (MusicTrack) -> Unit,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(visible, track?.videoId) {
        if (!visible) return@LaunchedEffect
        track?.let(onEnsureLyrics)
    }

    TvSidePanel(
        visible = visible,
        title = stringResource(R.string.tv_music_lyrics),
        onClose = onClose,
    ) {
        InlineLyricsPanel(
            lyrics = uiState.lyrics,
            syncedLyrics = uiState.syncedLyrics,
            currentPosition = positionProvider(),
            isLoading = uiState.isLyricsLoading,
            accentColor = MaterialTheme.colorScheme.primary,
            onSeekTo = onSeekTo,
            providerName = uiState.lyricsProviderName,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
