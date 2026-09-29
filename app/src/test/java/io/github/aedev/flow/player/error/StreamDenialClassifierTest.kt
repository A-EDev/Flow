package io.github.aedev.flow.player.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rules that decide what a 403 meant. Reading them wrong is what produced the bug this class
 * exists for: every refusal was reported as an expired URL, so the recovery re-minted under the
 * client that had just been refused and then blamed expiry for the loop.
 */
class StreamDenialClassifierTest {
    private val now = 1_800_000_000L

    @Test
    fun `a passed deadline is an expired url`() {
        assertThat(classify(expire = now - 60)).isEqualTo(StreamDenialKind.URL_EXPIRED)
    }

    @Test
    fun `a deadline inside the skew grace still counts as expired`() {
        assertThat(classify(expire = now + 5)).isEqualTo(StreamDenialKind.URL_EXPIRED)
    }

    @Test
    fun `a valid deadline with no pot is an enforced attestation`() {
        assertThat(classify(expire = now + 21_600)).isEqualTo(StreamDenialKind.ATTESTATION_GATED)
    }

    @Test
    fun `an empty pot parameter is not an attestation`() {
        val url = "https://rr1.googlevideo.com/videoplayback?expire=${now + 21_600}&pot=&c=MWEB"
        assertThat(StreamDenialClassifier.classify(url, now)).isEqualTo(StreamDenialKind.ATTESTATION_GATED)
    }

    @Test
    fun `a valid deadline with a pot means the token itself was refused`() {
        assertThat(classify(expire = now + 21_600, pot = "AbCd")).isEqualTo(StreamDenialKind.TOKEN_REJECTED)
    }

    @Test
    fun `a url with no deadline cannot be classified`() {
        assertThat(StreamDenialClassifier.classify("https://rr1.googlevideo.com/videoplayback?itag=140", now))
            .isEqualTo(StreamDenialKind.UNKNOWN)
        assertThat(StreamDenialClassifier.classify(null, now)).isEqualTo(StreamDenialKind.UNKNOWN)
        assertThat(StreamDenialClassifier.classify("", now)).isEqualTo(StreamDenialKind.UNKNOWN)
        assertThat(StreamDenialClassifier.classify("https://rr1.googlevideo.com/videoplayback", now))
            .isEqualTo(StreamDenialKind.UNKNOWN)
    }

    @Test
    fun `a non numeric deadline cannot be classified`() {
        assertThat(StreamDenialClassifier.classify("https://gvs/v?expire=soon&c=IOS", now))
            .isEqualTo(StreamDenialKind.UNKNOWN)
    }

    @Test
    fun `the minting client and itag are read back from the url`() {
        val url = "https://rr1.googlevideo.com/videoplayback?expire=${now + 100}&itag=140&c=visionos"
        assertThat(StreamDenialClassifier.clientOf(url)).isEqualTo("VISIONOS")
        assertThat(StreamDenialClassifier.itagOf(url)).isEqualTo("140")
        assertThat(StreamDenialClassifier.hasPoToken(url)).isFalse()
    }

    @Test
    fun `percent encoded values are decoded`() {
        val url = "https://gvs/v?expire=${now + 100}&mime=video%2Fwebm&c=MWEB"
        assertThat(StreamDenialClassifier.queryParam(url, "mime")).isEqualTo("video/webm")
    }

    @Test
    fun `a malformed escape is returned verbatim rather than throwing`() {
        val url = "https://gvs/v?expire=${now + 100}&spc=%ZZ"
        assertThat(StreamDenialClassifier.queryParam(url, "spc")).isEqualTo("%ZZ")
    }

    @Test
    fun `a parameter whose name merely ends with the query name is not matched`() {
        val url = "https://gvs/v?lsparams=a&params=b&expire=${now + 100}"
        assertThat(StreamDenialClassifier.queryParam(url, "params")).isEqualTo("b")
    }

    @Test
    fun `a fragment is not read as part of the last value`() {
        val url = "https://gvs/v?c=MWEB#expire=1"
        assertThat(StreamDenialClassifier.queryParam(url, "c")).isEqualTo("MWEB")
        assertThat(StreamDenialClassifier.classify(url, now)).isEqualTo(StreamDenialKind.UNKNOWN)
    }

    @Test
    fun `the expiry description names which side of the deadline the url is on`() {
        assertThat(StreamDenialClassifier.describeExpiry("https://gvs/v?expire=${now + 600}", now))
            .isEqualTo("expire=valid 600s left")
        assertThat(StreamDenialClassifier.describeExpiry("https://gvs/v?expire=${now - 600}", now))
            .isEqualTo("expire=PASSED 600s ago")
        assertThat(StreamDenialClassifier.describeExpiry("https://gvs/v?itag=140", now)).isEqualTo("expire=absent")
    }

    private fun classify(
        expire: Long,
        pot: String? = null,
    ): StreamDenialKind {
        val potPart = pot?.let { "&pot=$it" }.orEmpty()
        return StreamDenialClassifier.classify(
            "https://rr1.googlevideo.com/videoplayback?expire=$expire&itag=140&c=VISIONOS$potPart",
            now,
        )
    }
}
