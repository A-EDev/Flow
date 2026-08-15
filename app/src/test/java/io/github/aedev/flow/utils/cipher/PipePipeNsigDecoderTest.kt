package io.github.aedev.flow.utils.cipher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PipePipeNsigDecoderTest {
    private val streamUrl =
        "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1754600000&ei=xyz" +
            "&n=Q7dK2mLpR9vTs&itag=137&mime=video%2Fmp4&ratebypass=yes"

    @Test
    fun `reads the n parameter out of a stream url`() {
        assertThat(PipePipeNsigDecoder.rawN(streamUrl)).isEqualTo("Q7dK2mLpR9vTs")
    }

    @Test
    fun `a url with no n parameter reports none`() {
        val noThrottleParam = "https://rr3---sn-abc.googlevideo.com/videoplayback?itag=140&mime=audio%2Fmp4"

        assertThat(PipePipeNsigDecoder.rawN(noThrottleParam)).isNull()
    }

    @Test
    fun `percent-encoded n parameters are decoded`() {
        val encoded = "https://host/videoplayback?n=a%2Bb%3Dc&itag=137"

        assertThat(PipePipeNsigDecoder.rawN(encoded)).isEqualTo("a+b=c")
    }

    @Test
    fun `replacing n leaves every other query parameter untouched`() {
        val replaced = PipePipeNsigDecoder.replaceNParam(streamUrl, "DECODED")

        assertThat(replaced).isEqualTo(
            "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1754600000&ei=xyz" +
                "&n=DECODED&itag=137&mime=video%2Fmp4&ratebypass=yes",
        )
    }

    @Test
    fun `a replacement n is re-encoded so the query stays parseable`() {
        val replaced = PipePipeNsigDecoder.replaceNParam("https://host/videoplayback?n=old&itag=137", "a+b=c")

        assertThat(replaced).isEqualTo("https://host/videoplayback?n=a%2Bb%3Dc&itag=137")
    }

    @Test
    fun `replacement survives n being the first parameter`() {
        val replaced = PipePipeNsigDecoder.replaceNParam("https://host/videoplayback?n=old&itag=137", "new")

        assertThat(replaced).isEqualTo("https://host/videoplayback?n=new&itag=137")
    }

    @Test
    fun `a round trip through read and replace is stable`() {
        val replaced = PipePipeNsigDecoder.replaceNParam(streamUrl, "NEWVALUE")

        assertThat(PipePipeNsigDecoder.rawN(replaced)).isEqualTo("NEWVALUE")
    }
}
