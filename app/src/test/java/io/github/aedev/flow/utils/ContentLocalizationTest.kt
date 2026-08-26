package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentLocalizationTest {
    @Test
    fun `the app language drives the extraction language`() {
        assertThat(newPipeLocalization("fr").languageCode).isEqualTo("fr")
        assertThat(newPipeLocalization("fr-FR").languageCode).isEqualTo("fr")
    }

    @Test
    fun `a language without time-ago patterns falls back to english`() {
        assertThat(newPipeLocalization("ga").languageCode).isEqualTo("en")
    }

    @Test
    fun `a country without time-ago patterns keeps the language`() {
        val localization = newPipeLocalization("pt-BR")

        assertThat(localization.languageCode).isEqualTo("pt")
        assertThat(localization.countryCode).isEmpty()
    }

    @Test
    fun `blank region falls back instead of sending an invalid gl`() {
        assertThat(normalizeYouTubeCountry("  ")).matches("[A-Z]{2}")
        assertThat(normalizeYouTubeCountry("fr")).isEqualTo("FR")
    }
}
