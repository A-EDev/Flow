package io.github.aedev.flow.innertube.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The number reading and suffix matching, against a hand-kept table so it runs on the plain JVM. */
class YouTubeCountParserTest {
    private val fakeSuffixes =
        CompactSuffixSource { hl ->
            when (hl) {
                "es" -> mapOf("mil" to 1_000L, "m" to 1_000_000L)
                "de" -> mapOf("mio." to 1_000_000L, "mrd." to 1_000_000_000L)
                "tr" -> mapOf("b" to 1_000L, "mn" to 1_000_000L)
                "ja" -> mapOf("万" to 10_000L, "億" to 100_000_000L)
                "ko" -> mapOf("천" to 1_000L, "만" to 10_000L)
                else -> emptyMap()
            }
        }

    private fun parse(
        text: String?,
        hl: String?,
    ) = YouTubeCountParser.parse(text, hl, fakeSuffixes)

    @Test
    fun `exact counts read the same in every language`() {
        assertThat(parse("1,013,511 views", "en")).isEqualTo(1_013_511L)
        assertThat(parse("1.013.511 visualizaciones", "es")).isEqualTo(1_013_511L)
        assertThat(parse("1 013 511 vues", "fr")).isEqualTo(1_013_511L)
        assertThat(parse("334.435 Aufrufe", "de")).isEqualTo(334_435L)
        assertThat(parse("24,88,417 व्यूज़", "hi")).isEqualTo(2_488_417L)
        assertThat(parse("조회수 1,013,511회", "ko")).isEqualTo(1_013_511L)
        assertThat(parse("1 view", "en")).isEqualTo(1L)
    }

    @Test
    fun `a decimal comma is not a thousands separator`() {
        assertThat(parse("2,4 M de visualizaciones", "es")).isEqualTo(2_400_000L)
        assertThat(parse("2,4 Mio. Aufrufe", "de")).isEqualTo(2_400_000L)
        assertThat(parse("2.4M views", "en")).isEqualTo(2_400_000L)
    }

    @Test
    fun `the host language's suffix wins over the english letter`() {
        assertThat(parse("334 B görüntüleme", "tr")).isEqualTo(334_000L)
        assertThat(parse("1.2B views", "en")).isEqualTo(1_200_000_000L)
    }

    @Test
    fun `english letters still read where YouTube uses them in another language`() {
        assertThat(parse("334 K visualizaciones", "es")).isEqualTo(334_000L)
        assertThat(parse("334K visualizzazioni", "it")).isEqualTo(334_000L)
    }

    @Test
    fun `an alphabetic suffix must end its word`() {
        assertThat(parse("334 mil visualizaciones", "es")).isEqualTo(334_000L)
        assertThat(parse("334 mil visualizações", "xx")).isEqualTo(334L)
    }

    @Test
    fun `han and hangul suffixes run into the next word`() {
        assertThat(parse("33万回視聴", "ja")).isEqualTo(330_000L)
        assertThat(parse("조회수 33만회", "ko")).isEqualTo(330_000L)
    }

    @Test
    fun `a digit in any script counts`() {
        assertThat(parse("٣٣٤ views", "en")).isEqualTo(334L)
    }

    @Test
    fun `text without a number is no count`() {
        assertThat(parse(null, "en")).isNull()
        assertThat(parse("No views", "en")).isNull()
        assertThat(parseYouTubeViewCount("Sin visualizaciones", "es")).isEqualTo(0L)
    }
}
