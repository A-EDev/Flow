package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayabilityVerdictTest {
    @Test
    fun `every client reporting ERROR or UNPLAYABLE means the video is gone`() {
        assertThat(
            PlayabilityVerdict.isGone(listOf("ANDROID_VR: status=ERROR, reason=Video unavailable", "IOS: status=UNPLAYABLE, reason=x")),
        ).isTrue()
    }

    @Test
    fun `a bot check, timeout or exception leaves the answer unknown`() {
        assertThat(PlayabilityVerdict.isGone(listOf("WEB: BOT_WALL, reason=Sign in", "IOS: status=ERROR, reason=x"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: timeout or null response", "WEB: status=ERROR, reason=x"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: exception=IOException: offline"))).isFalse()
    }

    @Test
    fun `a sign-in wall or other status is not treated as gone`() {
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: status=LOGIN_REQUIRED, reason=private"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: no adaptive formats"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(emptyList())).isFalse()
    }

    @Test
    fun `a bot check is told apart from an age check`() {
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm you’re not a bot")).isTrue()
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm you're not a bot")).isTrue()
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm your age")).isFalse()
        assertThat(PlayabilityVerdict.isBotWall(null)).isFalse()
    }

    @Test
    fun `a bot wall on any client blocks the network`() {
        val reasons = listOf("VISIONOS: BOT_WALL, reason=Sign in", "MWEB: timeout or null response")

        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.BOT_WALL)
    }

    @Test
    fun `a sign-in status without a bot check needs an account`() {
        val reasons = listOf("VISIONOS: status=LOGIN_REQUIRED, reason=Sign in to confirm your age")

        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.LOGIN_REQUIRED)
    }

    @Test
    fun `other failures carry no block`() {
        assertThat(PlayabilityVerdict.block(listOf("IOS: status=ERROR, reason=x", "WEB: timeout or null response"))).isNull()
        assertThat(PlayabilityVerdict.block(emptyList())).isNull()
    }
}
