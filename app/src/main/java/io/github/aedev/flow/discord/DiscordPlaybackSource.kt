package io.github.aedev.flow.discord

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
class DiscordPlaybackSource(
    private val videoManager: EnhancedPlayerManager = EnhancedPlayerManager.getInstance(),
    private val shortsPool: ShortsPlayerPool = ShortsPlayerPool.getInstance(),
    private val selector: DiscordPlaybackSelector = DiscordPlaybackSelector(),
) {
    val playback: Flow<PlaybackSnapshot?> = merge(
        GlobalPlayerState.currentVideo.map { Unit },
        videoManager.playerState.map { Unit },
        EnhancedMusicPlayerManager.currentTrack.map { Unit },
        EnhancedMusicPlayerManager.playerState.map { Unit },
        shortsPool.currentVideo.map { Unit },
        ticker(),
    ).map {
        selector.select(
            short = shortSnapshot(),
            video = videoSnapshot(),
            music = musicSnapshot(),
        )
    }
        // Media3 players are owned by the main application thread. The presence runtime
        // collects on IO for its gateway calls, so keep every snapshot read upstream on main.
        .flowOn(Dispatchers.Main.immediate)
        .distinctUntilChanged()

    private fun shortSnapshot(): PlaybackSnapshot? {
        val short = shortsPool.currentVideo.value ?: return null
        if (shortsPool.currentVideoId.value != short.id) return null
        return PlaybackSnapshot(
            kind = PlaybackKind.SHORT,
            mediaId = short.id,
            title = short.title,
            subtitle = short.channelName,
            artworkUrl = short.thumbnailUrl,
            positionMs = shortsPool.playbackPosition(),
            durationMs = shortsPool.playbackDuration(),
            isPlaying = shortsPool.isPlaying(),
            isLive = false,
        )
    }

    private fun videoSnapshot(): PlaybackSnapshot? {
        val video = GlobalPlayerState.currentVideo.value ?: return null
        val state = videoManager.playerState.value
        if (state.currentVideoId != video.id) return null
        val isLive = state.isLive || video.isLive
        return PlaybackSnapshot(
            kind = when {
                isLive -> PlaybackKind.LIVE
                video.isShort -> PlaybackKind.SHORT
                else -> PlaybackKind.VIDEO
            },
            mediaId = video.id,
            title = video.title,
            subtitle = video.channelName,
            artworkUrl = video.thumbnailUrl,
            positionMs = videoManager.getCurrentPosition(),
            durationMs = if (isLive) 0L else videoManager.getDuration().coerceAtLeast(0L),
            isPlaying = state.isPlaying,
            isLive = isLive,
        )
    }

    private fun musicSnapshot(): PlaybackSnapshot? {
        val track = EnhancedMusicPlayerManager.currentTrack.value ?: return null
        val state = EnhancedMusicPlayerManager.playerState.value
        return PlaybackSnapshot(
            kind = PlaybackKind.MUSIC,
            mediaId = track.videoId,
            title = track.title,
            subtitle = track.artist,
            artworkUrl = track.highResThumbnailUrl,
            positionMs = EnhancedMusicPlayerManager.getCurrentPosition(),
            durationMs = EnhancedMusicPlayerManager.getDuration().coerceAtLeast(0L),
            isPlaying = state.isPlaying,
            isLive = false,
        )
    }

    private fun ticker(): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(POSITION_SAMPLE_INTERVAL_MS)
        }
    }

    private companion object {
        const val POSITION_SAMPLE_INTERVAL_MS = 5_000L
    }
}
