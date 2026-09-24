package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsViewModel
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaboratorsBottomSheet(
    collaborators: List<VideoCollaborator>,
    onDismiss: () -> Unit,
    viewModel: QuickActionsViewModel = sharedQuickActionsViewModel(),
) {
    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val navigator = LocalMediaNavigator.current
    val collaboratorChannelIds =
        remember(collaborators) {
            collaborators.map { it.channelId }.filter { it.isNotBlank() }.distinct()
        }
    androidx.compose.runtime.LaunchedEffect(collaboratorChannelIds) {
        collaboratorChannelIds.forEach { channelId ->
            viewModel.loadSubscriptionState(channelId)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.collaborators),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            collaborators.forEach { collaborator ->
                val canOpenChannel = collaborator.channelId.isNotBlank()
                val isSubscribed = subscribedChannelIds.contains(collaborator.channelId)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ChannelAvatarStack(
                        urls = listOf(collaborator.thumbnailUrl).filter { it.isNotBlank() },
                        contentDescription = collaborator.name,
                        avatarSize = 48.dp,
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (canOpenChannel) {
                                        Modifier.clickable {
                                            onDismiss()
                                            navigator.openChannel(collaborator.channelId)
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        val collaboratorName = collaborator.name.ifBlank { stringResource(R.string.collaborator) }
                        Text(
                            text = collaboratorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (collaborator.subscriberCountText.isNotBlank()) {
                            Text(
                                text = collaborator.subscriberCountText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (collaborator.channelId.isNotBlank()) {
                        FlowSubscribeButton(
                            isSubscribed = isSubscribed,
                            onSubscribeClick = {
                                viewModel.toggleSubscription(
                                    channelId = collaborator.channelId,
                                    channelName = collaborator.name,
                                    channelThumbnail = collaborator.thumbnailUrl,
                                )
                            },
                            onUnsubscribeClick = {
                                viewModel.toggleSubscription(
                                    channelId = collaborator.channelId,
                                    channelName = collaborator.name,
                                    channelThumbnail = collaborator.thumbnailUrl,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
