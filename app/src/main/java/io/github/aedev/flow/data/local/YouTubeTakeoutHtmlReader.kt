package io.github.aedev.flow.data.local

import java.io.Reader

// Every Takeout activity entry sits in this cell, whatever language the archive is in.
private const val TAKEOUT_ACTIVITY_MARKUP = "content-cell"
private const val READ_SIZE = 65_536
private const val OVERLAP = 2_048
private const val BATCH_SIZE = 500
private const val CHANNEL_LINK_REACH = 2_000

private val watchLinkPattern =
    Regex("""href="https://(www|music)\.youtube\.com/watch\?v=([\w-]{10,12})"[^>]*?>([^<]+)</a>""", RegexOption.IGNORE_CASE)
private val channelLinkPattern =
    Regex("""href="https://(?:www|music)\.youtube\.com/channel/([^"&\s]+)"[^>]*?>([^<]+)</a>""", RegexOption.IGNORE_CASE)
private val searchLinkPattern =
    Regex("""href="(https://(?:www|music)\.youtube\.com/(?:results|search)\?[^"]+)"""", RegexOption.IGNORE_CASE)

/** A video watched, as a Takeout activity file records it. */
internal data class TakeoutWatch(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val watchedAt: Long,
    val isMusic: Boolean,
)

/** What a Takeout activity HTML file held. [watches] counts what was handed to the batch callback. */
internal data class TakeoutHtmlActivity(
    val watches: Int,
    val searches: List<TakeoutSearch>,
)

/**
 * Streams a Takeout activity HTML file, handing the videos watched on YouTube and YouTube Music to
 * [onWatches] in batches and keeping the searches. Takeout names the watch and search history files
 * in the account's language, so both are read from every file. Neither carries a date Flow can read
 * in every language, so entries are stamped newest first from [now], as Takeout lists them. With
 * [requireActivityMarkup], a file that never shows the My Activity layout gives nothing, so other
 * HTML in the archive is ignored.
 */
internal suspend fun readTakeoutHtmlActivity(
    reader: Reader,
    now: Long,
    requireActivityMarkup: Boolean,
    onWatches: suspend (List<TakeoutWatch>) -> Unit,
): TakeoutHtmlActivity {
    var markupSeen = !requireActivityMarkup
    var watched = 0
    val batch = mutableListOf<TakeoutWatch>()
    val searches = mutableListOf<TakeoutSearch>()
    val buffer = CharArray(READ_SIZE)
    val tail = StringBuilder(OVERLAP)

    suspend fun flush() {
        if (batch.isEmpty()) return
        onWatches(batch.toList())
        batch.clear()
    }

    while (true) {
        val count = reader.read(buffer)
        if (count == -1) break
        val window = tail.toString() + String(buffer, 0, count)
        if (!markupSeen) markupSeen = window.contains(TAKEOUT_ACTIVITY_MARKUP)

        val channels = channelLinkPattern.findAll(window).toList()
        var channelIndex = 0
        for (match in watchLinkPattern.findAll(window)) {
            if (match.range.last < tail.length) continue
            while (channelIndex < channels.size && channels[channelIndex].range.first <= match.range.first) channelIndex++
            val channel = channels.getOrNull(channelIndex)?.takeIf { it.range.first - match.range.last < CHANNEL_LINK_REACH }
            batch +=
                TakeoutWatch(
                    videoId = match.groupValues[2].trim(),
                    title = match.groupValues[3].trim().unescapedHtml(),
                    channelName =
                        channel
                            ?.groupValues
                            ?.get(2)
                            ?.trim()
                            ?.unescapedHtml()
                            .orEmpty(),
                    channelId =
                        channel
                            ?.groupValues
                            ?.get(1)
                            ?.trim()
                            .orEmpty(),
                    watchedAt = now - (watched + batch.size),
                    isMusic = match.groupValues[1].equals("music", ignoreCase = true),
                )
        }

        for (match in searchLinkPattern.findAll(window)) {
            if (match.range.last < tail.length) continue
            val (query, isMusic) = takeoutSearchOf(match.groupValues[1].unescapedHtml()) ?: continue
            searches += TakeoutSearch(query, now - searches.size, isMusic)
        }

        tail.clear()
        tail.append(window, maxOf(0, window.length - OVERLAP), window.length)
        if (markupSeen && batch.size >= BATCH_SIZE) {
            watched += batch.size
            flush()
        }
    }
    if (!markupSeen) return TakeoutHtmlActivity(0, emptyList())
    watched += batch.size
    flush()
    return TakeoutHtmlActivity(watched, searches)
}

internal fun String.unescapedHtml(): String =
    replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")
        .replace("&amp;", "&")
