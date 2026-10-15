/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark.watch
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Builds the tech-only viewer of #907 from real videos: 150 of the ones scripts/neuro/fetch_tech_viewer.py
 * found are watched through the real engine (300 interactions), and the brain plus the matching
 * watch_history rows are written beside them in notes/neuro/techviewer. Copied into a debug build,
 * they let that device record real Home snapshots of such a viewer. Skipped when the videos are missing.
 */
class TechViewerFixtureTool {
    @Test
    fun `build the tech viewer`() {
        val root = File("../notes/neuro/techviewer")
        assumeTrue(File(root, "watched_candidates.json").isFile)
        val items = Json.parseToJsonElement(File(root, "watched_candidates.json").readText()).jsonArray
        val random = java.util.Random(907)
        val videos =
            items
                .map { it.jsonObject }
                .map {
                    Video(
                        id = it["id"]!!.jsonPrimitive.content,
                        title = it["title"]!!.jsonPrimitive.content,
                        channelName = it["channelName"]!!.jsonPrimitive.content,
                        channelId = it["channelId"]!!.jsonPrimitive.content,
                        thumbnailUrl = "",
                        duration = it["duration"]!!.jsonPrimitive.int,
                        viewCount = it["views"]!!.jsonPrimitive.long,
                        uploadDate = it["uploadDate"]!!.jsonPrimitive.content,
                    )
                }.shuffled(random)
                .take(150)
        val now = System.currentTimeMillis()
        val sql = StringBuilder()
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            videos.forEachIndexed { i, video ->
                val percent = 0.5f + random.nextFloat() * 0.5f
                engine.watch(video, percent)
                val at = now - (videos.size - i) * 4L * 60L * 60L * 1000L
                val durationMs = video.duration * 1000L
                val title = video.title.replace("'", "''")
                val channel = video.channelName.replace("'", "''")
                val thumbnail = "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
                sql.append(
                    "INSERT OR REPLACE INTO watch_history (videoId, position, duration, timestamp, title, thumbnailUrl, " +
                        "channelName, channelId, isMusic, isShort, isLocal) VALUES ('${video.id}', ${(durationMs * percent).toLong()}, " +
                        "$durationMs, $at, '$title', '$thumbnail', '$channel', '${video.channelId}', 0, 0, 0);\n",
                )
            }
            engine.resetSession()
            val brain = engine.getBrainSnapshot()
            engine.shutdown()
            val serial = with(NeuroStorage(mockk(relaxed = true))) { brain.toSerializable() }
            File(root, "brain.json").writeText(Json { encodeDefaults = true }.encodeToString(serial))
            println("interactions ${brain.totalInteractions} topics ${brain.globalVector.topics.size}")
            println(
                "top " +
                    brain.globalVector.topics.entries
                        .sortedByDescending { it.value }
                        .take(
                            30,
                        ).joinToString { "${it.key} ${"%.2f".format(it.value)}" },
            )
        }
        File(root, "history.sql").writeText(sql.toString())
    }
}
