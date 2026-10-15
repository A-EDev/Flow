/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.NeuroCategoryStore
import io.github.aedev.flow.data.recommendation.NeuroCoWatchStore
import io.github.aedev.flow.data.recommendation.NeuroKeySpec
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.snapshot.HomeSnapshot
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotRelated
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotSearch
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotSeed
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotVideo
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File

/**
 * Home snapshots replayed by [HomeReplayTest]. Real ones are recorded in Settings › Diagnostics
 * and kept out of git under notes/neuro/replay; the tech viewer below is built in code so the
 * harness always runs.
 */
internal object HomeReplayFixtures {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val SEARCH_ON_TOPIC = 8
    private const val SEARCH_BRIDGES = 2
    private const val SEARCH_OTHER = 2
    private const val RELATED_ON_TOPIC = 15
    private const val RELATED_BRIDGES = 2
    private const val RELATED_OTHER = 3
    private const val SEEDS = 4

    data class Fixture(
        val name: String,
        val snapshot: HomeSnapshot,
        val labels: HomeReplay.Labels,
    )

    /** Real snapshots: `<name>.json.gz` with `<name>.labels.json` beside it. */
    val replayDirectory = File("../notes/neuro/replay")

    private val labelJson = Json { ignoreUnknownKeys = true }

    fun recorded(): List<Fixture> =
        replayDirectory
            .listFiles { file -> file.name.endsWith(".json.gz") }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { file ->
                val name = file.name.removeSuffix(".json.gz")
                val labels = File(replayDirectory, "$name.labels.json").takeIf { it.isFile } ?: return@mapNotNull null
                Fixture(name, HomeReplay.read(file), labelJson.decodeFromString<LabelFile>(labels.readText()).toLabels())
            }

    @kotlinx.serialization.Serializable
    private data class LabelFile(
        val onTopicVideos: List<String> = emptyList(),
        val offTopicVideos: List<String> = emptyList(),
        val onTopicChannels: List<String> = emptyList(),
    ) {
        fun toLabels() = HomeReplay.Labels(onTopicVideos.toSet(), offTopicVideos.toSet(), onTopicChannels.toSet())
    }

    // Tech titles that share no word with what the viewer watched, from channels never watched.
    private val unsharedTech =
        listOf(
            "Snapdragon 8 Elite Gen 5 benchmarks explained",
            "Best budget laptop for students 2026",
            "Android 17 beta: every new feature",
            "Building a quiet gaming PC under 1000 dollars",
            "Sony WH-1000XM6 headphones",
            "Apple Watch Ultra 3 vs Garmin Fenix 8",
            "Why is my phone charging so slowly?",
            "Framework Laptop 13 repairability teardown",
            "ChatGPT vs Gemini on my phone",
            "M5 MacBook Pro after 30 days",
            "RTX 5070 Ti thermal throttling",
            "Is 8GB RAM enough in 2026?",
            "Linux on an old ThinkPad",
            "Unboxing the Nothing Ear 3",
            "Wear OS 6 smartwatch hands-on",
            "USB-C cables are a mess",
            "Foldable phones one year later: durability",
            "Tensor G5 chip performance deep dive",
            "Best Android launchers you are not using",
            "eSIM explained in 5 minutes",
        )

    // Titles of other subjects that share a word or two with tech titles.
    private val genericBridges =
        listOf(
            "The honest truth about my first year of marriage",
            "Worth upgrading your kitchen knives?",
            "A week in my life as a nurse",
            "Pro tips for growing tomatoes",
            "Testing viral pasta recipes",
            "My cat's camera roll",
            "Long term review of my running shoes",
            "Ultra light backpacking gear list",
            "Pixel perfect eyeliner tutorial",
            "Galaxy projector for a kids bedroom",
            "Is a pro membership worth it at Costco",
            "Honest truth about van life",
        )

    private fun video(
        id: String,
        title: String,
        channel: String,
        ageDays: Int,
        now: Long,
    ) = Video(
        id = id,
        title = title,
        channelName = channel,
        channelId = "ch-${channel.lowercase().replace(' ', '-')}",
        thumbnailUrl = "",
        duration = 840,
        viewCount = 250_000,
        uploadDate = "$ageDays days ago",
        timestamp = now - ageDays * DAY_MS,
    )

    /**
     * A viewer who watches phone reviews and nothing else (#907), with the supply YouTube gives
     * such a viewer: searches and related lists mostly on the subject, plus videos of other subjects
     * that share a word with it and some that share nothing.
     */
    fun techViewer(now: Long = System.currentTimeMillis()): Fixture =
        runBlocking {
            val brain = Issue907Scenarios.techBrain
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER)
            val queries = engine.generateDiscoveryQueries(resetDepth = true)
            engine.shutdown()

            val techSupply =
                unsharedTech.mapIndexed { i, title -> video("unshared-$i", title, "Hardware Desk ${i % 9}", 1 + i % 6, now) } +
                    NeuroLearningBenchmark.pool().filter { NeuroLearningBenchmark.familyOf(it.id) == NeuroLearningBenchmark.Family.TECH } +
                    (200 until 300).map { NeuroLearningBenchmark.tech(it) }
            val onTopic = ArrayDeque(techSupply)
            val bridges =
                ArrayDeque(
                    Issue907Scenarios.bridges() +
                        genericBridges.mapIndexed { i, title -> video("generic-$i", title, "Everyday Studio ${i % 8}", 2 + i % 5, now) },
                )
            val others = ArrayDeque((300 until 400).map { NeuroLearningBenchmark.other(it) })

            fun ArrayDeque<Video>.take(count: Int) = List(count) { removeFirstOrNull() }.filterNotNull()

            val searches =
                queries.map { query ->
                    SnapshotSearch(
                        query,
                        (onTopic.take(SEARCH_ON_TOPIC) + bridges.take(SEARCH_BRIDGES) + others.take(SEARCH_OTHER)).map(SnapshotVideo::of),
                    )
                }
            val seeds = (0 until SEEDS).map { NeuroLearningBenchmark.tech(it) }
            val related =
                seeds.flatMap { seed ->
                    (onTopic.take(RELATED_ON_TOPIC) + bridges.take(RELATED_BRIDGES) + others.take(RELATED_OTHER))
                        .mapIndexed { rank, video -> SnapshotRelated(SnapshotVideo.of(video), seed.id, 1.0, rank, "tech", 20) }
                }
            val subscriptionFeed =
                listOf("MKBHD", "Dave2D").flatMapIndexed { c, channel ->
                    (0 until 4).map { i ->
                        video(
                            "sub-$c-$i",
                            "${listOf("Pixel 10a", "Galaxy Tab S11", "iPad mini 8", "Xperia 1 VII")[i]} first look",
                            channel,
                            1 + i * 3,
                            now,
                        )
                    }
                }

            Fixture(
                name = "synthetic tech viewer",
                snapshot =
                    HomeSnapshot(
                        appVersion = "test",
                        keySpec = NeuroKeySpec.VERSION,
                        createdAt = now,
                        brain = Json.encodeToJsonElement(with(NeuroStorage(mockk(relaxed = true))) { brain.toSerializable() }),
                        categories = NeuroCategoryStore.CategoryIndex(),
                        coWatch = NeuroCoWatchStore.CoWatchIndex(),
                        subscriptions = subscriptionFeed.map { it.channelId }.distinct(),
                        watched = emptyList(),
                        discovery = searches,
                        seeds = seeds.map { SnapshotSeed(it.id, it.title) },
                        related = related,
                        subscriptionFeed = subscriptionFeed.map(SnapshotVideo::of),
                        memory = emptyList(),
                        page = emptyList(),
                    ),
                labels = HomeReplay.Labels(onTopicVideos = (techSupply + subscriptionFeed).mapTo(HashSet()) { it.id }),
            )
        }
}
