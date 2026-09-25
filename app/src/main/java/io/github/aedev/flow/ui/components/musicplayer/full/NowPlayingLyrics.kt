package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.ui.components.musicplayer.lyrics.InlineLyricsPanel
import io.github.aedev.flow.ui.components.musicplayer.lyrics.MusicLyricsSheet
import io.github.aedev.flow.ui.components.musicplayer.lyrics.lyricsTextAlignFor
import io.github.aedev.flow.ui.screens.music.MusicPlayerUiState
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel

/** The player's lyrics wired to its state: the full-screen sheet every layout can open. */
@Composable
internal fun NowPlayingLyricsSheet(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    visible: Boolean,
    retainContent: Boolean,
    backdropBaseColor: Color,
    accentColor: Color,
    fallbackTitle: String,
    fallbackArtist: String,
    artworkUrl: String,
    positionState: State<Long>,
    onDismiss: () -> Unit,
) {
    MusicLyricsSheet(
        visible = visible,
        retainContent = retainContent,
        backdropBaseColor = backdropBaseColor,
        accentColor = accentColor,
        trackTitle = uiState.currentTrack?.title ?: fallbackTitle,
        trackArtist = uiState.currentTrack?.artist ?: fallbackArtist,
        artworkUrl = artworkUrl,
        isPlaying = uiState.isPlaying,
        isBuffering = uiState.isBuffering,
        lyrics = uiState.lyrics,
        syncedLyrics = uiState.syncedLyrics,
        // Raw position — the panel's own sync loops apply syncOffsetMs; baking the
        // offset in here double-counted (and the loops ignored it anyway, #offset fix).
        positionProvider = { positionState.value },
        isLoading = uiState.isLyricsLoading,
        providerName = uiState.lyricsProviderName,
        alignPref = uiState.lyricsTextAlign,
        syncOffsetMs = uiState.lyricsSyncOffsetMs,
        candidates = uiState.lyricsCandidates,
        isBrowsing = uiState.isBrowsingLyrics,
        onSeekTo = { viewModel.seekTo((it - uiState.lyricsSyncOffsetMs).coerceAtLeast(0L)) },
        onRefresh = { viewModel.refreshLyrics() },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onAlignChange = { viewModel.setLyricsTextAlign(it) },
        onAdjustOffset = { viewModel.adjustLyricsSyncOffset(it) },
        onResetOffset = { viewModel.resetLyricsSyncOffset() },
        onBrowseSources = { viewModel.browseLyricsCandidates() },
        onCancelBrowse = { viewModel.cancelLyricsBrowse() },
        onSelectCandidate = { viewModel.applyLyricsCandidate(it) },
        onApplyEditedLyrics = { viewModel.applyEditedLyrics(it) },
        onDismiss = onDismiss,
    )
}

/**
 * The lyrics in the wide player's side pane. [active] is false whenever nobody can see the pane
 * (player collapsed, or the full-screen sheet over it) so its sync loop and spinner stop.
 */
@Composable
internal fun NowPlayingLyricsPane(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    accentColor: Color,
    positionState: State<Long>,
    active: Boolean,
) {
    InlineLyricsPanel(
        lyrics = uiState.lyrics,
        syncedLyrics = uiState.syncedLyrics,
        positionProvider = { positionState.value },
        isLoading = uiState.isLyricsLoading && active,
        accentColor = accentColor,
        onSeekTo = { viewModel.seekTo((it - uiState.lyricsSyncOffsetMs).coerceAtLeast(0L)) },
        providerName = uiState.lyricsProviderName,
        textAlign = lyricsTextAlignFor(uiState.lyricsTextAlign),
        syncOffsetMs = uiState.lyricsSyncOffsetMs,
        active = active,
        isPlaying = uiState.isPlaying,
        modifier = Modifier.fillMaxSize(),
    )
}
