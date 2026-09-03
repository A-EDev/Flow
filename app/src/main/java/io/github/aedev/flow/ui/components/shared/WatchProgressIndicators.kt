package io.github.aedev.flow.ui.components.shared

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.data.local.ViewHistory
import kotlinx.coroutines.flow.collectLatest

@Composable
fun rememberVideoWatchProgress(videoId: String): Float? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress by produceState<Float?>(initialValue = null, key1 = videoId) {
        ViewHistory.getInstance(context).getVideoHistory(videoId).collectLatest { entry ->
            value =
                if (entry != null && entry.duration > 0 && entry.progressPercentage >= 3f) {
                    if (entry.progressPercentage >= 90f) 1f else entry.progressPercentage / 100f
                } else {
                    null
                }
        }
    }
    return progress
}

@Composable
fun ThumbnailWatchProgress(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val progress = rememberVideoWatchProgress(videoId)
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.4f),
        )
    }
}
