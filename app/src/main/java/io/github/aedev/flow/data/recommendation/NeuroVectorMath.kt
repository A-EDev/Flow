/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.*

/**
 * Stateless vector operations. All functions are pure —
 * they take inputs and return outputs with no side effects.
 * Easy to unit test in isolation.
 */
internal object NeuroVectorMath {

    // ── Weight Constants ──
    const val TOPIC_SIMILARITY_WEIGHT = 0.70
    const val DURATION_SIMILARITY_WEIGHT = 0.10
    const val PACING_SIMILARITY_WEIGHT = 0.10
    const val COMPLEXITY_SIMILARITY_WEIGHT = 0.10

    const val TOPIC_PRUNE_THRESHOLD = 0.03

    /** Topics above this score are core interests — decay extremely slowly */
    const val ESTABLISHED_TOPIC_THRESHOLD = 0.30
    /** Topics above this score are developing — decay slowly */
    const val DEVELOPING_TOPIC_THRESHOLD = 0.10

    /** Established interests: half-life ~1400 interactions */
    const val ESTABLISHED_DECAY_RATE = 0.998
    /** Developing interests: half-life ~330 interactions */
    const val DEVELOPING_DECAY_RATE = 0.993
    /** Emerging/noisy topics: half-life ~46 interactions*/
    const val EMERGING_DECAY_RATE = 0.97

    const val NEGATIVE_PROPORTIONAL_EXPONENT = 1.5
    const val NEGATIVE_FLOOR_FACTOR = 0.3
    const val NEGATIVE_SCALAR_PROPORTIONAL = 0.3
    const val NEGATIVE_SCALAR_FLOOR = 0.1
    const val COMPRESSION_THRESHOLD = 0.6
    const val COMPRESSION_CEILING = 0.5
    const val COMPRESSION_FACTOR = 0.7

    fun calculateCosineSimilarity(
        user: ContentVector,
        content: ContentVector
    ): Double {
        val (smallMap, largeMap) = if (
            user.topics.size <= content.topics.size
        ) user.topics to content.topics
        else content.topics to user.topics

        val durationSim = 1.0 - abs(user.duration - content.duration)
        val pacingSim = 1.0 - abs(user.pacing - content.pacing)
        val complexitySim = 1.0 - abs(user.complexity - content.complexity)
        val scalarScore = (durationSim * DURATION_SIMILARITY_WEIGHT) +
            (pacingSim * PACING_SIMILARITY_WEIGHT) +
            (complexitySim * COMPLEXITY_SIMILARITY_WEIGHT)

        if (smallMap.isEmpty()) return scalarScore

        // Build O(1) reverse-lookup maps for migration-compatibility matches
        val largeBaseToTagged = HashMap<String, Pair<String, Double>>(largeMap.size)
        val largeUntagged = HashMap<String, Double>(largeMap.size)
        for ((k, v) in largeMap) {
            if (k.contains(':')) {
                largeBaseToTagged.putIfAbsent(k.substringBefore(':'), k to v)
            } else {
                largeUntagged[k] = v
            }
        }

        var dotProduct = 0.0
        var hasIntersection = false

        for ((key, smallVal) in smallMap) {
            // Exact match (full weight)
            val exactMatch = largeMap[key]
            if (exactMatch != null) {
                dotProduct += smallVal * exactMatch
                hasIntersection = true
                continue
            }
            // Migration compatibility: untagged ↔ tagged partial match (0.3x weight)
            if (!key.contains(":")) {
                val taggedMatch = largeBaseToTagged[key]
                if (taggedMatch != null) {
                    dotProduct += smallVal * taggedMatch.second * 0.3
                    hasIntersection = true
                }
            } else {
                val baseWord = key.substringBefore(":")
                val untaggedMatch = largeUntagged[baseWord]
                if (untaggedMatch != null) {
                    dotProduct += smallVal * untaggedMatch * 0.3
                    hasIntersection = true
                }
            }
        }

        if (!hasIntersection) return scalarScore

        var magnitudeA = 0.0
        var magnitudeB = 0.0
        user.topics.values.forEach { magnitudeA += it * it }
        content.topics.values.forEach { magnitudeB += it * it }

        val topicSim = if (magnitudeA > 0 && magnitudeB > 0) {
            dotProduct / (sqrt(magnitudeA) * sqrt(magnitudeB))
        } else 0.0

        return (topicSim * TOPIC_SIMILARITY_WEIGHT) + scalarScore
    }

    fun adjustVector(
        current: ContentVector,
        target: ContentVector,
        baseRate: Double
    ): ContentVector {
        val newTopics = current.topics.toMutableMap()
        val isNegative = baseRate < 0

        target.topics.forEach { (key, targetVal) ->
            val currentVal = newTopics[key] ?: 0.0

            val delta = if (isNegative) {
                val proportional = currentVal *
                    currentVal.pow(NEGATIVE_PROPORTIONAL_EXPONENT) * baseRate
                val absoluteFloor = baseRate * NEGATIVE_FLOOR_FACTOR
                minOf(proportional, absoluteFloor)
            } else {
                val saturationPenalty = (1.0 - currentVal).pow(2)
                // Cold-topic damping: brand-new topics (currentVal near 0) learn at reduced
                // rate, requiring sustained engagement to build up.
                // At 0.0: 50% of base rate. At 0.10: 75%. At 0.20+: ~100%.
                val coldTopicDamping = (0.5 + 0.5 * (currentVal / 0.20).coerceAtMost(1.0))
                val effectiveRate = baseRate * saturationPenalty * coldTopicDamping
                (targetVal - currentVal) * effectiveRate
            }

            newTopics[key] = (currentVal + delta).coerceIn(0.0, 1.0)
        }

        val iterator = newTopics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val isCurrentTarget = target.topics.containsKey(entry.key)
            if (baseRate > 0 && !isCurrentTarget) {
                val tieredDecay = when {
                    entry.value >= ESTABLISHED_TOPIC_THRESHOLD -> ESTABLISHED_DECAY_RATE
                    entry.value >= DEVELOPING_TOPIC_THRESHOLD -> DEVELOPING_DECAY_RATE
                    else -> EMERGING_DECAY_RATE
                }
                entry.setValue(entry.value * tieredDecay)
            }
            if (!isCurrentTarget && entry.value < TOPIC_PRUNE_THRESHOLD) {
                iterator.remove()
            }
        }

        if (isNegative && newTopics.isNotEmpty()) {
            val totalMagnitude = newTopics.values.sum()
            val maxScore = newTopics.values.maxOrNull() ?: 0.0

            if (totalMagnitude > 0 &&
                maxScore / totalMagnitude > COMPRESSION_THRESHOLD
            ) {
                val compressed = newTopics.mapValues { (_, v) ->
                    if (v > COMPRESSION_CEILING)
                        COMPRESSION_CEILING +
                            (v - COMPRESSION_CEILING) * COMPRESSION_FACTOR
                    else v
                }
                newTopics.clear()
                newTopics.putAll(compressed)
            }
        }

        fun updateScalar(
            currentScalar: Double,
            targetScalar: Double
        ): Double {
            return if (isNegative) {
                val proportional = currentScalar * baseRate *
                    NEGATIVE_SCALAR_PROPORTIONAL
                val floor = baseRate * NEGATIVE_SCALAR_FLOOR
                currentScalar + minOf(proportional, floor)
            } else {
                val saturation = (1.0 - currentScalar).pow(2)
                currentScalar + (targetScalar - currentScalar) *
                    baseRate * saturation
            }.coerceIn(0.0, 1.0)
        }

        return current.copy(
            topics = newTopics,
            duration = updateScalar(current.duration, target.duration),
            pacing = updateScalar(current.pacing, target.pacing),
            complexity = updateScalar(current.complexity, target.complexity),
            isLive = updateScalar(current.isLive, target.isLive)
        )
    }

    fun normalizeTopicVector(
        topics: MutableMap<String, Double>
    ): Map<String, Double> {
        if (topics.isEmpty()) return topics
        var magnitude = 0.0
        topics.values.forEach { magnitude += it * it }
        magnitude = sqrt(magnitude)
        return if (magnitude > 0) topics.mapValues { (_, v) -> v / magnitude }
        else topics
    }

    fun calculateTitleSimilarity(
        tokens1: Set<String>,
        tokens2: Set<String>
    ): Double {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}
