package io.github.aedev.flow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

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
                        .padding(6.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.cd_short_watched),
                    tint = Color.White,
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .size(14.dp),
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.4f),
        )
    }
}
