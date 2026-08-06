package io.github.aedev.flow.notification

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** A single `<entry>` of a YouTube channel RSS feed. */
internal data class RssVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
)

/**
 * Parsing and diffing for the channel RSS feeds the subscription check polls.
 *
 * Kept separate from the worker so both halves stay unit-testable without WorkManager.
 */
internal object SubscriptionRssParser {
    private const val TAG = "SubscriptionRssParser"

    /**
     * A channel can publish several videos between two checks, so a burst is capped rather than
     * dropped to one — but not left unbounded, otherwise a stored id that has aged out of the feed
     * would announce the channel's whole backlog at once.
     */
    const val MAX_NEW_PER_CHANNEL = 5

    /** Entries in feed order (newest first). Returns empty on malformed XML. */
    fun parse(xml: String): List<RssVideo> =
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            readEntries(parser)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create the RSS parser", e)
            emptyList()
        }

    /**
     * Walks a namespace-aware parser already positioned on the feed, returning whatever was read
     * before any malformed markup. Separate from [parse] so tests can drive a real parser
     * implementation — the platform XML factory is stubbed out in local unit tests.
     */
    internal fun readEntries(parser: XmlPullParser): List<RssVideo> {
        val videos = mutableListOf<RssVideo>()
        try {
            var insideEntry = false
            var videoId: String? = null
            var title: String? = null
            var thumbnail: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            insideEntry = true
                            videoId = null
                            title = null
                            thumbnail = null
                        } else if (insideEntry) {
                            when {
                                tagName.equals("videoId", ignoreCase = true) -> {
                                    videoId = parser.nextText()
                                }

                                // The feed repeats the title inside <media:group>; keep the first.
                                tagName.equals("title", ignoreCase = true) && title == null -> {
                                    title = parser.nextText()
                                }

                                tagName.equals("thumbnail", ignoreCase = true) && thumbnail == null -> {
                                    thumbnail = parser.getAttributeValue(null, "url")
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("entry", ignoreCase = true)) {
                            insideEntry = false
                            val id = videoId
                            val entryTitle = title
                            if (!id.isNullOrEmpty() && !entryTitle.isNullOrEmpty()) {
                                videos += RssVideo(id, entryTitle, thumbnail)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS XML", e)
        }
        return videos
    }

    /**
     * The entries published since [lastVideoId] was recorded, newest first.
     *
     * A null [lastVideoId] means this channel has never been checked, so nothing is announced —
     * the caller only stores the pointer. When [lastVideoId] is no longer in the feed the backlog
     * length is unknown, so only the newest entry is announced.
     */
    fun newEntriesSince(
        entries: List<RssVideo>,
        lastVideoId: String?,
    ): List<RssVideo> {
        if (entries.isEmpty() || lastVideoId == null) return emptyList()

        val knownIndex = entries.indexOfFirst { it.id == lastVideoId }
        return when {
            knownIndex == 0 -> emptyList()
            knownIndex > 0 -> entries.take(minOf(knownIndex, MAX_NEW_PER_CHANNEL))
            else -> entries.take(1)
        }
    }
}
