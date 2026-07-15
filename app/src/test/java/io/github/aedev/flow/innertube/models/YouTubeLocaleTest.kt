package io.github.aedev.flow.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class YouTubeLocaleTest {
    @Test
    fun systemLocaleDropsUnsupportedRegionAndUnicodeExtensions() {
        val systemLocale = Locale.forLanguageTag("en-DE-u-fw-mon-ms-metric-mu-celsius")

        assertEquals("en", normalizeYouTubeHostLanguage("system", systemLocale))
    }

    @Test
    fun explicitLocaleDropsUnsupportedRegionAndUnicodeExtensions() {
        assertEquals(
            "en",
            normalizeYouTubeHostLanguage("en-DE-u-fw-mon-ms-metric-mu-celsius"),
        )
    }

    @Test
    fun preservesSupportedRegionalLanguageVariants() {
        assertEquals("pt-BR", normalizeYouTubeHostLanguage("pt-BR"))
        assertEquals("zh-TW", normalizeYouTubeHostLanguage("zh-Hant-TW-u-nu-hanidec"))
        assertEquals("zh-HK", normalizeYouTubeHostLanguage("zh-Hant-HK"))
    }

    @Test
    fun unsupportedKabyleHostLanguageFallsBackToEnglish() {
        assertEquals("en", normalizeYouTubeHostLanguage("kab"))
    }

    @Test
    fun invalidLanguageFallsBackToEnglish() {
        assertEquals("en", normalizeYouTubeHostLanguage("und"))
    }
}
