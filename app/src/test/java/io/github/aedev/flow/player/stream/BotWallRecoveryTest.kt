package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BotWallRecoveryTest {
    private val wall = "WEB: BOT_WALL, reason=Sign in to confirm you're not a bot"

    @Test
    fun `every client bot-walled rotates and retries`() {
        val reasons = listOf(wall, "MWEB: BOT_WALL, reason=x", "ANDROID_VR: BOT_WALL, reason=x")

        assertThat(BotWallRecovery.shouldRotateAndRetry(reasons, 0, null)).isTrue()
    }

    @Test
    fun `bot walls mixed with timeouts still count as a viewer block`() {
        val reasons = listOf(wall, "IOS: timeout or null response", "TV: exception=IOException: offline")

        assertThat(BotWallRecovery.shouldRotateAndRetry(reasons, 0, null)).isTrue()
    }

    @Test
    fun `a video-level status beside a bot wall does not rotate`() {
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall, "IOS: status=LOGIN_REQUIRED, reason=age"), 0, null)).isFalse()
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall, "IOS: status=UNPLAYABLE, reason=x"), 0, null)).isFalse()
    }

    @Test
    fun `no bot wall never rotates`() {
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf("IOS: timeout or null response"), 0, null)).isFalse()
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf("IOS: status=ERROR, reason=x"), 0, null)).isFalse()
        assertThat(BotWallRecovery.shouldRotateAndRetry(emptyList(), 0, null)).isFalse()
    }

    @Test
    fun `an active cooldown blocks rotation`() {
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall), 0, 1_000L)).isFalse()
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall), 0, BotWallRecovery.ROTATION_COOLDOWN_MS - 1)).isFalse()
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall), 0, BotWallRecovery.ROTATION_COOLDOWN_MS)).isTrue()
    }

    @Test
    fun `a load that already retried does not retry again`() {
        assertThat(BotWallRecovery.shouldRotateAndRetry(listOf(wall), 1, null)).isFalse()
    }
}
