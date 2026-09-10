package io.github.aedev.flow.data.engagement

import android.util.Log
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.InteractionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val TAG = "VideoEngagement"

/** Everything a surface shows about the viewer's relationship with one video and its channel. */
data class VideoEngagement(
    val isSubscribed: Boolean = false,
    val isNotificationEnabled: Boolean = false,
    val likeState: String? = null,
)

/**
 * Subscribe, notification and like/dislike for one video, with the learning signals each action
 * owes the recommendation engine.
 *
 * The player, Shorts and the quick-actions sheet all performed the same writes followed by the
 * same [VideoEngagementSignals] calls; this is that sequence, once. It holds no state of its own —
 * every read is a cold flow and every write is a suspend call — so it is unscoped and the caller's
 * own scope owns any collection.
 *
 * Mutators take an `onApplied` callback rather than returning: the local write must be reflected in
 * the UI immediately, and the signals that follow it include a network channel-tag fetch, so a
 * caller that updated its state on return would leave the button stale for the length of that
 * fetch.
 */
class VideoEngagementUseCase
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val likedVideosRepository: LikedVideosRepository,
        private val signals: VideoEngagementSignals,
    ) {
        fun subscriptionState(channelId: String): Flow<Boolean> = subscriptionRepository.isSubscribed(channelId)

        fun notificationState(channelId: String): Flow<Boolean> =
            subscriptionRepository.getSubscription(channelId).map { it?.isNotificationEnabled ?: false }

        fun likeState(videoId: String): Flow<String?> = likedVideosRepository.getLikeState(videoId)

        /**
         * The three concerns as one flow, so a screen showing all of them holds one collector per
         * concern for as long as it is on the same video rather than re-reading them per action.
         */
        fun engagement(
            videoId: String,
            channelId: String,
        ): Flow<VideoEngagement> =
            combine(
                subscriptionState(channelId),
                notificationState(channelId),
                likeState(videoId),
            ) { isSubscribed, isNotificationEnabled, likeState ->
                VideoEngagement(
                    isSubscribed = isSubscribed,
                    isNotificationEnabled = isNotificationEnabled,
                    likeState = likeState,
                )
            }

        /** Flips the stored subscription, reporting the state it landed on. */
        suspend fun toggleSubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
            onApplied: (Boolean) -> Unit = {},
        ) {
            val isSubscribed = subscriptionRepository.isSubscribed(channelId).first()
            applySubscription(
                channelId = channelId,
                channelName = channelName,
                channelThumbnail = channelThumbnail,
                subscribed = !isSubscribed,
                onApplied = onApplied,
            )
        }

        /**
         * Writes [subscribed] for a caller that already knows which way the toggle is going — the
         * quick-actions sheet decides from its own cache and resolves the channel avatar first.
         */
        suspend fun applySubscription(
            channelId: String,
            channelName: String,
            channelThumbnail: String,
            subscribed: Boolean,
            onApplied: (Boolean) -> Unit = {},
        ) {
            if (subscribed) {
                subscriptionRepository.subscribe(
                    ChannelSubscription(
                        channelId = channelId,
                        channelName = channelName,
                        channelThumbnail = channelThumbnail,
                    ),
                )
            } else {
                subscriptionRepository.unsubscribe(channelId)
            }
            onApplied(subscribed)
            runCatching {
                signals.channelSubscriptionChanged(channelId, channelName, subscribed)
            }.onFailure { Log.w(TAG, "Failed to record subscription signal", it) }
            if (subscribed) {
                runCatching { signals.channelTagsLearned(channelId) }
            }
        }

        suspend fun setNotificationEnabled(
            channelId: String,
            enabled: Boolean,
        ) = subscriptionRepository.updateNotificationState(channelId, enabled)

        /**
         * Likes [video]. [signalVideo] is the item the engine learns from — the richest version of
         * the video the caller holds — and a null one means the action carries no learning signal.
         */
        suspend fun like(
            video: Video,
            signalVideo: Video? = null,
            onApplied: () -> Unit = {},
        ) {
            likedVideosRepository.likeVideo(
                LikedVideoInfo(
                    videoId = video.id,
                    title = video.title,
                    thumbnail = video.thumbnailUrl,
                    channelName = video.channelName,
                ),
            )
            onApplied()
            if (signalVideo == null) return
            try {
                signals.videoInteraction(signalVideo, InteractionType.LIKED)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record like signal", e)
            }
        }

        suspend fun dislike(
            videoId: String,
            signalVideo: Video? = null,
            onApplied: () -> Unit = {},
        ) {
            likedVideosRepository.dislikeVideo(videoId)
            onApplied()
            if (signalVideo == null) return
            try {
                signals.videoInteraction(signalVideo, InteractionType.DISLIKED)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record dislike", e)
            }
        }

        suspend fun removeLike(videoId: String) = likedVideosRepository.removeLikeState(videoId)
    }
