/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.NeuroDiscovery
import org.junit.AfterClass
import org.junit.Test
import java.io.File

/**
 * The #907 reports, measured on the real engine. Each assertion records how the engine behaves
 * today; the change that fixes a report replaces its assertion with the target. Writes
 * app/build/reports/neuro-benchmark/issue907.txt.
 */
class Issue907ScenariosTest {
    @Test
    fun `problem 5 - a tech-only viewer and videos sharing one generic word`() {
        val r = Issue907Scenarios.techBridges()
        log(
            "P5 tech viewer: generic key share ${f(
                r.genericKeyShare,
            )} | bridges top 10 ${f(r.ranked.top10)}, 5-20 ${f(r.ranked.positions5to20)}" +
                " | jittered ${f(r.jittered.top10)}, ${f(r.jittered.positions5to20)} | after 10 skips ${f(r.afterTenSkips.top10)}," +
                " ${f(
                    r.afterTenSkips.positions5to20,
                )} | fresh bridges ${f(r.freshBridgesOldTech.top10)}, ${f(r.freshBridgesOldTech.positions5to20)}" +
                " | bored bridge outranks on-topic ${r.boredBridgeOutranksOnTopic}",
        )
        // Today: pairs such as "life after" and "long term", learned from tech titles, bridge to unrelated videos.
        assertThat(r.genericKeyShare).isAtLeast(0.4)
        assertThat(r.ranked.top10).isAtLeast(0.2)
        assertThat(r.ranked.positions5to20).isAtLeast(0.4)
        // Skipping videos never turns relevance upside down.
        assertThat(r.boredBridgeOutranksOnTopic).isFalse()
    }

    @Test
    fun `problem 6 - two guitar playalongs after months of comedy`() {
        val seeds = Issue907Scenarios.burstSeeds()
        val lanes = HomeLaneBenchmark.comedyThenGuitar()
        log(
            "P6 seeds: guitar ${seeds.guitarSeeds} of 4 | seed weight guitar ${f(
                seeds.guitarSeedWeight,
            )}, comedy ${f(seeds.comedySeedWeight)}",
        )
        log(
            "P1/P6 Home: unrelated in 5-20 ${f(lanes.unrelatedIn5to20)} | guitar in top 20 ${f(lanes.guitarInTop20)}" +
                " | comedy in top 20 ${f(lanes.comedyInTop20)} | searches ${lanes.searches}",
        )
        // A short playalong watched in full weighs about as much as a long special watched in part, and a
        // new interest gets one seed. Videos that share nothing with the viewer stay off Home.
        assertThat(seeds.guitarSeeds).isAtMost(1)
        assertThat(seeds.guitarSeedWeight).isLessThan(seeds.comedySeedWeight * 1.5)
        assertThat(lanes.unrelatedIn5to20).isAtMost(0.15)
        // Today: with only two interests, the playalongs are a major one and get as many searches as comedy.
        assertThat(lanes.guitarInTop20).isAtLeast(0.3)
    }

    @Test
    fun `problem 2 - one review among comedy and cooking`() {
        val r = Issue907Scenarios.occasionalReview()
        log("P2 one review: learned ${r.learned.take(10)} | bridges top 10 ${f(r.ranked.top10)}, 5-20 ${f(r.ranked.positions5to20)}")
        // Today: one watch plants its word pairs at the acquisition floor. YouTube's category is not a topic.
        assertThat(r.learned).containsAtLeast("google pixel", "pixel pro")
        assertThat(r.learned).containsNoneOf("science technology", "science", "technology")
        assertThat(r.ranked.top10).isAtLeast(0.1)
    }

    @Test
    fun `problem 7 - picks decay together and watch frequency barely counts`() {
        val spread = Issue907Scenarios.pickedTopicsAfterUnrelatedWatching()
        val frequency = Issue907Scenarios.watchFrequency()
        log(
            "P7 picks after 400 unrelated watches: ${f(spread.min)} to ${f(spread.max)} of the strongest" +
                " | in every video ${f(frequency.everyVideo)}, in one of four ${f(frequency.quarterOfVideos)}",
        )
        // Today: twelve picks stay equal, and the topic in a quarter of the videos ends above the one in all of them.
        assertThat(spread.min).isAtLeast(0.97)
        assertThat(frequency.quarterOfVideos).isGreaterThan(frequency.everyVideo)
    }

    @Test
    fun `problem 8 - restart after a reset`() {
        val afterReset = Issue907Scenarios.restartWithOldBrainFile(savedBrainExists = true)
        val afterUpgrade = Issue907Scenarios.restartWithOldBrainFile(savedBrainExists = false)
        val (starters, recorded) = Issue907Scenarios.starterSearches()
        log(
            "P8 restart after reset: ${afterReset.topics} topics, old files read ${afterReset.oldFilesRead}" +
                " | first start after an upgrade: ${afterUpgrade.topics} topics | starter searches $starters, recorded $recorded",
        )
        // A reset brain stays empty; only a first start in the current format migrates an older brain.
        assertThat(afterReset.topics).isEqualTo(0)
        assertThat(afterReset.oldFilesRead).isFalse()
        assertThat(afterUpgrade.topics).isEqualTo(50)
        assertThat(afterUpgrade.saves).isAtLeast(1)
        // The starter searches are used, but never stored as if they had been learned.
        assertThat(starters).containsExactlyElementsIn(NeuroDiscovery.STARTER_QUERIES)
        assertThat(recorded).isEqualTo(0)
    }

    @Test
    fun `problem 9 - onboarding picks as interest groups`() {
        val r = Issue907Scenarios.onboardingGroups()
        log("P9 seven onboarding picks: ${r.groups} group, ${r.groupsWithoutPickLinks} without the pick links | chips ${r.chipLabels}")
        // Today: onboarding links every pick to every other, so seven picks form one group and one chip.
        assertThat(r.groups).isEqualTo(1)
        assertThat(r.groupsWithoutPickLinks).isAtLeast(5)
        assertThat(r.chipLabels).containsExactly("Cook")
    }

    @Test
    fun `problems 3 and 4 - what one title or one search teaches`() {
        val title = Issue907Scenarios.learnFromTitle("Why Big Tech's Privacy Promises Are Lies")
        val curly = Issue907Scenarios.learnFromTitle("Why Big Tech’s Privacy Promises Are Lies")
        val pixel = Issue907Scenarios.learnFromTitle("Google Pixel 10 Pro Review")
        val search = Issue907Scenarios.learnFromSearch("How does encryption work")
        val pool =
            Issue907Scenarios.chancePairs(
                NeuroLearningBenchmark.pool().filter { NeuroLearningBenchmark.familyOf(it.id) != NeuroLearningBenchmark.Family.OTHER },
            )
        val real = Issue907Scenarios.chancePairs(Issue907Scenarios.titlesAsVideos(RealTitleCorpus.titles))
        log("P3 title: planted ${title.planted} | learned ${title.learned.keys}")
        log("P3 curly apostrophe: planted ${curly.planted}")
        log("P2 Pixel review title: planted ${pixel.planted} | learned ${pixel.learned.keys}")
        log("P4 search: ${search.entries.sortedByDescending { it.value }.map { "${it.key} ${f(it.value)}" }}")
        log("P3 chance pairs, benchmark titles: $pool, planted share ${f(pool.plantedUnprovenShare)}")
        log("P3 chance pairs, ${real.titles} real titles: $real, planted share ${f(real.plantedUnprovenShare)}")
        // Today: every adjacent pair is written, across "are" too, and the floor plants three of them.
        assertThat(title.planted).containsExactly("big tech's", "tech's privacy", "privacy promises").inOrder()
        assertThat(title.learned.keys).contains("promises lies")
        assertThat(curly.planted).contains("big tech’s")
        assertThat(pixel.planted).containsExactly("google pixel", "pixel pro")
        assertThat(search.keys).contains("encryption work")
        assertThat(pool.plantedUnprovenShare).isAtLeast(0.9)
        assertThat(real.titles).isAtLeast(100)
        assertThat(real.plantedUnprovenShare).isAtLeast(0.9)
    }

    companion object {
        private val lines = mutableListOf<String>()

        private fun f(value: Double) = "%.3f".format(value)

        @Synchronized
        private fun log(line: String) {
            println(line)
            lines += line
        }

        @JvmStatic
        @AfterClass
        fun writeReport() {
            val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
            File(out, "issue907.txt").writeText(lines.sorted().joinToString("\n", postfix = "\n"))
        }
    }
}
