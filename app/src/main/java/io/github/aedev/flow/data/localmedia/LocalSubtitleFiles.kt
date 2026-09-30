package io.github.aedev.flow.data.localmedia

import io.github.aedev.flow.player.stream.CaptionFormat
import java.util.Locale

/**
 * A subtitle file near a video. [path] is relative to the video's folder, such as `Movie.en.srt`
 * or `Subs/Movie/2_English.srt`; [inVideoSubfolder] marks a file inside a `Subs/<video name>/`
 * folder, which releases use to keep each episode's subtitles apart.
 */
internal data class SubtitleFileCandidate(
    val path: String,
    val inVideoSubfolder: Boolean = false,
)

/** A subtitle file that belongs to a video, with the language its name carries, or blank. */
internal data class SubtitleFileMatch(
    val path: String,
    val languageTag: String,
    val format: CaptionFormat,
) {
    val name: String get() = path.substringAfterLast('/')
}

/** Folder names that hold a release's subtitles next to its video. */
internal val SubtitleFolderNames = setOf("subs", "sub", "subtitles", "subtitle")

internal val VideoExtensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "flv", "3gp", "mpg", "mpeg")

private val Separators = Regex("[._\\-\\s\\[\\]()]+")
private val EpisodeCode = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})[ ._-]?e(\\d{1,3})(?![0-9])|(?:^|[^0-9])(\\d{1,2})x(\\d{2,3})(?![0-9])")
private val LanguageCode = Regex("^[a-zA-Z]{2,3}(?:[-_][a-zA-Z0-9]{2,4})?$")

// Markers players put beside the language that are not a language themselves.
private val NonLanguageTokens =
    setOf("sdh", "cc", "forced", "default", "full", "auto", "hi", "hearing", "impaired", "sign", "signs", "songs", "commentary")

private val Regions: Set<String> by lazy { Locale.getISOCountries().map { it.lowercase(Locale.ROOT) }.toSet() }

private val TwoLetterByThree: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { code -> runCatching { Locale(code).isO3Language }.getOrDefault(code) }
}

private val CodeByLanguageName: Map<String, String> by lazy {
    Locale
        .getISOLanguages()
        .flatMap { code ->
            val locale = Locale(code)
            listOf(locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(locale)).map { it.lowercase(Locale.ROOT) to code }
        }.filter { (name, code) -> name.isNotBlank() && name != code }
        .toMap()
}

/**
 * The subtitle files among [candidates] that belong to [videoFileName], the way other players and
 * common release layouts put them:
 * - named like the video: `Movie.srt`, `Movie.en.srt`, `Movie.en.forced.srt`, however the words are
 *   separated;
 * - the same episode code as the video, `S01E02` or `1x02`, for series named differently;
 * - inside `Subs/<video name>/`;
 * - any file, when the video is the only one in its folder ([videosInFolder]), as in a movie's own
 *   folder with an `English.srt` or a `Subs` folder.
 *
 * A file with another episode's code never matches. Files named like the video come first.
 */
internal fun matchingSubtitleFiles(
    videoFileName: String,
    candidates: List<SubtitleFileCandidate>,
    videosInFolder: Int = 2,
): List<SubtitleFileMatch> {
    val videoStem = videoFileName.substringBeforeLast('.')
    val videoWords = normalized(videoStem)
    val videoEpisode = episodeOf(videoStem)
    return candidates
        .mapNotNull { candidate ->
            val name = candidate.path.substringAfterLast('/')
            val format = CaptionFormat.ofExtension(name.substringAfterLast('.', "")) ?: return@mapNotNull null
            val stem = name.substringBeforeLast('.')
            val words = normalized(stem)
            val episode = episodeOf(stem)
            if (episode != null && videoEpisode != null && episode != videoEpisode) return@mapNotNull null
            val tail = words.removePrefix(videoWords).trim().split(' ')
            // A bare number after the name is another film ("Movie 2"), not a track of this one.
            val namedLikeVideo =
                words == videoWords ||
                    (words.startsWith("$videoWords ") && tail.all(::describesTrack) && tail.any { word -> !word.all(Char::isDigit) })
            val belongs =
                namedLikeVideo ||
                    (episode != null && episode == videoEpisode) ||
                    candidate.inVideoSubfolder ||
                    (videosInFolder == 1 && episode == null)
            if (!belongs) return@mapNotNull null
            val languageWords = if (namedLikeVideo) words.removePrefix(videoWords).trim() else words
            val rank =
                when {
                    namedLikeVideo -> 0
                    candidate.inVideoSubfolder || episode != null -> 1
                    else -> 2
                }
            rank to SubtitleFileMatch(candidate.path, subtitleLanguageOf(languageWords.split(' '), namedLikeVideo), format)
        }.sortedWith(compareBy({ it.first }, { it.second.languageTag.isNotEmpty() }, { it.second.path.lowercase(Locale.ROOT) }))
        .map { it.second }
}

/**
 * The language a subtitle file's name carries, as a BCP-47 tag, or blank. A language written out
 * (`English`, `Français`) counts anywhere; a code (`en`, `pt-BR`, `eng`) only where a name puts it
 * after the video's name, or in the last two words, so a title word is not read as a language.
 */
internal fun subtitleLanguageOf(
    words: List<String>,
    codesAnywhere: Boolean = true,
): String {
    val tokens = words.filter { it.isNotBlank() }
    tokens.firstNotNullOfOrNull { CodeByLanguageName[it.lowercase(Locale.ROOT)] }?.let { return it }
    val firstCodeIndex = if (codesAnywhere) 0 else (tokens.size - 2).coerceAtLeast(0)
    val index = (firstCodeIndex until tokens.size).firstOrNull { isLanguageCode(tokens[it]) } ?: return ""
    val tag = Locale.forLanguageTag(tokens[index].replace('_', '-'))
    val language = tag.language.lowercase(Locale.ROOT).let { TwoLetterByThree[it] ?: it }
    // "pt-BR" reaches here as "pt br", the separators being gone.
    val region = tag.country.ifEmpty { tokens.getOrNull(index + 1)?.takeIf(::isRegion).orEmpty() }
    return runCatching {
        Locale
            .Builder()
            .setLanguage(language)
            .setRegion(region)
            .build()
            .toLanguageTag()
    }.getOrDefault("")
}

/** A word that can follow a video's name in its subtitle file's name: a language, region or marker. */
private fun describesTrack(word: String): Boolean =
    word.lowercase(Locale.ROOT) in NonLanguageTokens ||
        CodeByLanguageName.containsKey(word.lowercase(Locale.ROOT)) ||
        isLanguageCode(word) ||
        isRegion(word) ||
        (word.all(Char::isDigit) && word.length <= 2)

private fun isLanguageCode(token: String): Boolean {
    if (!token.matches(LanguageCode) || token.lowercase(Locale.ROOT) in NonLanguageTokens) return false
    val language = token.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
    return language in TwoLetterByThree.values || language in TwoLetterByThree.keys
}

private fun isRegion(token: String): Boolean = token.lowercase(Locale.ROOT) in Regions || (token.length == 3 && token.all(Char::isDigit))

private fun normalized(stem: String): String =
    stem
        .lowercase(Locale.ROOT)
        .split(Separators)
        .filter { it.isNotEmpty() }
        .joinToString(" ")

private fun episodeOf(stem: String): Pair<Int, Int>? {
    val match = EpisodeCode.find(stem) ?: return null
    val (season, episode) =
        if (match.groupValues[1].isNotEmpty()) {
            match.groupValues[1] to match.groupValues[2]
        } else {
            match.groupValues[3] to match.groupValues[4]
        }
    return season.toInt() to episode.toInt()
}
