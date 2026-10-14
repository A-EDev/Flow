package io.github.aedev.flow.data.music.model

import io.github.aedev.flow.utils.foldForSearch

private val Brackets = Regex("""\s*[(\[][^)\]]*[)\]]""")
private val Featuring = Regex("""\s+(feat\.?|ft\.?|featuring)\s.*$""")
private val NonWord = Regex("""[^\p{L}\p{N}]+""")
private const val TOPIC_SUFFIX = " - topic"

/** [title] without its bracketed parts or featured artists: "Levitating (feat. DaBaby)" is "Levitating". */
fun musicBaseTitle(title: String): String =
    title
        .replace(Brackets, "")
        .replace(Featuring, "")
        .trim()

/** An artist name compared without case, accents or YouTube's " - Topic" channel suffix. */
fun musicArtistKey(name: String): String = name.foldForSearch().removeSuffix(TOPIC_SUFFIX).trim()

/**
 * A song title as the same song is titled everywhere: "The Weeknd - Blinding Lights (Official
 * Video)" and "Blinding Lights" both become "blinding lights" when [artists] holds The Weeknd.
 */
fun musicTitleKey(
    title: String,
    artists: List<String> = emptyList(),
): String {
    var folded = musicBaseTitle(title.foldForSearch())
    for (artist in artists.map(::musicArtistKey)) {
        val prefix = "$artist - "
        if (artist.isNotEmpty() && folded.startsWith(prefix)) folded = folded.removePrefix(prefix)
    }
    return folded.replace(NonWord, " ").trim()
}
