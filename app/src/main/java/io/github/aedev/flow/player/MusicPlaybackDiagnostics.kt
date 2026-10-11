package io.github.aedev.flow.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.github.aedev.flow.player.error.PlayerDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Puts what a "stuck loading" or "playing but silent" report needs into [PlayerDiagnostics], the log
 * a user can send from the app; Media3's EventLogger already covers logcat. The diagnostics buffer is
 * small, so only one line per song and the anomalies go in, never every state change.
 */
@UnstableApi
internal class MusicPlaybackDiagnostics(
    private val player: Player,
    private val scope: CoroutineScope,
) : AnalyticsListener {
    private var stallWatch: Job? = null

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) {
        stallWatch?.cancel()
        if (state != Player.STATE_BUFFERING) return
        val mediaId = player.currentMediaItem?.mediaId
        stallWatch =
            scope.launch {
                delay(STALL_WARNING_MS)
                if (player.playbackState != Player.STATE_BUFFERING || player.currentMediaItem?.mediaId != mediaId) return@launch
                warn(
                    "music $mediaId still buffering after ${STALL_WARNING_MS / 1000}s: position=${player.currentPosition}ms " +
                        "buffered=${player.bufferedPosition}ms loading=${player.isLoading} playWhenReady=${player.playWhenReady}",
                )
            }
    }

    override fun onTracksChanged(
        eventTime: AnalyticsListener.EventTime,
        tracks: Tracks,
    ) {
        if (tracks.isEmpty) return
        val mediaId = player.currentMediaItem?.mediaId
        val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        val format = audio?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }
        if (format == null) {
            warn("music $mediaId has no selected audio track (${tracks.groups.size} track groups)")
            return
        }
        PlayerDiagnostics.logInfo(
            TAG,
            "music $mediaId audio ${format.sampleMimeType} ${format.codecs} ${format.bitrate}bps " +
                "${format.sampleRate}Hz x${format.channelCount}",
        )
    }

    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: Exception,
    ) = warn("music audio sink error: ${audioSinkError.javaClass.simpleName}: ${audioSinkError.message}")

    override fun onAudioCodecError(
        eventTime: AnalyticsListener.EventTime,
        audioCodecError: Exception,
    ) = warn("music audio codec error: ${audioCodecError.javaClass.simpleName}: ${audioCodecError.message}")

    private fun warn(message: String) {
        Log.w(TAG, message)
        PlayerDiagnostics.logWarning(TAG, message)
    }

    private companion object {
        const val TAG = "MusicPlayback"
        const val STALL_WARNING_MS = 20_000L
    }
}
