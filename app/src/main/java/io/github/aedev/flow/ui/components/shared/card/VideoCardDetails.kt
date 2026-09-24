package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.utils.avatarImageIdentityKey

internal fun Video.channelAvatarUrls(collaborators: List<VideoCollaborator> = emptyList()): List<String> {
    if (collaborators.size <= 1) {
        return (listOf(channelThumbnailUrl) + channelThumbnailUrls)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(1)
    }

    return collaborators
        .map { it.thumbnailUrl }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.avatarImageIdentityKey() }
        .take(3)
}

/**
 * YouTube omits the view count on members-only uploads and shows this badge instead, so the row above
 * it legitimately reads "2 days ago" with no views.
 */
@Composable
internal fun MembersOnlyLabel(video: Video) {
    val label = video.membersOnlyText ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.extendedColors.success,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extendedColors.success,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
