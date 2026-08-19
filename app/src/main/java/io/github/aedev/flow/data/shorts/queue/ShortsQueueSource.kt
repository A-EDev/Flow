package io.github.aedev.flow.data.shorts.queue

sealed interface ShortsQueueSource {
    fun encode(): String

    /** The algorithmic reel feed, from the top. What the Shorts tab opens. */
    data object Feed : ShortsQueueSource {
        override fun encode(): String = FEED
    }

    /** The algorithmic feed seeded from one short — a related-shorts tap, or an external link. */
    data class SeededFeed(
        val startVideoId: String,
    ) : ShortsQueueSource {
        override fun encode(): String = "$FEED:$startVideoId"
    }

    /** The user's saved Shorts, in saved order. Finite by design. */
    data class Saved(
        val startVideoId: String = "",
    ) : ShortsQueueSource {
        override fun encode(): String = if (startVideoId.isBlank()) SAVED else "$SAVED:$startVideoId"
    }

    /**
     * A channel's Shorts tab, paginated, in the sort order the tab is showing.
     *
     * [sortIndex] is the position of the chosen chip in YouTube's own sort bar rather than a named
     * order, because the bar is data-driven — the app shows whatever sorts the channel offers
     * (today Latest, Popular, Oldest) and never has to guess YouTube's params.
     */
    data class Channel(
        val channelUrl: String,
        val startVideoId: String,
        val sortIndex: Int = 0,
    ) : ShortsQueueSource {
        override fun encode(): String = "$CHANNEL:$startVideoId:$sortIndex:$channelUrl"
    }

    data class Subscriptions(
        val startVideoId: String = "",
    ) : ShortsQueueSource {
        override fun encode(): String = if (startVideoId.isBlank()) SUBSCRIPTIONS else "$SUBSCRIPTIONS:$startVideoId"
    }

    /** An in-memory list handed over by a shelf. */
    data class Snapshot(
        val token: String,
        val startVideoId: String,
    ) : ShortsQueueSource {
        override fun encode(): String = "$SNAPSHOT:$token:$startVideoId"
    }

    companion object {
        private const val FEED = "feed"
        private const val SAVED = "saved"
        private const val CHANNEL = "channel"
        private const val SUBSCRIPTIONS = "subs"
        private const val SNAPSHOT = "snap"

        /**
         * Parses what [encode] produced. Anything unrecognised — including a stale or truncated
         * descriptor after process death — falls back to [Feed] rather than failing the navigation.
         */
        fun decode(raw: String?): ShortsQueueSource {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return Feed

            val kind = value.substringBefore(':')
            val rest = if (':' in value) value.substringAfter(':') else ""

            return when (kind) {
                FEED -> {
                    if (rest.isBlank()) Feed else SeededFeed(rest)
                }

                SAVED -> {
                    Saved(rest)
                }

                SUBSCRIPTIONS -> {
                    Subscriptions(rest)
                }

                CHANNEL -> {
                    val startVideoId = rest.substringBefore(':')
                    val afterStart = rest.substringAfter(':', missingDelimiterValue = "")
                    val sortIndex = afterStart.substringBefore(':').toIntOrNull() ?: 0
                    val channelUrl = afterStart.substringAfter(':', missingDelimiterValue = "")
                    if (channelUrl.isBlank()) Feed else Channel(channelUrl, startVideoId, sortIndex)
                }

                SNAPSHOT -> {
                    val token = rest.substringBefore(':')
                    val startVideoId = rest.substringAfter(':', missingDelimiterValue = "")
                    if (token.isBlank()) Feed else Snapshot(token, startVideoId)
                }

                else -> {
                    Feed
                }
            }
        }
    }
}

/**
 * The short a queue should open on, when the source names one.
 *
 * Named distinctly from the `startVideoId` constructor properties on purpose: an extension property
 * sharing their name reads as recursive at the call site and resolves to the member silently.
 *
 * [ShortsQueueSource.Feed] and [ShortsQueueSource.Saved] have no anchor: they start at the top.
 */
val ShortsQueueSource.openAtVideoId: String?
    get() =
        when (this) {
            is ShortsQueueSource.SeededFeed -> this.startVideoId.takeIf { it.isNotBlank() }
            is ShortsQueueSource.Channel -> this.startVideoId.takeIf { it.isNotBlank() }
            is ShortsQueueSource.Snapshot -> this.startVideoId.takeIf { it.isNotBlank() }
            is ShortsQueueSource.Saved -> this.startVideoId.takeIf { it.isNotBlank() }
            is ShortsQueueSource.Subscriptions -> this.startVideoId.takeIf { it.isNotBlank() }
            ShortsQueueSource.Feed -> null
        }

/**
 * Whether running out of items should hand over to the algorithmic feed.
 *
 * Shelves, channel tabs and the subscription list all continue, so a swipe never dead-ends: #547
 * and #823 are about staying inside the chosen source while it still has Shorts to give, not about
 * stopping once it runs dry.
 *
 * Saved Shorts is the exception — it is a deliberate collection, and silently trailing
 * recommendations onto it would misrepresent it.
 */
val ShortsQueueSource.continuesIntoFeed: Boolean
    get() =
        when (this) {
            is ShortsQueueSource.Snapshot,
            is ShortsQueueSource.Channel,
            is ShortsQueueSource.Subscriptions,
            -> true

            ShortsQueueSource.Feed, is ShortsQueueSource.Saved, is ShortsQueueSource.SeededFeed -> false
        }

/**
 * Whether the queue's own items come from the algorithmic feed, and so may absorb a late discovery
 * pass. A shelf snapshot, a channel tab and saved Shorts must not — see
 * [ShortsQueueController]'s `acceptsDiscovery`.
 */
val ShortsQueueSource.isAlgorithmicFeed: Boolean
    get() =
        when (this) {
            ShortsQueueSource.Feed, is ShortsQueueSource.SeededFeed -> true

            is ShortsQueueSource.Snapshot,
            is ShortsQueueSource.Channel,
            is ShortsQueueSource.Saved,
            is ShortsQueueSource.Subscriptions,
            -> false
        }
