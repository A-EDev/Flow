/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.IdfSnapshot
import io.github.aedev.flow.data.recommendation.NeuroSearchLearning
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

/**
 * What reading text costs per call on this JVM: a candidate (title and channel), a watched video
 * (title, description, tags) and a search. Writes app/build/reports/neuro-benchmark/text-cost.txt.
 * The bounds only catch a large regression; budgets are compared back to back in the PR.
 */
class NeuroTextCostBenchmarkTest {
    private val tokenizer = NeuroTokenizer()
    private val idf = IdfSnapshot(emptyMap(), 0)
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private data class Cost(
        val medianMicros: Double,
        val p95Micros: Double,
        val bytesPerCall: Long,
    )

    private fun <T> measure(
        inputs: List<T>,
        calls: Int = 20_000,
        block: (T) -> Any,
    ): Cost {
        var sink = 0
        repeat(calls) { sink += block(inputs[it % inputs.size]).hashCode() }
        val nanos = LongArray(calls)
        val threadId = Thread.currentThread().id
        val allocatedBefore = threads.getThreadAllocatedBytes(threadId)
        for (i in 0 until calls) {
            val start = System.nanoTime()
            sink += block(inputs[i % inputs.size]).hashCode()
            nanos[i] = System.nanoTime() - start
        }
        val allocated = threads.getThreadAllocatedBytes(threadId) - allocatedBefore
        nanos.sort()
        check(sink != Int.MIN_VALUE)
        return Cost(nanos[calls / 2] / 1_000.0, nanos[calls * 95 / 100] / 1_000.0, allocated / calls)
    }

    private fun candidate(
        title: String,
        i: Int,
    ) = Video(
        id = "cost-$i",
        title = title,
        channelName = "Channel $i",
        channelId = "c-$i",
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1_000,
        uploadDate = "2 days ago",
    )

    @Test
    fun `text processing cost per call`() {
        val titles = RealTitleCorpus.titles + NeuroLearningBenchmark.pool().map { it.title }
        val candidates = titles.mapIndexed { i, title -> candidate(title, i) }
        val watched =
            candidates.map {
                it.copy(
                    description = "${it.title}\nEverything about this, start to finish.\nChapters below.\n0:00 Intro\n1:20 Main part",
                    tags = listOf("tech review", "smartphone", "android", "camera test", "battery life"),
                )
            }
        val searches = listOf("How does encryption work", "best budget gaming laptop 2026", "lofi hip hop radio", "why big tech lies")

        val candidateCost = measure(candidates) { tokenizer.extractFeatures(it, idf) }
        val watchedCost = measure(watched) { tokenizer.extractFeatures(it, idf) }
        val searchCost = measure(searches, calls = 2_000) { NeuroSearchLearning.queryTopics(it, tokenizer) }

        val report =
            listOf(
                "candidate" to candidateCost,
                "watched video" to watchedCost,
                "search" to searchCost,
            ).joinToString("\n") { (name, cost) ->
                "%-14s median %.2f µs, p95 %.2f µs, %d B per call".format(name, cost.medianMicros, cost.p95Micros, cost.bytesPerCall)
            }
        println(report)
        File(File("build/reports/neuro-benchmark").apply { mkdirs() }, "text-cost.txt").writeText("$report\n")

        assertThat(candidateCost.medianMicros).isLessThan(200.0)
        assertThat(watchedCost.medianMicros).isLessThan(500.0)
        assertThat(searchCost.medianMicros).isLessThan(2_000.0)
    }
}
