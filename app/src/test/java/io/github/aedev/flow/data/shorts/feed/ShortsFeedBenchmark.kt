package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.ShortsSeedInput
import io.github.aedev.flow.data.recommendation.ShortsSeedSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * The reel feed served offline: deterministic lanes over a universe of channels, the pager run for
 * a number of pages with every served reel marked seen, and the numbers that matter read off the
 * result. The same shape as the engine's `NeuroBenchmark`: fixed seed, no network, real pipeline.
 */
internal class ShortsFeedBenchmark(
    private val channelCount: Int = 40,
    private val subscribedCount: Int = 8,
    seed: Long = 42L,
) {
    private val random = Random(seed)
    val channels = (1..channelCount).map { "UC$it" }
    val subscribed = channels.take(subscribedCount).toSet()
    private val unsubscribed = channels.drop(subscribedCount)

    val requests = AtomicInteger()
    val shown = LinkedHashSet<String>()

    private fun reel(
        id: String,
        channel: String,
        titled: Boolean,
    ) = ShortVideo(id = id, thumbnailUrl = "", channelId = channel, title = if (titled) "Reel $id" else "")

    val sources =
        object : ShortsFeedSources {
            private var explorePage = 0
            private val relatedPages = HashMap<String, Int>()
            private val tabVisits = HashMap<String, Int>()

            override suspend fun explore(continuation: String?): ShortsSourceBatch? {
                requests.incrementAndGet()
                val page = explorePage++
                val reels =
                    (0 until EXPLORE_PAGE).map { i ->
                        reel("x-$page-$i", channels[(page * 7 + i * 3) % channelCount], titled = false)
                    }
                return ShortsSourceBatch(reels, "x-${page + 1}")
            }

            override suspend fun related(
                seedId: String,
                continuation: String?,
            ): ShortsSourceBatch? {
                requests.incrementAndGet()
                val page = relatedPages.merge(seedId, 1, Int::plus)!! - 1
                if (page >= RELATED_PAGES) return ShortsSourceBatch(emptyList(), null)
                val home = seedId.hashCode().mod(unsubscribed.size)
                val reels =
                    (0 until RELATED_PAGE).map { i ->
                        reel(
                            "r-$seedId-$page-$i",
                            unsubscribed[
                                (home + i * 5 + page) %
                                    unsubscribed.size,
                            ],
                            titled = false,
                        )
                    }
                return ShortsSourceBatch(reels, if (page + 1 < RELATED_PAGES) "r-$seedId-${page + 1}" else null)
            }

            override suspend fun discovery(query: String): List<ShortVideo> {
                requests.incrementAndGet()
                return (0 until DISCOVERY_RESULTS).map { i -> reel("d-$query-$i", channels[random.nextInt(channelCount)], titled = true) }
            }

            override suspend fun subscriptionReels(): List<ShortVideo> =
                subscribed.flatMapIndexed { index, channel -> (0 until 3).map { i -> reel("s-rss-$index-$i", channel, titled = true) } }

            override suspend fun channelReels(channelId: String): List<ShortVideo> {
                requests.incrementAndGet()
                val visit = tabVisits.merge(channelId, 1, Int::plus)!! - 1
                if (visit >= TAB_VISITS) return emptyList()
                return (0 until 3).map { i -> reel("s-tab-$channelId-$visit-$i", channelId, titled = true) }
            }

            override suspend fun subscribedChannels(): List<String> = subscribed.toList()
        }

    val engine =
        object : ShortsFeedEngine {
            private var depth = 0
            private val usedSeeds = HashSet<String>()

            override suspend fun rank(
                videos: List<Video>,
                subscribedChannelIds: Set<String>,
            ): List<Video> = videos

            override suspend fun discoveryQueries(resetDepth: Boolean): List<String> {
                depth = if (resetDepth) 0 else depth + 1
                return (1..6).map { "q$depth-$it" }
            }

            override suspend fun selectSeeds(
                candidates: List<ShortsSeedInput>,
                maxSeeds: Int,
            ): List<String> =
                candidates
                    .map { it.id }
                    .filterNot { it in usedSeeds }
                    .take(maxSeeds)
                    .also { usedSeeds += it }

            override suspend fun reportQueryNovelty(
                query: String,
                novelRatio: Double,
            ) = Unit

            override suspend fun recentlyShownIds(): Set<String> = shown
        }

    val context =
        object : ShortsFeedContext {
            override suspend fun profile() = ShortsFeedProfile(subscribed, isColdStart = false)

            override suspend fun filters() = ShortsFeedFilters(seenIds = shown.toSet())

            override suspend fun seedInputs(): List<ShortsSeedInput> =
                (1..12).map { i ->
                    ShortsSeedInput("seed-$i", unsubscribed[i % unsubscribed.size], ShortsSeedSource.LIKED, timestamp = 1_700_000_000_000L)
                }
        }

    data class Report(
        val pagesServed: Int,
        val reelsServed: Int,
        val seenRepeatRate: Double,
        val uniqueServedRatio: Double,
        val maxSameChannelRun: Int,
        val channelConcentration: Double,
        val subscriptionChannelShare: Double,
        val subscriptionLaneShare: Double,
        val laneCoverage: Map<ShortsFeedLane, Double>,
        val pagesWithoutRelated: List<Int>,
        val meanRequestsPerPage: Double,
        val maxRequestsPerPage: Int,
    )

    suspend fun run(pages: Int): Report {
        val pager = ShortsFeedPager(sources, engine, context)
        val served = mutableListOf<ShortVideo>()
        val perPage = mutableListOf<List<ShortVideo>>()
        val requestsPerPage = mutableListOf<Int>()
        var repeats = 0

        fun record(page: List<ShortVideo>) {
            if (page.isEmpty()) return
            perPage += page
            page.forEach { reel ->
                if (!shown.add(reel.id)) repeats++
                served += reel
            }
        }

        record(pager.open(null))
        requestsPerPage += pager.requestsForLastPage
        repeat(pages - 1) {
            val page = pager.nextPage()
            record(page)
            requestsPerPage += pager.requestsForLastPage
        }

        var maxRun = 0
        var run = 0
        var previous = ""
        served.forEach { reel ->
            run = if (reel.channelId.isNotBlank() && reel.channelId == previous) run + 1 else 1
            maxRun = maxOf(maxRun, run)
            previous = reel.channelId
        }
        val byChannel = served.filter { it.channelId.isNotBlank() }.groupingBy { it.channelId }.eachCount()
        val concentration = byChannel.values.sumOf { (it.toDouble() / served.size).let { share -> share * share } }
        val laneOf = { reel: ShortVideo ->
            when (reel.id.substringBefore('-')) {
                "r" -> ShortsFeedLane.RELATED
                "x" -> ShortsFeedLane.EXPLORE
                "d" -> ShortsFeedLane.DISCOVERY
                else -> ShortsFeedLane.SUBSCRIPTIONS
            }
        }
        return Report(
            pagesServed = perPage.size,
            reelsServed = served.size,
            seenRepeatRate = repeats.toDouble() / served.size.coerceAtLeast(1),
            uniqueServedRatio = served.distinctBy { it.id }.size.toDouble() / served.size.coerceAtLeast(1),
            maxSameChannelRun = maxRun,
            channelConcentration = concentration,
            subscriptionChannelShare = served.count { it.channelId in subscribed }.toDouble() / served.size.coerceAtLeast(1),
            subscriptionLaneShare = served.count { laneOf(it) == ShortsFeedLane.SUBSCRIPTIONS }.toDouble() / served.size.coerceAtLeast(1),
            laneCoverage =
                ShortsFeedLane.entries.associateWith { lane ->
                    perPage.count { page -> page.any { laneOf(it) == lane } }.toDouble() / perPage.size.coerceAtLeast(1)
                },
            pagesWithoutRelated =
                perPage
                    .withIndex()
                    .filter { (_, page) ->
                        page.none { laneOf(it) == ShortsFeedLane.RELATED }
                    }.map { it.index },
            meanRequestsPerPage = requestsPerPage.drop(1).average().takeIf { !it.isNaN() } ?: 0.0,
            maxRequestsPerPage = requestsPerPage.drop(1).maxOrNull() ?: 0,
        )
    }

    private companion object {
        const val EXPLORE_PAGE = 14
        const val RELATED_PAGE = 14
        const val RELATED_PAGES = 5
        const val DISCOVERY_RESULTS = 15
        const val TAB_VISITS = 10
    }
}
