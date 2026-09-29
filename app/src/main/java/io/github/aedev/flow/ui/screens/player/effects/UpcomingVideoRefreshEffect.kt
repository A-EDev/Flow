package io.github.aedev.flow.ui.screens.player.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.UpcomingPremierePolicy
import kotlinx.coroutines.delay

/**
 * A premiere does not flip to playable at its announced time, so after the countdown expires the
 * video info is re-fetched until the upstream metadata catches up.
 *
 * The poll is bound to STARTED: nothing consumes the refreshed metadata while the player is not on
 * screen, and a backgrounded premiere used to keep a 30 s network wakeup running for ten minutes.
 * [attempts] is hoisted above `repeatOnLifecycle` so the ceiling stays a total across the whole
 * premiere rather than resetting on every return to the foreground.
 */
@Composable
internal fun UpcomingVideoRefreshEffect(
    videoId: String,
    isUpcoming: Boolean,
    upcomingReleaseTimeMs: Long?,
    viewModel: VideoPlayerViewModel,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(videoId, isUpcoming, upcomingReleaseTimeMs, lifecycleOwner) {
        val releaseMs = upcomingReleaseTimeMs
        if (!isUpcoming || releaseMs == null) return@LaunchedEffect
        var attempts = 0
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (attempts >= UpcomingPremierePolicy.MAX_REFRESH_ATTEMPTS) return@repeatOnLifecycle
            val waitMs = (releaseMs - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(waitMs + UpcomingPremierePolicy.SETTLE_MS)
            while (viewModel.uiState.value.isUpcoming && attempts < UpcomingPremierePolicy.MAX_REFRESH_ATTEMPTS) {
                viewModel.loadVideoInfo(videoId, forceRefresh = true)
                attempts++
                delay(UpcomingPremierePolicy.REFRESH_INTERVAL_MS)
            }
        }
    }
}
