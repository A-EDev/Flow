/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.pow

/**
 * Channels like the viewer's own (#907): channels YouTube lists beside the videos of at least
 * [MIN_POINTERS] different channels the viewer follows or watches. Whatever made YouTube put them
 * there (watch time, likes, people watching both), several of the viewer's channels agreeing on one
 * is the closest signal to "made for the same audience" the app can read. Built from the co-watch
 * sources, so finding them costs no request.
 */
internal object NeighbourChannels {
    const val MIN_POINTERS = 2
    const val MAX_CHANNELS = 12

    // Uploads of this many neighbours are fetched per Home load; each fetch is one request.
    const val REFRESH_PER_LOAD = 4
    const val UPLOADS_PER_CHANNEL = 3
    const val REFRESH_AFTER_MS = 6L * 60L * 60L * 1000L
    const val UPLOAD_WINDOW_MS = 14L * 24L * 60L * 60L * 1000L

    // A neighbour shown this many times without a watch is not for this viewer.
    const val IGNORED_IMPRESSIONS = 6

    private const val HALF_LIFE_MS = 45.0 * 24.0 * 60.0 * 60.0 * 1000.0

    data class Neighbour(
        val channelId: String,
        /** The viewer's channels that point here, strongest first. */
        val pointers: List<String>,
        val score: Double,
    )

    fun neighbours(
        sources: Collection<NeuroCoWatchStore.Source>,
        anchors: Set<String>,
        excluded: (String) -> Boolean,
        now: Long,
    ): List<Neighbour> {
        val byChannel = HashMap<String, HashMap<String, Double>>()
        sources.forEach { source ->
            if (source.channelId !in anchors) return@forEach
            val weight = 0.5.pow((now - source.at).coerceAtLeast(0L) / HALF_LIFE_MS)
            source.related.forEach { channel ->
                if (channel in anchors || excluded(channel)) return@forEach
                val pointers = byChannel.getOrPut(channel) { HashMap() }
                pointers[source.channelId] = maxOf(pointers[source.channelId] ?: 0.0, weight)
            }
        }
        return byChannel
            .filterValues { it.size >= MIN_POINTERS }
            .map { (channel, pointers) ->
                Neighbour(channel, pointers.entries.sortedByDescending { it.value }.map { it.key }, pointers.values.sum())
            }.sortedByDescending { it.score }
            .take(MAX_CHANNELS)
    }
}
