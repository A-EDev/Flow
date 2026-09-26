package io.github.aedev.flow.innertube.pages

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The platform's CLDR suffixes against counts as YouTube printed them (captured 2026-09-27 from
 * search and channel responses, with the no-break spaces YouTube sends).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class IcuCompactSuffixSourceTest {
    @Test
    fun `abbreviated counts read in every language YouTube prints them in`() {
        val cases =
            listOf(
                Triple("es", "2,4 M de visualizaciones", 2_400_000L),
                Triple("es", "334 K visualizaciones", 334_000L),
                Triple("es", "1357 M de visualizaciones", 1_357_000_000L),
                Triple("fr", "334 k vues", 334_000L),
                Triple("fr", "1,2 M de vues", 1_200_000L),
                Triple("de", "1,2 Mio. Aufrufe", 1_200_000L),
                Triple("pt", "334 mil visualizações", 334_000L),
                Triple("pt", "2,4 mi de visualizações", 2_400_000L),
                Triple("it", "2,4 Mln di visualizzazioni", 2_400_000L),
                Triple("ru", "334 тыс. просмотров", 334_000L),
                Triple("ru", "1,2 млн просмотров", 1_200_000L),
                Triple("ja", "120万 回視聴", 1_200_000L),
                Triple("ko", "조회수 33만회", 330_000L),
                Triple("zh-CN", "248万次观看", 2_480_000L),
                Triple("zh-TW", "觀看次數：33萬次", 330_000L),
                Triple("hi", "3.3 लाख व्यूज़", 330_000L),
                Triple("hi", "12 लाख व्यूज़", 1_200_000L),
                Triple("ar", "334 ألف مشاهدة", 334_000L),
                Triple("ar", "2.4 مليون مشاهدة", 2_400_000L),
                Triple("tr", "334 B görüntüleme", 334_000L),
                Triple("tr", "2,4 Mn görüntüleme", 2_400_000L),
                Triple("id", "2,4 jt x ditonton", 2_400_000L),
                Triple("en", "2.4M views", 2_400_000L),
            )
        for ((hl, text, expected) in cases) {
            assertWithMessage("$hl: $text").that(YouTubeCountParser.parse(text, hl)).isEqualTo(expected)
        }
    }

    @Test
    fun `exact counts need no table`() {
        assertWithMessage("de").that(YouTubeCountParser.parse("334.435 Aufrufe", "de")).isEqualTo(334_435L)
        assertWithMessage("ru").that(YouTubeCountParser.parse("2 488 417 просмотров", "ru")).isEqualTo(2_488_417L)
    }
}
