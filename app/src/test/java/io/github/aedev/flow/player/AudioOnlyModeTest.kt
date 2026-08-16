package io.github.aedev.flow.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Audio-only is what keeps playback going with the screen off, so its flags getting out of step is
 * not cosmetic: leaving video tracks enabled with no surface stalls playback, and dropping
 * audio-only on a queue advance has already caused exactly that.
 */
class AudioOnlyModeTest {
    @Test
    fun `a fresh mode plays video and needs no restore`() {
        val mode = AudioOnlyMode()

        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isFalse()
        assertThat(mode.tracksDisabled).isFalse()
        assertThat(mode.needsVideoRestore).isFalse()
    }

    @Test
    fun `restoring without a surface defers instead of dropping audio-only`() {
        val mode = AudioOnlyMode()
        mode.enter()

        assertThat(mode.restore(canRestore = false)).isFalse()

        // Still audio-only: dropping it here would re-enable video tracks with nothing to draw on.
        assertThat(mode.isActive).isTrue()
        assertThat(mode.restorePending).isTrue()
        assertThat(mode.needsVideoRestore).isTrue()
    }

    @Test
    fun `a deferred restore completes once a surface is back`() {
        val mode = AudioOnlyMode()
        mode.enter()
        mode.restore(canRestore = false)

        assertThat(mode.restore(canRestore = true)).isTrue()
        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isFalse()
        assertThat(mode.needsVideoRestore).isFalse()
    }

    @Test
    fun `new streams can keep playback audio-only across a queue advance`() {
        val mode = AudioOnlyMode()
        mode.enter()

        mode.applyStreams(keepAudioOnly = true)
        assertThat(mode.isActive).isTrue()

        mode.applyStreams(keepAudioOnly = false)
        assertThat(mode.isActive).isFalse()
    }

    @Test
    fun `disabling tracks reports only the transitions`() {
        val mode = AudioOnlyMode()

        assertThat(mode.setTracksDisabled(true)).isTrue()
        assertThat(mode.setTracksDisabled(true)).isFalse()
        assertThat(mode.tracksDisabled).isTrue()

        assertThat(mode.setTracksDisabled(false)).isTrue()
        assertThat(mode.setTracksDisabled(false)).isFalse()
        assertThat(mode.tracksDisabled).isFalse()
    }

    @Test
    fun `reset clears audio-only but a deferred restore still runs later`() {
        val mode = AudioOnlyMode()
        mode.enter()
        mode.restore(canRestore = false)

        mode.reset()

        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isTrue()
        assertThat(mode.needsVideoRestore).isTrue()
    }
}

class BackgroundHandoffPolicyTest {
    private fun needsReload(
        isSameVideo: Boolean = true,
        hasMediaItem: Boolean = true,
        playbackState: Int? = Player.STATE_READY,
        isPrepared: Boolean = true,
    ) = BackgroundHandoffPolicy.needsServiceLayerReload(isSameVideo, hasMediaItem, playbackState, isPrepared)

    @Test
    fun `a prepared player carries on playing in the background`() {
        assertThat(needsReload()).isFalse()
    }

    @Test
    fun `a player with nothing loaded is reloaded from the service layer`() {
        assertThat(needsReload(hasMediaItem = false)).isTrue()
        assertThat(needsReload(hasMediaItem = false, playbackState = null)).isTrue()
    }

    @Test
    fun `an idle or unprepared player is reloaded`() {
        assertThat(needsReload(playbackState = Player.STATE_IDLE)).isTrue()
        assertThat(needsReload(isPrepared = false)).isTrue()
    }

    @Test
    fun `a player on a different video is left alone`() {
        // Reloading here would yank playback back to the session's video mid-switch.
        assertThat(needsReload(isSameVideo = false, hasMediaItem = false)).isFalse()
        assertThat(needsReload(isSameVideo = false, isPrepared = false)).isFalse()
    }
}
