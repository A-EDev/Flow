package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.runtime.Composable
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.components.shared.CollaboratorsBottomSheet

@Composable
internal fun VideoCardSheets(
    state: VideoCardState,
    onChannelClick: ((String) -> Unit)?,
) {
    val sheets = state.sheets
    if (sheets.showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = state.video,
            onChannelClick = onChannelClick,
            onDismiss = { sheets.showQuickActions = false },
        )
    }

    if (sheets.showCollaborators) {
        CollaboratorsBottomSheet(
            collaborators = state.collaborators,
            onChannelClick = onChannelClick,
            onDismiss = { sheets.showCollaborators = false },
        )
    }
}
