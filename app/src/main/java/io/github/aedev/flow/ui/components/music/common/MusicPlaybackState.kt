/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.components.music.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.theme.MusicScrimNowPlaying
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Video id of the track the music player currently holds, or null when nothing is loaded.
 *
 * Read it through [isTrackPlaying] rather than directly. Every music item derives its own playing
 * state from this one value, so an item cannot silently miss the indicator by forgetting to pass a
 * flag — which is what happened while each call site computed it for itself.
 */
val LocalPlayingVideoId: ProvidableCompositionLocal<String?> = compositionLocalOf { null }

/**
 * Publishes the playing track id to every music item below it.
 *
 * Collected once, here, and mapped down to the id so the value only changes on an actual track
 * change rather than on every emission of the same track.
 */
@Composable
fun ProvideMusicPlaybackState(content: @Composable () -> Unit) {
    val playingIdFlow =
        remember {
            EnhancedMusicPlayerManager.currentTrack
                .map { it?.videoId }
                .distinctUntilChanged()
        }
    val playingVideoId by playingIdFlow.collectAsStateWithLifecycle(initialValue = null)

    CompositionLocalProvider(LocalPlayingVideoId provides playingVideoId, content = content)
}

/**
 * True when [videoId] is the track the player currently holds.
 */
@Composable
fun isTrackPlaying(videoId: String?): Boolean = !videoId.isNullOrEmpty() && LocalPlayingVideoId.current == videoId

/**
 * The now-playing treatment drawn over artwork: a scrim carrying the equaliser bars. The single
 * definition every music item shares, so the indicator is identical in every list and grid.
 */
@Composable
fun BoxScope.MusicNowPlayingOverlay(
    waveformWidth: Dp = 28.dp,
    waveformHeight: Dp = 24.dp,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(MusicScrimNowPlaying),
        contentAlignment = Alignment.Center,
    ) {
        PlayingWaveform(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = waveformWidth, height = waveformHeight),
        )
    }
}
