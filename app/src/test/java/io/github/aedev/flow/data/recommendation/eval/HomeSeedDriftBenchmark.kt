/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSelector
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.ui.screens.home.feedSeedInputs
import java.util.Random

/**
 * Offline model of the Home related lane across a refresh and its load-more pages, driving the real
 * seed selection ([GraphSeedSelector.selectWithLongTerm], [feedSeedInputs]).
 *
 * The world: the viewer has binged guitar this week (mostly half-watched, so few watches qualify as
 * seeds), cooked a little last month, and watched comedy for months before that (older than the 40
 * newest watches) and liked some of it. Anonymous `/next` lists mix unrelated "drift" videos into
 * every neighbour list, and a drift video's own neighbours are mostly more drift. Refreshes come 4 h
 * apart, inside the 6 h seed cooldown, so a seed used by one refresh is cooled for the next.
 * The lane's ranking is modelled as a round-robin over seeds. Two numbers answer #907:
 *  - offProfileShare: how much of what the related lane puts on screen is drift, over 8 pages
 *  - longTermCoverage: how often a refresh's related lane serves the long-term interest (comedy)
 */
internal object HomeSeedDriftBenchmark {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val NOW = 1_800_000_000_000L
    private const val RELATED_PER_SEED = 10
    private const val WAVE1_SEEDS = 4
    private const val PAGE_SEEDS = 3
    private const val PAGE_SIZE = 8
    private const val PAGES = 8
    private const val WAVE1_RELATED = 12
    private const val DISCOVERY_PER_PAGE = 3

    val interests = listOf("guitar", "cooking", "comedy")
    private val drift = listOf("phonk", "celebrity", "prank", "lottery")

    private val topicScores = mapOf("guitar" to 0.30, "cooking" to 0.35, "comedy" to 0.50)
    private val communityMass = mapOf("guitar" to 0.30, "cooking" to 0.35, "comedy" to 0.50)
    private val communityOf: (String) -> String = { it }

    data class Policy(
        val name: String,
        val excludeRelatedPicks: Boolean,
        val longTermSlot: Boolean,
    )

    val legacy = Policy("legacy (feed picks re-seed, no long-term slot)", excludeRelatedPicks = false, longTermSlot = false)
    val current = Policy("current", excludeRelatedPicks = true, longTermSlot = true)

    data class Result(
        val policy: Policy,
        val offProfileShare: Double,
        val offProfileLastPages: Double,
        val longTermCoverage: Double,
    )

    fun run(
        policy: Policy,
        sessions: Int = 8,
    ): Result {
        var relatedServed = 0
        var driftServed = 0
        var lastPagesServed = 0
        var lastPagesDrift = 0
        var sessionsWithComedy = 0
        var previousSessionSeeds = emptySet<String>()
        repeat(sessions) { session ->
            val random = Random(1_000L + session)
            val history = historyInputs()
            val longTerm = if (policy.longTermSlot) longTermInputs() else emptyList()
            val usedSeeds = HashSet(previousSessionSeeds)
            val sessionSeeds = HashSet<String>()

            val wave1Seeds = pickSeeds(history, WAVE1_SEEDS, longTerm, usedSeeds).also { sessionSeeds += it }
            val wave1Related = interleave(wave1Seeds.map { related(it, random) }).take(WAVE1_RELATED)
            if (wave1Related.any { groupOf(it) == "comedy" }) sessionsWithComedy++

            val feed = (wave1Related + discovery(random, 20)).toMutableList()
            val relatedPicks = wave1Related.mapTo(HashSet()) { it.id }
            relatedServed += wave1Related.size
            driftServed += wave1Related.count { groupOf(it) in drift }

            for (page in 1..PAGES) {
                val feedSeeds =
                    feedSeedInputs(
                        feed.reversed(),
                        NOW,
                        max = 30,
                        relatedPickIds = if (policy.excludeRelatedPicks) relatedPicks else emptySet(),
                    )
                val inputs = (history + likedInputs() + feedSeeds).distinctBy { it.id }
                val seeds = pickSeeds(inputs, PAGE_SEEDS, emptyList(), usedSeeds).also { sessionSeeds += it }
                val onScreen = feed.mapTo(HashSet()) { it.id }
                val pageRelated =
                    interleave(seeds.map { related(it, random) })
                        .filter { it.id !in onScreen }
                        .take(PAGE_SIZE - DISCOVERY_PER_PAGE)
                feed += pageRelated
                feed += discovery(random, DISCOVERY_PER_PAGE)
                relatedPicks += pageRelated.map { it.id }
                relatedServed += pageRelated.size
                driftServed += pageRelated.count { groupOf(it) in drift }
                if (page > PAGES / 2) {
                    lastPagesServed += pageRelated.size
                    lastPagesDrift += pageRelated.count { groupOf(it) in drift }
                }
            }
            previousSessionSeeds = sessionSeeds
        }
        return Result(
            policy = policy,
            offProfileShare = driftServed.toDouble() / relatedServed.coerceAtLeast(1),
            offProfileLastPages = lastPagesDrift.toDouble() / lastPagesServed.coerceAtLeast(1),
            longTermCoverage = sessionsWithComedy.toDouble() / sessions,
        )
    }

    /** The engine's six-hour seed cooldown, collapsed to "not twice in one session". */
    private fun pickSeeds(
        inputs: List<GraphSeedInput>,
        max: Int,
        longTerm: List<GraphSeedInput>,
        used: MutableSet<String>,
    ): List<String> =
        GraphSeedSelector
            .selectWithLongTerm(
                candidates = inputs,
                maxSeeds = max,
                longTermCandidates = longTerm,
                communityMass = communityMass,
                communityOf = communityOf,
                now = NOW,
                cooledIds = used,
                topicScores = topicScores,
            ).also { used += it }

    private fun historyInputs(): List<GraphSeedInput> =
        (0 until 10).map { watch("guitar", it, daysAgo = it / 3 + 1) } +
            (10 until 36).map { watch("guitar", it, daysAgo = it / 5 + 1, percent = 20.0) } +
            (0 until 4).map { watch("cooking", it, daysAgo = 20 + it) }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = LinkedHashSet<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out.toList()
    }

    private fun longTermInputs(): List<GraphSeedInput> = likedInputs() + (0 until 60).map { watch("comedy", it, daysAgo = 60 + it * 2) }

    private fun likedInputs(): List<GraphSeedInput> =
        (0 until 5).map {
            GraphSeedInput(
                id = "comedy-liked-$it",
                title = "comedy standup special $it",
                channelId = "",
                source = GraphSeedSource.LIKED,
                engagementWeight = 1.0,
                timestamp = NOW - (120 + it) * DAY_MS,
                durationSec = 0,
                percentWatched = 0.0,
            )
        }

    private fun watch(
        group: String,
        index: Int,
        daysAgo: Int,
        percent: Double = 90.0,
    ) = GraphSeedInput(
        id = "$group-h$index",
        title = titleFor(group, index),
        channelId = "ch-$group-${index % 4}",
        source = GraphSeedSource.WATCH_HISTORY,
        engagementWeight = percent / 100.0,
        timestamp = NOW - daysAgo * DAY_MS,
        durationSec = 900,
        percentWatched = percent,
    )

    /** Anonymous /next: mostly the seed's own world, plus drift; a drift seed pulls mostly drift. */
    private fun related(
        seedId: String,
        random: Random,
    ): List<Video> {
        val seedGroup = seedId.substringBefore('-')
        return (0 until RELATED_PER_SEED)
            .map { slot ->
                val group =
                    when {
                        seedGroup in drift -> if (slot < 8) seedGroup else drift[random.nextInt(drift.size)]
                        slot < 7 -> seedGroup
                        slot < 9 -> drift[random.nextInt(drift.size)]
                        else -> interests[random.nextInt(interests.size)]
                    }
                video(group, random.nextInt(400))
            }.shuffled(random)
    }

    /** Engine search results: on-profile by construction. */
    private fun discovery(
        random: Random,
        count: Int,
    ): List<Video> = (0 until count).map { video(interests[random.nextInt(interests.size)], 1_000 + random.nextInt(400)) }

    private fun video(
        group: String,
        index: Int,
    ) = Video(
        id = "$group-v$index",
        title = titleFor(group, index),
        channelName = "ch-$group",
        channelId = "ch-$group-${index % 6}",
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1,
        uploadDate = "",
    )

    private fun titleFor(
        group: String,
        index: Int,
    ): String = "$group video number $index"

    private fun groupOf(video: Video): String = video.id.substringBefore('-')

    fun report(results: List<Result>): String =
        buildString {
            appendLine("═══ HomeSeedDriftBenchmark ═══")
            appendLine("policy | offProfileShare | offProfileLastPages | longTermCoverage")
            results.forEach { r ->
                appendLine(
                    "${r.policy.name} | ${"%.3f".format(r.offProfileShare)} | " +
                        "${"%.3f".format(r.offProfileLastPages)} | ${"%.3f".format(r.longTermCoverage)}",
                )
            }
        }
}
