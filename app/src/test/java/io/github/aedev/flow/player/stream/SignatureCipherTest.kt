package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignatureCipherTest {
    private val cipher =
        "s=00%3Dg%3D%3DgcjUAvr&sp=sig&url=https%3A%2F%2Frr6.googlevideo.com%2Fvideoplayback%3Fitag%3D140%26c%3DTVHTML5%26n%3Di-ml"

    @Test
    fun `the url, scrambled signature and parameter are read decoded`() {
        val parsed = SignatureCipher.parse(cipher)!!

        assertThat(parsed.url).isEqualTo("https://rr6.googlevideo.com/videoplayback?itag=140&c=TVHTML5&n=i-ml")
        assertThat(parsed.signature).isEqualTo("00=g==gcjUAvr")
        assertThat(parsed.parameter).isEqualTo("sig")
    }

    @Test
    fun `the solved signature is appended encoded under its parameter`() {
        val signed = SignatureCipher.parse(cipher)!!.signedUrl("AE0s+JYw/RQ==")

        assertThat(signed).endsWith("&n=i-ml&sig=AE0s%2BJYw%2FRQ%3D%3D")
    }

    @Test
    fun `a cipher with no sp parameter signs as signature`() {
        val parsed = SignatureCipher.parse("s=abc&url=https%3A%2F%2Fhost%2Fp")!!

        assertThat(parsed.signedUrl("x")).isEqualTo("https://host/p?signature=x")
    }

    @Test
    fun `a cipher missing its url or signature is unusable`() {
        assertThat(SignatureCipher.parse("s=abc&sp=sig")).isNull()
        assertThat(SignatureCipher.parse("sp=sig&url=https%3A%2F%2Fhost")).isNull()
        assertThat(SignatureCipher.parse(null)).isNull()
        assertThat(SignatureCipher.parse("")).isNull()
    }
}
