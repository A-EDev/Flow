/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.NeuroMaintenance
import io.github.aedev.flow.data.recommendation.UserBrain
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * JVM wall time of one rank() over a Home-sized pool with a mature brain. A JVM number on a desktop
 * CPU, not a device measurement: it compares code against code, nothing more. Writes
 * app/build/reports/neuro-benchmark/rank-cost.txt.
 */
class NeuroRankCostBenchmarkTest {
    private val words =
        listOf(
            "guitar",
            "comedy",
            "recipe",
            "pixel",
            "review",
            "android",
            "camera",
            "pasta",
            "drift",
            "football",
            "anime",
            "chess",
            "physics",
            "history",
            "travel",
            "mma",
            "boxing",
            "linux",
            "kotlin",
            "rust",
            "gaming",
            "minecraft",
            "piano",
            "jazz",
            "coffee",
            "baking",
            "budget",
            "investing",
            "cars",
            "engine",
            "hiking",
            "camping",
            "fishing",
            "drawing",
            "painting",
            "lego",
            "space",
            "rocket",
            "nasa",
            "biology",
        )

    private fun brain(random: Random): UserBrain {
        val topics = HashMap<String, Double>()
        while (topics.size < 200) {
            val a = words[random.nextInt(words.size)]
            val key = if (random.nextBoolean()) a else "$a ${words[random.nextInt(words.size)]}"
            topics[key] = 0.03 + random.nextDouble() * 0.6
        }
        val affinities = HashMap<String, Double>()
        while (affinities.size < 500) {
            val (a, b) = listOf(words[random.nextInt(words.size)], words[random.nextInt(words.size)]).sorted()
            if (a != b) affinities["$a|$b"] = random.nextDouble() * 0.5
        }
        // A mature brain's IDF table: every word and pair it has ever learned from.
        val idf = HashMap<String, Int>()
        while (idf.size < 15_000) idf["w${idf.size}"] = 1 + random.nextInt(40)
        return UserBrain(
            schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION,
            totalInteractions = 2_000,
            globalVector = ContentVector(topics = topics),
            topicAffinities = affinities,
            idfWordFrequency = idf,
            idfTotalDocuments = 3_000,
        )
    }

    private fun pool(random: Random): List<Video> =
        (0 until 600).map { i ->
            Video(
                id = "v$i",
                title = (0 until 8).joinToString(" ") { words[random.nextInt(words.size)] },
                channelName = "Channel ${i % 40}",
                channelId = "ch${i % 40}",
                thumbnailUrl = "",
                duration = 600,
                viewCount = 100_000,
                uploadDate = "2 days ago",
            )
        }

    @Test
    fun `rank cost - report`() {
        val random = Random(42)
        val engine = NeuroLearningBenchmark.engine(brain(random))
        val pool = pool(random)
        val timings =
            runBlocking {
                repeat(5) { engine.rank(pool, emptySet()) }
                (0 until 20).map {
                    val start = System.nanoTime()
                    engine.rank(pool, emptySet())
                    (System.nanoTime() - start) / 1_000_000.0
                }
            }.sorted()
        engine.shutdown()
        val report =
            "RANK COST (600 candidates, 200-topic brain, 500 affinities, 15k IDF words; JVM, 20 runs after 5 warm-ups)\n" +
                "  median ms = %.1f\n".format(timings[timings.size / 2]) +
                "  p90 ms    = %.1f\n".format(timings[(timings.size * 9) / 10])
        println(report)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "rank-cost.txt").writeText(report)
    }
}
