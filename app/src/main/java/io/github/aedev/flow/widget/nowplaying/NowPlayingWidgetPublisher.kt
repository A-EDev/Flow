package io.github.aedev.flow.widget.nowplaying

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.widget.core.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.markNowPlayingStopped
import io.github.aedev.flow.widget.core.writeNowPlayingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the music session to the Now Playing widget: captures a snapshot on player
 * events (event-driven — never polled) and pushes it to the widget's DataStore.
 */
@Singleton
class NowPlayingWidgetPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var publishJob: Job? = null

    /** Must be called on the player's application thread (service listener callbacks are). */
    fun publish(player: Player) {
        val item = player.currentMediaItem ?: return
        val snapshot = NowPlayingSnapshot(
            mediaId = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            artworkUrl = item.mediaMetadata.artworkUri?.toString(),
            // playWhenReady (not isPlaying) so brief buffering still shows the pause glyph
            isPlaying = player.playWhenReady &&
                player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE,
            isLiked = EnhancedMusicPlayerManager.isLiked.value,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
        )
        publishJob?.cancel()
        publishJob = scope.launch {
            // Debounce bursts (transition + state + isPlaying often fire together)
            delay(150)
            context.writeNowPlayingSnapshot(snapshot)
            updatePlayerWidgets()
        }
    }

    /** Service is going away — keep the last track on the widget, but shown paused. */
    fun publishStopped() {
        publishJob?.cancel()
        publishJob = scope.launch {
            context.markNowPlayingStopped()
            updatePlayerWidgets()
        }
    }

    private suspend fun updatePlayerWidgets() {
        NowPlayingWidget().updateAll(context)
        io.github.aedev.flow.widget.turntable.TurntableWidget().updateAll(context)
    }
}
