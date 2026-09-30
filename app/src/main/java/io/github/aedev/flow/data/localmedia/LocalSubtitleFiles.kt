package io.github.aedev.flow.data.localmedia

import io.github.aedev.flow.player.stream.CaptionFormat
import java.util.Locale

/** A subtitle file beside a video, with the language its name carries, or blank for none. */
internal data class SubtitleFileMatch(
    val name: String,
    val languageTag: String,
    val format: CaptionFormat,
)

private val LanguageToken = Regex("^[a-zA-Z]{2,3}(?:[-_][a-zA-Z0-9]{2,4})?$")

// Markers players put beside the language that are not a language themselves.
private val NonLanguageTokens = setOf("sdh", "cc", "forced", "default", "full", "auto")

private val TwoLetterByThree: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { code -> runCatching { Locale(code).isO3Language }.getOrDefault(code) }
}

/**
 * The subtitle files among [siblings] that belong to [videoFileName], the way other players find
 * them: `Movie.srt`, or `Movie.en.srt` and `Movie.en.forced.srt` with the language after the name.
 * The file named exactly like the video comes first.
 */
internal fun matchingSubtitleFiles(
    videoFileName: String,
    siblings: List<String>,
): List<SubtitleFileMatch> {
    val stem = videoFileName.substringBeforeLast('.')
    return siblings
        .mapNotNull { name ->
            val format = CaptionFormat.ofExtension(name.substringAfterLast('.', "")) ?: return@mapNotNull null
            val base = name.substringBeforeLast('.')
            if (!base.startsWith(stem, ignoreCase = true)) return@mapNotNull null
            val rest = base.substring(stem.length)
            if (rest.isNotEmpty() && !rest.startsWith('.')) return@mapNotNull null
            SubtitleFileMatch(name, subtitleLanguageOf(rest.split('.')), format)
        }.sortedWith(compareBy<SubtitleFileMatch> { it.languageTag.isNotEmpty() }.thenBy { it.name.lowercase(Locale.ROOT) })
}

/** The language a subtitle file's name carries after the video's name, as a BCP-47 tag. */
internal fun subtitleLanguageOf(tokens: List<String>): String {
    val token =
        tokens.firstOrNull { it.matches(LanguageToken) && it.lowercase(Locale.ROOT) !in NonLanguageTokens } ?: return ""
    val tag = Locale.forLanguageTag(token.replace('_', '-'))
    val language = tag.language.lowercase(Locale.ROOT).let { TwoLetterByThree[it] ?: it }
    return runCatching {
        Locale
            .Builder()
            .setLanguage(language)
            .setRegion(tag.country)
            .build()
            .toLanguageTag()
    }.getOrDefault("")
}
