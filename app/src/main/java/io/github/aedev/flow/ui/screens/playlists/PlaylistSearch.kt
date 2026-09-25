package io.github.aedev.flow.ui.screens.playlists

import io.github.aedev.flow.data.model.Video
import java.text.Normalizer
import java.util.Locale

private val Whitespace = Regex("""\s+""")
private val CombiningMarks = Regex("""\p{Mn}+""")

/**
 * The videos whose title or channel holds every word of [query], in playlist order. Case and
 * accents are ignored, so "cafe" finds "Café".
 */
internal fun List<Video>.matchingSearch(query: String): List<Video> {
    val terms =
        query
            .trim()
            .split(Whitespace)
            .filter(String::isNotEmpty)
            .map(::foldForSearch)
    if (terms.isEmpty()) return this
    return filter { video ->
        val text = foldForSearch("${video.title} ${video.channelName}")
        terms.all { it in text }
    }
}

private fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(CombiningMarks, "").lowercase(Locale.ROOT)
