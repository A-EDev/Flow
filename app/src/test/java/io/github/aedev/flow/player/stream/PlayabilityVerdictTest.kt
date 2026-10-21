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
    fun `a web client that served formats means the video exists`() {
        val webOnly =
            listOf(
                "VISIONOS: status=UNPLAYABLE, reason=This video is not available",
                "MWEB: SABR resolved but no format survived decipher/n-transform",
                "ANDROID_VR: status=UNPLAYABLE, reason=This video is not available",
                "MOBILE: 22 formats, none resolvable (SABR-only)",
            )
        val webDemoted =
            listOf(
                "VISIONOS: status=UNPLAYABLE, reason=This video is not available",
                "all SABR clients demoted: MWEB",
                "WEB: SABR resolved but no format survived decipher/n-transform",
                "ANDROID_VR: status=UNPLAYABLE, reason=This video is not available",
            )

        assertThat(PlayabilityVerdict.isGone(webOnly)).isFalse()
        assertThat(PlayabilityVerdict.isGone(webDemoted)).isFalse()
    }

    @Test
    fun `a client skipped without asking leaves a removed video gone`() {
        val reasons =
            listOf(
                "VISIONOS: status=ERROR, reason=Video unavailable",
                "all SABR clients demoted: MWEB, WEB",
                "ANDROID_VR: status=UNPLAYABLE, reason=Video unavailable",
                "TV_TIZEN: status=ERROR, reason=Video unavailable",
            )

        assertThat(PlayabilityVerdict.isGone(reasons)).isTrue()
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
    fun `a wall on every client that answered blocks the visitor, and the network once a fresh one is walled`() {
        val reasons = listOf("VISIONOS: BOT_WALL, reason=Sign in", "MWEB: timeout or null response")

        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.BOT_WALL)
        assertThat(PlayabilityVerdict.block(reasons, freshVisitorWalled = true)).isEqualTo(PlaybackBlock.NETWORK_WALL)
        assertThat(PlayabilityVerdict.block(reasons + "IOS: status=ERROR, reason=x")).isNull()
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

    @Test
    fun `a wall on every asked client is a full wall, one that answered otherwise is not (#1354)`() {
        val walled =
            listOf(
                "ANDROID_VR: BOT_WALL, reason=Sign in to confirm you're not a bot",
                "WEB_EMBEDDED: BOT_WALL, reason=Sign in to confirm you're not a bot",
                "MWEB: PoToken unavailable (WebView missing/broken?)",
                "all SABR clients demoted: WEB",
            )
        assertThat(PlayabilityVerdict.everyClientWalled(walled)).isTrue()
        assertThat(PlayabilityVerdict.everyClientWalled(walled + "IOS: timeout or null response")).isTrue()
        assertThat(PlayabilityVerdict.everyClientWalled(walled + "IOS: status=ERROR, reason=x")).isFalse()
        assertThat(PlayabilityVerdict.everyClientWalled(listOf("MWEB: no visitorData"))).isFalse()
        assertThat(PlayabilityVerdict.everyClientWalled(emptyList())).isFalse()
    }
}
