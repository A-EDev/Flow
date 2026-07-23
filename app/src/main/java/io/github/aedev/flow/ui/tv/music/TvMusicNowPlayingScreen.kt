package io.github.aedev.flow.ui.tv.music

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.player.PlayerBackground
import io.github.aedev.flow.ui.screens.music.player.PlayerProgressSlider
import io.github.aedev.flow.ui.screens.music.player.rememberMusicPalette
import io.github.aedev.flow.ui.tv.components.TvIconButton
import io.github.aedev.flow.ui.tv.components.TvIconButtonColors
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.input.TvPlayerKeyMapper
import io.github.aedev.flow.ui.tv.player.state.TvScrubController
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class TvMusicPanel { NONE, QUEUE, LYRICS }

/**
 * Full-screen music now-playing sharing the mobile player's visual system:
 * artwork palette extraction, the user's PlayerBackground style, and the
 * mobile progress slider (style-preference aware) wrapped for D-pad scrubbing.
 */
@Composable
fun TvMusicNowPlayingScreen(
    viewModel: MusicPlayerViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = EnhancedMusicPlayerManager
    val context = LocalContext.current
    val track by manager.currentTrack.collectAsStateWithLifecycle()
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val shuffleEnabled by manager.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by manager.repeatMode.collectAsStateWithLifecycle()
    val isLiked by manager.isLiked.collectAsStateWithLifecycle()
    val playingFrom by manager.playingFrom.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    val playerPreferences = remember { PlayerPreferences(context) }
    val backgroundStyle by playerPreferences.musicPlayerBackgroundStyle.collectAsState(
        initial = MusicPlayerBackgroundStyle.BLUR_GRADIENT,
    )
    val artworkUrl = track?.highResThumbnailUrl ?: track?.thumbnailUrl
    val palette = rememberMusicPalette(artworkUrl)
    // Translucent chips over the always-dark PlayerBackground; latched toggles
    // (like, shuffle, repeat, panels) light up with the artwork accent.
    val playerButtonColors = remember(palette.accent) {
        TvIconButtonColors(
            container = Color.White.copy(alpha = 0.12f),
            content = Color.White,
            focusedContainer = Color.White,
            focusedContent = Color.Black,
            activeContainer = palette.accent,
            activeContent = if (palette.accent.luminance() > 0.5f) Color.Black else Color.White,
        )
    }

    var panel by rememberSaveable { mutableStateOf(TvMusicPanel.NONE) }
    val scrubController = remember { TvScrubController() }
    var scrubUiState by remember { mutableStateOf(TvScrubController.ScrubState()) }
    var seekBarFocused by remember { mutableStateOf(false) }
    val seekBarFocusRequester = remember { FocusRequester() }

    // Slider position at 2 Hz while playing — the mobile slider is
    // position-driven, and this screen has no video pipeline to protect.
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playerState.isPlaying, track?.videoId) {
        while (isActive) {
            positionMs = manager.getCurrentPosition().coerceAtLeast(0L)
            durationMs = manager.getDuration().coerceAtLeast(0L)
            if (!playerState.isPlaying) break
            delay(500)
        }
    }

    fun commitScrub() {
        scrubController.commit()?.let { target ->
            manager.seekTo(target)
            positionMs = target
        }
        scrubUiState = scrubController.current
    }

    BackHandler {
        when {
            scrubController.current.isScrubbing -> {
                scrubController.cancel()
                scrubUiState = scrubController.current
            }
            panel != TvMusicPanel.NONE -> panel = TvMusicPanel.NONE
            else -> onCollapse()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                when (event.type) {
                    KeyEventType.KeyUp ->
                        if (scrubController.current.isScrubbing &&
                            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                        ) {
                            commitScrub()
                            true
                        } else {
                            false
                        }
                    KeyEventType.KeyDown -> {
                        val action = TvPlayerKeyMapper.map(keyCode)
                            ?: if (seekBarFocused) TvPlayerKeyMapper.mapDpadWhenSeekBarFocused(keyCode) else null
                        when (action) {
                            TvPlayerAction.TOGGLE_PLAYBACK -> manager.togglePlayPause()
                            TvPlayerAction.PLAY, TvPlayerAction.PAUSE -> manager.togglePlayPause()
                            TvPlayerAction.NEXT -> manager.playNext()
                            TvPlayerAction.PREVIOUS -> manager.playPrevious()
                            TvPlayerAction.SEEK_BACK -> manager.seekTo(
                                (manager.getCurrentPosition() - 10_000L).coerceAtLeast(0L),
                            )
                            TvPlayerAction.SEEK_FORWARD -> manager.seekTo(
                                manager.getCurrentPosition() + 10_000L,
                            )
                            TvPlayerAction.SCRUB_BACK, TvPlayerAction.SCRUB_FORWARD -> {
                                scrubUiState = scrubController.beginOrStep(
                                    direction = if (action == TvPlayerAction.SCRUB_FORWARD) 1 else -1,
                                    repeatCount = event.nativeKeyEvent.repeatCount,
                                    currentPositionMs = manager.getCurrentPosition(),
                                    durationMs = manager.getDuration(),
                                )
                            }
                            TvPlayerAction.COMMIT_SCRUB -> commitScrub()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerBackground(
            thumbnailUrl = artworkUrl,
            style = backgroundStyle,
            paletteBaseColor = palette.base,
            paletteAccentColor = palette.accent,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dimens.overscanHorizontal,
                    vertical = dimens.overscanVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black.copy(alpha = 0.3f),
                tonalElevation = 0.dp,
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = track?.title,
                    modifier = Modifier
                        .size(300.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (playingFrom.isNotBlank()) {
                    Text(
                        text = playingFrom,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = track?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.onBase,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TvIconButton(
                        icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.tv_library_likes),
                        onClick = viewModel::toggleLike,
                        active = isLiked,
                        colors = playerButtonColors,
                    )
                }
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onBase.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Mobile progress slider (style-preference aware) inside a
                // focusable shell: D-pad LEFT/RIGHT scrubs via TvScrubController.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(seekBarFocusRequester)
                        .onFocusChanged { seekBarFocused = it.isFocused }
                        .focusable(),
                    shape = MaterialTheme.shapes.large,
                    color = if (seekBarFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                ) {
                    PlayerProgressSlider(
                        currentPosition = scrubUiState.takeIf { it.isScrubbing }?.targetMs ?: positionMs,
                        duration = durationMs,
                        onSeekTo = { target ->
                            manager.seekTo(target)
                            positionMs = target
                        },
                        isPlaying = playerState.isPlaying,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .focusProperties { canFocus = false },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIconButton(
                        icon = Icons.Outlined.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        onClick = manager::toggleShuffle,
                        active = shuffleEnabled,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = Icons.Outlined.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        onClick = manager::playPrevious,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = if (playerState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playerState.isPlaying) {
                            stringResource(R.string.pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        onClick = manager::togglePlayPause,
                        active = true,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = Icons.Outlined.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        onClick = manager::playNext,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = if (repeatMode == RepeatMode.ONE) {
                            Icons.Outlined.RepeatOne
                        } else {
                            Icons.Outlined.Repeat
                        },
                        contentDescription = stringResource(R.string.loop_video),
                        onClick = manager::toggleRepeat,
                        active = repeatMode != RepeatMode.OFF,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = Icons.Outlined.Lyrics,
                        contentDescription = stringResource(R.string.tv_music_lyrics),
                        onClick = {
                            panel = if (panel == TvMusicPanel.LYRICS) TvMusicPanel.NONE else TvMusicPanel.LYRICS
                        },
                        active = panel == TvMusicPanel.LYRICS,
                        colors = playerButtonColors,
                    )
                    TvIconButton(
                        icon = Icons.AutoMirrored.Outlined.QueueMusic,
                        contentDescription = stringResource(R.string.tv_player_queue),
                        onClick = {
                            panel = if (panel == TvMusicPanel.QUEUE) TvMusicPanel.NONE else TvMusicPanel.QUEUE
                        },
                        active = panel == TvMusicPanel.QUEUE,
                        colors = playerButtonColors,
                    )
                }
            }
        }

        TvMusicQueuePanel(
            visible = panel == TvMusicPanel.QUEUE,
            manager = manager,
            onClose = { panel = TvMusicPanel.NONE },
        )
        TvLyricsPanel(
            visible = panel == TvMusicPanel.LYRICS,
            track = track,
            uiState = uiState,
            positionProvider = { manager.getCurrentPosition().coerceAtLeast(0L) },
            onEnsureLyrics = viewModel::ensureLyricsLoaded,
            onSeekTo = manager::seekTo,
            onClose = { panel = TvMusicPanel.NONE },
        )
    }
}
