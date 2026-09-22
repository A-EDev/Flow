package io.github.aedev.flow.player.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamExpiryRetryLimiterTest {
    private var nowMs = 10_000L

    @Test
    fun `repeated 403 for same variant gives up even when urls change`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/one"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/two"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/three"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 3, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/four"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.GiveUp(attempts = 4, limit = 3),
        )
    }

    @Test
    fun `different variant starts a fresh retry count`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/720-a", height = 720))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/720-b", height = 720))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/360", height = 360))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
    }

    @Test
    fun `debounced retry does not consume an attempt`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/720-a"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
        nowMs += 100L
        assertThat(limiter.record(context(url = "https://gvs/720-b"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Debounced,
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/720-c"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 2, limit = 3),
        )
    }

    @Test
    fun `alternating variants still terminate on the overall ceiling`() {
        val limiter = limiter()

        // The per-variant cap never fires while the variant keeps changing, so without an overall
        // ceiling an escalation that swaps between two dead clients retries forever.
        repeat(6) { attempt ->
            val decision = limiter.record(context(url = "https://gvs/$attempt", height = if (attempt % 2 == 0) 720 else 360))
            assertThat(decision).isInstanceOf(StreamExpiryRetryLimiter.Decision.Retry::class.java)
            advancePastDebounce()
        }

        assertThat(limiter.record(context(url = "https://gvs/last", height = 720))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.GiveUp(attempts = 7, limit = 6),
        )
    }

    @Test
    fun `the same quality refused by two clients is not one variant`() {
        val limiter = limiter()

        assertThat(limiter.record(context(url = "https://gvs/a", client = "VISIONOS"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
        advancePastDebounce()
        assertThat(limiter.record(context(url = "https://gvs/b", client = "MWEB"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
    }

    @Test
    fun `a reset lifts the overall ceiling too`() {
        val limiter = limiter()
        repeat(7) {
            limiter.record(context(url = "https://gvs/$it", height = if (it % 2 == 0) 720 else 360))
            advancePastDebounce()
        }
        assertThat(limiter.hasGivenUp()).isTrue()

        limiter.reset()

        assertThat(limiter.hasGivenUp()).isFalse()
        assertThat(limiter.record(context(url = "https://gvs/after-reset"))).isEqualTo(
            StreamExpiryRetryLimiter.Decision.Retry(attempt = 1, limit = 3),
        )
    }

    private fun limiter() =
        StreamExpiryRetryLimiter(
            maxConsecutiveFailures = 3,
            debounceMs = 1_500L,
            clockMs = { nowMs },
        )

    private fun context(
        url: String,
        height: Int = 720,
        client: String? = null,
    ) = StreamFailureContext(
        reason = "http-403",
        httpCode = 403,
        url = url,
        videoHeight = height,
        videoCodec = "h264",
        videoItag = "136",
        audioItag = "140",
        client = client,
    )

    private fun advancePastDebounce() {
        nowMs += 1_501L
    }
}
