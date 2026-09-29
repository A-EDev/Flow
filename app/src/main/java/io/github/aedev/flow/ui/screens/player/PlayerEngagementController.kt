package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.engagement.VideoEngagementUseCase
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Subscription, notification and like state for the video the player screen is on: the writes the
 * user's taps make, and the one collector that mirrors the stored state back.
 *
 * Only one channel/video pair is observed at a time — asking again for the pair already collecting
 * is dropped, and any other pair cancels the collector rather than adding a second one.
 *
 * The learning signal each write carries comes from [richVideoFor], so a like recorded from a card
 * with nothing but a title still reaches the engine with the tags and description the screen holds.
 */
internal class PlayerEngagementController(
    private val engagement: VideoEngagementUseCase,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<VideoPlayerUiState>,
    private val richVideoFor: (String) -> Video?,
) {
    private var job: Job? = null
    private var channelId: String? = null
    private var videoId: String? = null

    fun toggleSubscription(
        channelId: String,
        channelName: String,
        channelThumbnail: String,
    ) {
        scope.launch {
            engagement.toggleSubscription(channelId, channelName, channelThumbnail) { isSubscribed ->
                state.value = state.value.copy(isSubscribed = isSubscribed)
            }
        }
    }

    fun setNotificationEnabled(
        channelId: String,
        enabled: Boolean,
    ) {
        scope.launch {
            engagement.setNotificationEnabled(channelId, enabled)
            state.value = state.value.copy(isNotificationsEnabled = enabled)
        }
    }

    fun like(
        videoId: String,
        title: String,
        thumbnail: String,
        channelName: String,
        channelId: String,
    ) {
        scope.launch {
            val liked =
                Video(
                    id = videoId,
                    title = title,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = thumbnail,
                    duration = 0,
                    viewCount = 0,
                    uploadDate = "",
                )
            engagement.like(video = liked, signalVideo = richVideoFor(videoId) ?: liked) {
                state.value = state.value.copy(likeState = "LIKED")
            }
        }
    }

    fun dislike(videoId: String) {
        scope.launch {
            engagement.dislike(videoId, signalVideo = richVideoFor(videoId)) {
                state.value = state.value.copy(likeState = "DISLIKED")
            }
        }
    }

    fun removeLike(videoId: String) {
        scope.launch {
            engagement.removeLike(videoId)
            state.value = state.value.copy(likeState = null)
        }
    }

    fun observe(
        channelId: String,
        videoId: String,
    ) {
        if (this.channelId == channelId && this.videoId == videoId && job?.isActive == true) return
        job?.cancel()
        this.channelId = channelId
        this.videoId = videoId
        job =
            scope.launch {
                engagement.engagement(videoId = videoId, channelId = channelId).collect { engaged ->
                    state.update {
                        it.copy(
                            isSubscribed = engaged.isSubscribed,
                            isNotificationsEnabled = engaged.isNotificationEnabled,
                            likeState = engaged.likeState,
                        )
                    }
                }
            }
    }
}
