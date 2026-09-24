package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.runtime.Composable
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.components.shared.CollaboratorsBottomSheet

@Composable
internal fun VideoCardSheets(
    video: Video,
    collaborators: List<VideoCollaborator>,
    showQuickActions: Boolean,
    showCollaborators: Boolean,
    onChannelClick: ((String) -> Unit)?,
    onDismissQuickActions: () -> Unit,
    onDismissCollaborators: () -> Unit,
) {
    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onDismiss = onDismissQuickActions,
        )
    }

    if (showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = collaborators,
            onChannelClick = onChannelClick,
            onDismiss = onDismissCollaborators,
        )
    }
}
