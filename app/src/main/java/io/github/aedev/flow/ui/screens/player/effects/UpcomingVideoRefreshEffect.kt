package io.github.aedev.flow.ui.screens.player.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import kotlinx.coroutines.delay

/**
 * A premiere does not flip to playable at its announced time, so after the countdown expires the
 * video info is re-fetched until the upstream metadata catches up.
 */
@Composable
internal fun UpcomingVideoRefreshEffect(
    videoId: String,
    isUpcoming: Boolean,
    upcomingReleaseTimeMs: Long?,
    viewModel: VideoPlayerViewModel,
) {
    LaunchedEffect(videoId, isUpcoming, upcomingReleaseTimeMs) {
        val releaseMs = upcomingReleaseTimeMs
        if (!isUpcoming || releaseMs == null) return@LaunchedEffect
        val waitMs = (releaseMs - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(waitMs + 3_000L)
        var attempts = 0
        while (viewModel.uiState.value.isUpcoming && attempts < 20) {
            viewModel.loadVideoInfo(videoId, forceRefresh = true)
            attempts++
            delay(30_000L)
        }
    }
}
