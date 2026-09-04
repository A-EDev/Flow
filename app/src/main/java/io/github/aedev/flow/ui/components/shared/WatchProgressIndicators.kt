package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.rememberWatchProgress
import io.github.aedev.flow.ui.theme.artworkScrim

private const val PROGRESS_TRACK_ALPHA = 0.4f
private val ProgressBarHeight = 3.dp

@Composable
fun ThumbnailWatchProgress(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val progress = rememberWatchProgress(videoId) ?: return
    WatchProgressBar(progress = progress, modifier = modifier)
}

@Composable
fun WatchProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = ProgressBarHeight,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
        color = MaterialTheme.colorScheme.primary,
        trackColor = artworkScrim(PROGRESS_TRACK_ALPHA),
    )
}
