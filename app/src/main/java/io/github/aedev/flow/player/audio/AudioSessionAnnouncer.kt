package io.github.aedev.flow.player.audio

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Keeps [AudioSessionRegistry] in step with one player's audio session. Since Media3 1.6 the id is
 * generated after the player is built and arrives through [onAudioSessionIdChanged], so reading
 * `audioSessionId` right after `build()` returns 0.
 *
 * Attach it in the same main-thread task that built the player, so the first id is not missed, and
 * detach it before the player is released.
 */
class AudioSessionAnnouncer(
    private val registry: AudioSessionRegistry,
    private val contentType: Int,
    private val onSessionId: (Int) -> Unit = {},
) : Player.Listener {
    private var player: Player? = null
    private var sessionId = 0

    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(this)
        onAudioSessionIdChanged(player.audioSessionId)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        registry.close(sessionId)
        sessionId = 0
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        val id = audioSessionId.coerceAtLeast(0)
        if (id == sessionId) return
        registry.close(sessionId)
        sessionId = id
        registry.open(id, contentType)
        if (player?.isPlaying == true) registry.playing(id)
        onSessionId(id)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) registry.playing(sessionId) else registry.paused(sessionId)
    }
}
