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
}
