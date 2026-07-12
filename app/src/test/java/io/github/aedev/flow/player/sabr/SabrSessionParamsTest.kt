package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.core.SabrCpn
import org.junit.Test

/**
 * The SABR POST needs the GVS session query params `alr=yes`, `cpn` and `rn` (PipePipe's
 * `withSabrSessionParameters`). These must be additive: never override params the player
 * response already baked into the streaming URL.
 */
class SabrSessionParamsTest {

    @Test
    fun `generated cpn uses youtube session format`() {
        val cpn = SabrCpn.generate()

        assertThat(cpn).hasLength(16)
        assertThat(cpn).matches("[A-Za-z0-9_-]{16}")
    }

    @Test
    fun `appends alr cpn and rn to a bare gvs url`() {
        val url = SabrStreamController.buildRequestUrl(
            "https://r1.googlevideo.com/videoplayback?sabr=1", "CPN1234567890ab", 3
        )
        assertThat(url).contains("alr=yes")
        assertThat(url).contains("cpn=CPN1234567890ab")
        assertThat(url).contains("rn=3")
        assertThat(url).startsWith("https://r1.googlevideo.com/videoplayback?sabr=1&")
    }

    @Test
    fun `never overrides an existing cpn or alr`() {
        val url = SabrStreamController.buildRequestUrl("https://x/vp?alr=yes&cpn=EXISTING", "NEWCPN", 1)
        assertThat(url).doesNotContain("cpn=NEWCPN")
        assertThat(url).contains("cpn=EXISTING")
        assertThat(url.split("alr=yes").size - 1).isEqualTo(1) // alr not duplicated
        assertThat(url).contains("rn=1")
    }

    @Test
    fun `omits cpn when the session has none and starts params with a question mark`() {
        val url = SabrStreamController.buildRequestUrl("https://x/vp", "", 5)
        assertThat(url).doesNotContain("cpn=")
        assertThat(url).contains("alr=yes")
        assertThat(url).contains("rn=5")
        assertThat(url).startsWith("https://x/vp?")
    }

    @Test
    fun `replaces stale rn and preserves fragment`() {
        val url = SabrStreamController.buildRequestUrl(
            "https://x/vp?sabr=1&rn=2#player", "CPN", 9
        )

        assertThat(url).contains("rn=9")
        assertThat(url).doesNotContain("rn=2")
        assertThat(url).endsWith("#player")
    }
}
