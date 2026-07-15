package io.github.aedev.flow.innertube.models

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class YouTubeLocale(
    val gl: String, // geolocation
    val hl: String, // host language
) {
    companion object {
        val EXTRACTION = YouTubeLocale(gl = "US", hl = "en")
    }
}

internal fun normalizeYouTubeHostLanguage(
    languageTag: String,
    fallbackLocale: Locale = Locale.getDefault(),
): String {
    val candidate = languageTag.trim()
        .takeUnless { it.isBlank() || it.equals("system", ignoreCase = true) }
        ?: fallbackLocale.toLanguageTag()
    val locale = Locale.forLanguageTag(candidate.replace('_', '-'))
    val language = locale.language
        .lowercase(Locale.ROOT)
        .takeUnless { it.isBlank() || it == "und" }
        ?: "en"

    return when (language) {
        "kab" -> "en"
        "pt" -> if (locale.country.equals("BR", ignoreCase = true)) "pt-BR" else "pt"
        "zh" -> when {
            locale.country.equals("HK", ignoreCase = true) -> "zh-HK"
            locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.equals("TW", ignoreCase = true) ||
                locale.country.equals("MO", ignoreCase = true) -> "zh-TW"
            else -> "zh-CN"
        }
        else -> language
    }
}
