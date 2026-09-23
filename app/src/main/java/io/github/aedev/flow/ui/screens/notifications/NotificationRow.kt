package io.github.aedev.flow.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.ui.components.shared.MediaThumbnail

private val ThumbnailWidth = 120.dp
private val RowPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
private val MetaSpacing = 6.dp
private val DeleteIconPadding = 24.dp

/**
 * One notification in a segmented group. Swiping it towards the start removes it; the screen offers
 * the undo, so the row itself carries no delete button.
 */
@Composable
internal fun NotificationRow(
    notification: NotificationEntity,
    isNew: Boolean,
    time: String,
    shape: Shape,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDismiss() },
        backgroundContent = { DeleteBackground() },
    ) {
        NotificationContent(notification, isNew, time, shape, onClick)
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(end = DeleteIconPadding),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationContent(
    notification: NotificationEntity,
    isNew: Boolean,
    time: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val newLabel = stringResource(R.string.notifications_group_new)
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.shapes(shape = shape),
        modifier = if (isNew) Modifier.semantics { stateDescription = newLabel } else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                supportingContentColor = colors.onSurfaceVariant,
            ),
        contentPadding = RowPadding,
        leadingContent = {
            MediaThumbnail(
                videoId = notification.videoId,
                thumbnailUrl = notification.thumbnailUrl,
                width = ThumbnailWidth,
                placeholder = Icons.Outlined.Notifications,
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MetaSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isNew) Badge(containerColor = colors.primary)
                Text(
                    text = stringResource(R.string.notifications_meta, notification.channelName, time),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Text(
            text = notification.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isNew) FontWeight.SemiBold else null,
        )
    }
}
