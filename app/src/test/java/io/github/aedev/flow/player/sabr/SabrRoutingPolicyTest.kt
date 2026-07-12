package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SabrRoutingPolicyTest {

    @Test
    fun `direct ladders below the floor are upgrade candidates`() {
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(360)).isTrue()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(480)).isTrue()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(0)).isTrue()
    }

    @Test
    fun `direct ladders at or above the floor are accepted as-is`() {
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(720)).isFalse()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(1080)).isFalse()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(2160)).isFalse()
    }

    @Test
    fun `sabr is preferred only when it beats the direct ceiling`() {
        // SABR 1080p vs direct 360p -> upgrade
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 1080, 360)).isTrue()
        // SABR equal to direct -> keep direct (no needless session)
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 1080, 1080)).isFalse()
        // SABR lower than direct -> keep direct
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 720, 1080)).isFalse()
        // No SABR height -> never prefer
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 0, 0)).isFalse()
    }

    @Test
    fun `forced escalation always prefers sabr regardless of heights`() {
        // A 403/expiry reload forces SABR even if heights look equal or unknown
        assertThat(SabrRoutingPolicy.shouldPreferSabr(true, 0, 1080)).isTrue()
        assertThat(SabrRoutingPolicy.shouldPreferSabr(true, 1080, 1080)).isTrue()
    }
}
