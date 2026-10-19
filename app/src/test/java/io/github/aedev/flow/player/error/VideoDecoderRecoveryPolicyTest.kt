package io.github.aedev.flow.player.error

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.error.VideoDecoderRecoveryPolicy.Decision
import org.junit.Test

class VideoDecoderRecoveryPolicyTest {
    private val decodingFailed = PlaybackException.ERROR_CODE_DECODING_FAILED

    @Test
    fun `first decoding failure re-prepares`() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun `decoder init failure re-prepares`() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(
            policy.onDecoderError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, formatHandled = true),
        ).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun `a second failure before any frame gives up`() {
        val policy = VideoDecoderRecoveryPolicy()
        policy.onDecoderError(decodingFailed, formatHandled = true)

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun `a rendered frame allows another attempt`() {
        val policy = VideoDecoderRecoveryPolicy()
        policy.onDecoderError(decodingFailed, formatHandled = true)
        policy.onFirstFrameRendered()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun `attempts per video are capped even with frames in between`() {
        val policy = VideoDecoderRecoveryPolicy(maxPerVideo = 2)
        repeat(2) {
            assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
            policy.onFirstFrameRendered()
        }

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun `a new video starts a fresh budget`() {
        val policy = VideoDecoderRecoveryPolicy(maxPerVideo = 1)
        policy.onDecoderError(decodingFailed, formatHandled = true)
        policy.reset()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun `a format the decoder cannot handle gives up`() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = false)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun `decoder query and audio track failures give up`() {
        val policy = VideoDecoderRecoveryPolicy()

        listOf(
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        ).forEach { code ->
            assertThat(policy.onDecoderError(code, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
        }
    }
}
