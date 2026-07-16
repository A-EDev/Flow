package io.github.aedev.flow.player

object PlayerChannelMetadataPolicy {
    fun channelReferences(uploaderUrl: String?, channelId: String?): List<String> =
        listOf(uploaderUrl, channelId)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()

    fun selectAvatarUrl(
        fetchedAvatarUrl: String?,
        embeddedAvatarUrl: String?,
        currentAvatarUrl: String?
    ): String? = sequenceOf(fetchedAvatarUrl, embeddedAvatarUrl, currentAvatarUrl)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
}
