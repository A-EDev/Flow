package io.github.aedev.flow.player.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamExpiryRetryLimiterTest {
    private var nowMs = 10_000L

    @Test
    fun `repeated 403 for same variant gives up even when urls change`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/one"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3)
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/two"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3)
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/three"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 3, limit = 3)
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/four"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.GiveUp(attempts = 4, limit = 3)
        )
    }

    @Test
    fun `different variant starts a fresh retry count`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/720-a", height = 720))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3)
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/720-b", height = 720))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3)
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/360", height = 360))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3)
        )
    }

    @Test
    fun `debounced retry does not consume an attempt`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/720-a"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3)
        )
        nowMs += 100L
        assertThat(limiter.record(context(url = "https://gvs/720-b"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Debounced
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/720-c"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3)
        )
    }

    private fun limiter() = StreamExpiryRetryLimiter(
        maxConsecutiveFailures = 3,
        debounceMs = 1_500L,
        clockMs = { nowMs }
    )

    private fun context(url: String, height: Int = 720) = StreamFailureContext(
        reason = "http-403",
        httpCode = 403,
        url = url,
        videoHeight = height,
        videoCodec = "h264",
        videoItag = "136",
        audioItag = "140"
    )

    private fun advancePastDebounce() {
        nowMs += 1_501L
    }
}
