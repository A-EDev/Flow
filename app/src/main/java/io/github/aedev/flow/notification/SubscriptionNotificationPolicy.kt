package io.github.aedev.flow.notification

internal object SubscriptionNotificationPolicy {
    fun shouldAnnounce(
        videoId: String,
        reelVideoIds: Set<String>,
        announceReelsInFeed: Boolean,
        notifyShorts: Boolean,
    ): Boolean {
        if (videoId !in reelVideoIds) return true
        return announceReelsInFeed && notifyShorts
    }
}
