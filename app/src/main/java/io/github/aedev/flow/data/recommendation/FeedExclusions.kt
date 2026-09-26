package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.model.Video

/**
 * What the viewer asked not to see, read from the brain in one snapshot for the surfaces that never
 * pass through [FlowNeuroEngine.rank]: Subscriptions, the subscription Shorts queue, search, the
 * player's related list and autoplay, and the Home feed restored from cache.
 *
 * Two strengths, because not every surface should hide the same things (owner decision D5):
 * [hides] is what the viewer said outright, a video marked not interested or a blocked channel, and
 * applies everywhere, Subscriptions included. [hidesFromRecommendations] adds what the engine
 * inferred, the channels a not-interested mark suppresses for a while and blocked topics, and applies
 * only where the app chose the video rather than the viewer.
 */
class FeedExclusions(
    val suppressedVideoIds: Set<String> = emptySet(),
    val blockedChannelIds: Set<String> = emptySet(),
    val suppressedChannelIds: Set<String> = emptySet(),
    private val blockedText: (title: String, channelName: String) -> Boolean = { _, _ -> false },
) {
    val isEmpty: Boolean
        get() = suppressedVideoIds.isEmpty() && blockedChannelIds.isEmpty() && suppressedChannelIds.isEmpty()

    fun hides(video: Video): Boolean = video.id in suppressedVideoIds || hidesChannel(video.channelId)

    fun hidesChannel(channelId: String): Boolean = channelId.isNotBlank() && channelId in blockedChannelIds

    fun hidesFromRecommendations(video: Video): Boolean =
        hides(video) ||
            (video.channelId.isNotBlank() && video.channelId in suppressedChannelIds) ||
            blockedText(video.title, video.channelName)

    companion object {
        val NONE = FeedExclusions()
    }
}
