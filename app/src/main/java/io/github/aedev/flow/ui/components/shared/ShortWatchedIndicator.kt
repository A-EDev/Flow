package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.WATCHED_PROGRESS_THRESHOLD
import io.github.aedev.flow.ui.components.rememberWatchProgress
import io.github.aedev.flow.ui.theme.ArtworkScrimContent
import io.github.aedev.flow.ui.theme.artworkScrim

private const val WATCHED_BADGE_SCRIM_ALPHA = 0.6f
private val BadgeMargin = 6.dp
private val BadgePadding = 4.dp
private val BadgeIconSize = 14.dp

/**
 * "You already watched this reel" marker for a Shorts thumbnail.
 *
 * Reels carry no duration badge and, until now, no progress line either, so a reel you had already
 * swiped through looked exactly like an untouched one. This draws the same progress line long-form
 * cards use, plus an eye badge once the reel counts as watched.
 *
 * Add it as the last child of the thumbnail `Box` so it sits above the image.
 *
 * Progress comes from the shared [LocalVideoWatchProgress] store, so a whole grid of reels still
 * costs a single history observer and only the reel whose entry changed recomposes.
 */
@Composable
fun ShortWatchedIndicator(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val progress = rememberWatchProgress(videoId) ?: return
    Box(modifier = modifier.fillMaxSize()) {
        if (progress >= WATCHED_PROGRESS_THRESHOLD) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(BadgeMargin),
                shape = CircleShape,
                color = artworkScrim(WATCHED_BADGE_SCRIM_ALPHA),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.cd_short_watched),
                    tint = ArtworkScrimContent,
                    modifier =
                        Modifier
                            .padding(BadgePadding)
                            .size(BadgeIconSize),
                )
            }
        }
        WatchProgressBar(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
