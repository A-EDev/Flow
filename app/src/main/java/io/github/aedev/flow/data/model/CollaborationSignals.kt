package io.github.aedev.flow.data.model

import java.util.Locale

internal fun Video.needsCollaboratorResolution(): Boolean =
    id.isNotBlank() &&
        collaborators.size < 2 &&
        (channelThumbnailUrls.size > 1 || channelName.hasLikelyCollaborationByline())

internal fun String.hasLikelyCollaborationByline(): Boolean {
    val normalized = " ${trim().lowercase(Locale.US)} "
    return normalized.contains(" & ") ||
        normalized.contains(" x ") ||
        normalized.contains(" feat. ") ||
        normalized.contains(" ft. ")
}
