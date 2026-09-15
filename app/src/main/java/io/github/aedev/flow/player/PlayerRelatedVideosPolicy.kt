package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video

object PlayerRelatedVideosPolicy {
    fun select(
        videoId: String,
        primary: List<Video>,
        fallback: List<Video>,
        current: List<Video>,
        shortsEnabled: Boolean = true,
        blockedChannelIds: Set<String> = emptySet(),
    ): List<Video> =
        sequenceOf(primary, fallback, current)
            .map { candidates -> sanitize(videoId, candidates, shortsEnabled, blockedChannelIds) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

    /**
     * [blockedChannelIds] drops a blocked creator's videos here the way search and the home feed
     * already drop them. It matters beyond the cards on screen: this same list seeds autoplay and
     * the queue, so leaving a blocked channel in it would keep playing them.
     */
    fun sanitize(
        videoId: String,
        candidates: List<Video>,
        shortsEnabled: Boolean = true,
        blockedChannelIds: Set<String> = emptySet(),
    ): List<Video> =
        candidates
            .filter { it.id.isNotBlank() && it.id != videoId }
            .filter { shortsEnabled || !it.isShort }
            .filter { it.channelId.isBlank() || it.channelId !in blockedChannelIds }
            .distinctBy { it.id }
}
