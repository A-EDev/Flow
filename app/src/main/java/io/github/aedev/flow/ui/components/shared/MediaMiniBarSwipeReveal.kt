package io.github.aedev.flow.ui.components.shared

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MiniBarSwipeAction
import kotlin.math.abs

private const val DISABLED_CONTENT_ALPHA = 0.38f
private val RevealFullAt = 64.dp

/** What a swipe uncovers on one side: the action, or the next or previous item it would move to. */
@Immutable
data class MiniBarReveal(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val peekImageUrl: String? = null,
)

val MiniBarSwipeAction.icon: ImageVector
    get() =
        when (this) {
            MiniBarSwipeAction.CLOSE -> Icons.Rounded.Close
            MiniBarSwipeAction.NEXT -> Icons.Rounded.SkipNext
            MiniBarSwipeAction.PREVIOUS -> Icons.Rounded.SkipPrevious
            MiniBarSwipeAction.WATCH_LATER -> Icons.Rounded.WatchLater
            MiniBarSwipeAction.LIKE -> Icons.Rounded.ThumbUp
            MiniBarSwipeAction.DOWNLOAD -> Icons.Rounded.Download
            MiniBarSwipeAction.NOT_INTERESTED -> Icons.Rounded.NotInterested
            MiniBarSwipeAction.REMOVE_FROM_QUEUE -> Icons.Rounded.PlaylistRemove
        }

@get:StringRes
val MiniBarSwipeAction.labelRes: Int
    get() =
        when (this) {
            MiniBarSwipeAction.CLOSE -> R.string.close
            MiniBarSwipeAction.NEXT -> R.string.next
            MiniBarSwipeAction.PREVIOUS -> R.string.previous
            MiniBarSwipeAction.WATCH_LATER -> R.string.watch_later
            MiniBarSwipeAction.LIKE -> R.string.action_like
            MiniBarSwipeAction.DOWNLOAD -> R.string.download
            MiniBarSwipeAction.NOT_INTERESTED -> R.string.not_interested
            MiniBarSwipeAction.REMOVE_FROM_QUEUE -> R.string.remove_from_queue
        }

/** Closing and the two removals take the error container; every other action the secondary one. */
val MiniBarSwipeAction.isDestructive: Boolean
    get() =
        this == MiniBarSwipeAction.CLOSE ||
            this == MiniBarSwipeAction.NOT_INTERESTED ||
            this == MiniBarSwipeAction.REMOVE_FROM_QUEUE

/**
 * The layer under a swiping mini bar. [offset] is the card's horizontal offset: dragged towards the
 * start it uncovers the end edge with [towardsStart], and the other way [towardsEnd]. It fades in
 * over the first stretch of the drag, read in the draw phase.
 */
@Composable
fun MediaMiniBarSwipeReveal(
    offset: () -> Float,
    towardsStart: MiniBarReveal?,
    towardsEnd: MiniBarReveal?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val goingStart by remember { derivedStateOf { offset() < 0f } }
    val reveal = (if (goingStart) towardsStart else towardsEnd) ?: return
    val fullAtPx = with(LocalDensity.current) { RevealFullAt.toPx() }
    val container = if (reveal.destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (reveal.destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (abs(offset()) / fullAtPx).coerceIn(0f, 1f) }
                .background(container, shape)
                .padding(horizontal = 20.dp),
        contentAlignment = if (goingStart) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.graphicsLayer { alpha = if (reveal.enabled) 1f else DISABLED_CONTENT_ALPHA },
        ) {
            // The picture sits at the uncovered edge, so the card reaches it last.
            if (!goingStart) RevealVisual(reveal, content)
            Text(
                text = reveal.label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            if (goingStart) RevealVisual(reveal, content)
        }
    }
}

@Composable
private fun RevealVisual(
    reveal: MiniBarReveal,
    tint: Color,
) {
    if (reveal.peekImageUrl != null) {
        AsyncImage(
            model = reveal.peekImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 56.dp, height = 32.dp).clip(MaterialTheme.shapes.small),
        )
    } else {
        Icon(reveal.icon, contentDescription = null, tint = tint)
    }
}
