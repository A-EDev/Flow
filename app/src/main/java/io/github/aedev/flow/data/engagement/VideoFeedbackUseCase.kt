package io.github.aedev.flow.data.engagement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.stats.LedgerAction
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import javax.inject.Inject

/** A channel a block applied to, named as the viewer knows it. */
data class BlockedChannel(
    val channelId: String,
    val channelName: String,
)

/**
 * What a video's menu tells the recommendation engine and the library: Watch later, not interested,
 * more like this, watched, and hiding a channel. Each call does the writes and the learning signal
 * that belong together, and says nothing to the viewer; the caller decides how to confirm it.
 */
class VideoFeedbackUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val playlistRepository: PlaylistRepository,
        private val viewHistory: ViewHistory,
        private val videoStats: VideoStatsRecorder,
    ) {
        /** Adds [video] to Watch later, or takes it out; returns whether it is saved afterwards. */
        suspend fun toggleWatchLater(video: Video): Boolean {
            if (playlistRepository.isInWatchLater(video.id)) {
                playlistRepository.removeFromWatchLater(video.id)
                return false
            }
            playlistRepository.addToWatchLater(video)
            videoStats.onAction(LedgerAction.SAVE)
            runCatching { FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.SAVED) }
            return true
        }

        suspend fun setWatchLater(
            video: Video,
            saved: Boolean,
        ) {
            if (saved) playlistRepository.addToWatchLater(video) else playlistRepository.removeFromWatchLater(video.id)
        }

        /**
         * Hides [video]'s channel from every feed. A live or premiere entry can carry a handle instead
         * of a channel id, so that case asks the watch page for the real id first.
         */
        suspend fun blockChannel(video: Video): BlockedChannel {
            val metadata = if (video.channelId.startsWith("UC")) null else repository.getLiveWatchMetadata(video.id)
            val channelId = metadata?.channelId.orEmpty().ifBlank { video.channelId }
            check(channelId.isNotBlank()) { "No channel id for ${video.id}" }
            FlowNeuroEngine.blockChannel(context, channelId)
            videoStats.onAction(LedgerAction.BLOCK_CHANNEL)
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.ChannelBlocked(channelId, video.id))
            return BlockedChannel(channelId, metadata?.channelName.orEmpty().ifBlank { video.channelName })
        }

        suspend fun unblockChannel(channelId: String) {
            FlowNeuroEngine.unblockChannel(context, channelId)
        }

        suspend fun markNotInterested(video: Video) {
            FlowNeuroEngine.markNotInterested(context, video)
            videoStats.onAction(LedgerAction.NOT_INTERESTED)
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.NotInterested(video.id, video.channelId))
        }

        suspend fun markInterested(video: Video) {
            FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.LIKED, percentWatched = 0f)
        }

        /** Records [video] as watched to the end, so every card shows it full and the engine learns from it. */
        suspend fun markWatched(video: Video) {
            FlowNeuroEngine.onVideoInteraction(context, video, InteractionType.WATCHED, percentWatched = 1.0f)
            val durationMs = if (video.duration > 0) video.duration * 1000L else 1000L
            viewHistory.savePlaybackPosition(
                videoId = video.id,
                position = durationMs,
                duration = durationMs,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl.ifEmpty { ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(video.id) },
                channelName = video.channelName,
                channelId = video.channelId,
                isMusic = false,
                isShort = video.isShort,
            )
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.MarkedWatched(video.id))
        }
    }
