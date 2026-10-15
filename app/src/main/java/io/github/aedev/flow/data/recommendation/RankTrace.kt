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

/** The step of [NeuroScoring.applySmartDiversity] that placed a video. */
enum class DiversityPhase { STRICT, EXPLORATION, DEFERRED, RELAXED }

/**
 * Why each candidate of one rank pass made it into the result, so a Home snapshot can tell a video
 * the viewer's taste chose from one that only filled a short lane.
 */
class RankTrace {
    val toppedUp: MutableSet<String> = HashSet()
    val unrelated: MutableSet<String> = HashSet()
    val phases: MutableMap<String, DiversityPhase> = HashMap()

    /** Admitted although nothing about the viewer chose it: a top-up, or a video sharing nothing with the viewer. */
    fun isPadding(videoId: String): Boolean = videoId in toppedUp || videoId in unrelated

    fun merge(other: RankTrace) {
        toppedUp += other.toppedUp
        unrelated += other.unrelated
        phases += other.phases
    }
}
