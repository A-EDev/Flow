package io.github.aedev.flow.ui.components.videoplayer.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatViewCount

@Composable
internal fun VideoActionRow(
    likeState: String,
    likeCount: Long? = null,
    dislikeCount: Long?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackgroundPlayClick: () -> Unit,
    onCopyLinkClick: () -> Unit = {},
    onCopyLinkAtTimeClick: () -> Unit = {},
    isSaved: Boolean = false,
    isDownloaded: Boolean = false,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SegmentedLikeDislikeButton(
                likeState = likeState,
                likeCount = likeCount,
                dislikeCount = dislikeCount,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick,
            )
        }

        item {
            ActionChip(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) stringResource(R.string.saved) else stringResource(R.string.save),
                onClick = onSaveClick,
                tint = if (isSaved) MaterialTheme.colorScheme.primary else null,
            )
        }

        item {
            ActionChip(
                icon = if (isDownloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                label = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                onClick = onDownloadClick,
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else null,
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Headphones,
                label = stringResource(R.string.player_action_background),
                onClick = onBackgroundPlayClick,
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.share),
                onClick = onShareClick,
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Link,
                label = stringResource(R.string.player_action_copy_link),
                onClick = onCopyLinkClick,
            )
        }

        item {
            ActionChip(
                icon = Icons.Outlined.Timer,
                label = stringResource(R.string.player_action_copy_link_at_time),
                onClick = onCopyLinkAtTimeClick,
            )
        }
    }
}

@Composable
internal fun SegmentedLikeDislikeButton(
    likeState: String,
    likeCount: Long? = null,
    dislikeCount: Long?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.height(36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Like Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clickable(onClick = onLikeClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (likeState == "LIKED") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                val likeText =
                    if (likeCount != null && likeCount > 0) {
                        formatViewCount(likeCount)
                    } else if (likeState == "LIKED") {
                        stringResource(R.string.liked)
                    } else {
                        stringResource(R.string.like)
                    }

                Text(
                    text = likeText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
            )

            // Dislike Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clickable(onClick = onDislikeClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (likeState == "DISLIKED") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(R.string.player_action_dislike),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )

                if (dislikeCount != null && dislikeCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatViewCount(dislikeCount),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.height(36.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = tint ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
