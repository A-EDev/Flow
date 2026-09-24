package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.data.model.needsCollaboratorResolution
import io.github.aedev.flow.data.repository.VideoCollaboratorResolver

internal fun Video.collaboratorItems(resolvedCollaborators: List<VideoCollaborator> = emptyList()): List<VideoCollaborator> =
    (collaborators + resolvedCollaborators)
        .filter { it.name.isNotBlank() }
        .filter { it.hasChannelCollaboratorSignal() }
        .distinctBy { it.channelId.ifBlank { it.name.lowercase() } }
        .takeIf { it.size > 1 }
        .orEmpty()

private fun VideoCollaborator.hasChannelCollaboratorSignal(): Boolean =
    channelId.startsWith("UC") ||
        thumbnailUrl.isNotBlank() ||
        subscriberCountText.contains("subscriber", ignoreCase = true)

internal fun List<VideoCollaborator>.displayCollaboratorChannelName(
    fallback: String,
    moreCollaboratorsText: String? = null,
    conjunction: String,
): String {
    val names = map { it.name }.filter { it.isNotBlank() }
    return when {
        names.size > 2 && moreCollaboratorsText != null -> moreCollaboratorsText
        names.size > 1 -> names.joinToString(" $conjunction ")
        else -> fallback
    }
}

@Composable
internal fun rememberCollaboratorChannelDisplayName(
    fallback: String,
    collaborators: List<VideoCollaborator>,
): String {
    val firstName = collaborators.firstOrNull()?.name.orEmpty()
    val compactName =
        stringResource(
            R.string.channel_and_more_template,
            firstName,
            (collaborators.size - 1).coerceAtLeast(0),
        )
    val conjunction = stringResource(R.string.conjunction_and)
    return remember(fallback, collaborators, compactName, conjunction) {
        collaborators.displayCollaboratorChannelName(fallback, compactName, conjunction)
    }
}

@Composable
internal fun rememberCollaboratorItems(video: Video): List<VideoCollaborator> {
    val needsResolution = video.needsCollaboratorResolution()
    val fetchedCollaborators by produceState<List<VideoCollaborator>>(
        initialValue = emptyList(),
        key1 = video.id,
        key2 = video.collaborators,
        key3 = needsResolution,
    ) {
        value =
            if (needsResolution) {
                VideoCollaboratorResolver.resolve(video.id)
            } else {
                emptyList()
            }
    }
    return remember(video, fetchedCollaborators) {
        video.collaboratorItems(fetchedCollaborators)
    }
}
