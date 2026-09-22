package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.utils.formatViewCount

/** The one size every Shorts grid and strip agrees on, so cells line up across screens. */
object ShortCardDefaults {
    val MinWidth = 160.dp
    val Spacing = 12.dp
    const val ASPECT_RATIO = 9f / 16f
}

private val TitleSpacing = 8.dp

/**
 * A portrait reel card: poster, title, view count. Long-pressing opens the video quick actions.
 * The channel Shorts grid, search, history, the library shelves and the home strip all draw this
 * one card; a grid passes `Modifier.fillMaxWidth()` and a strip keeps the default width.
 */
@Composable
fun MediaShortCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(ShortCardDefaults.MinWidth),
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var showQuickActions by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onLongClick = { showQuickActions = true },
                    onClick = onClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ShortCardDefaults.ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .thumbnailGradientOverlay(),
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            ShortWatchedIndicator(videoId = video.id)
        }
        Spacer(modifier = Modifier.height(TitleSpacing))
        Text(
            text = video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.views_template, formatViewCount(video.viewCount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            trailingContent?.invoke()
        }
    }

    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = null,
            onDismiss = { showQuickActions = false },
        )
    }
}
