package io.github.aedev.flow.ui.tv.player.state

import io.github.aedev.flow.data.model.SponsorBlockSegment

/**
 * Precomputed seek-bar geometry: chapter tick fractions and SponsorBlock
 * segment ranges as [0,1] fractions. Built once per (chapters, segments,
 * duration) so the per-frame draw allocates nothing.
 */
data class TvSeekBarMarks(
    val chapterFractions: List<Float>,
    val segments: List<Segment>,
) {
    data class Segment(
        val startFraction: Float,
        val endFraction: Float,
        val category: String,
    )

    fun isEmpty(): Boolean = chapterFractions.isEmpty() && segments.isEmpty()

    companion object {
        fun from(
            chapterStartSeconds: List<Int>,
            sponsorSegments: List<SponsorBlockSegment>,
            durationMs: Long,
        ): TvSeekBarMarks {
            if (durationMs <= 0L) return TvSeekBarMarks(emptyList(), emptyList())
            val durationSec = durationMs / 1_000f

            val chapters = chapterStartSeconds
                .asSequence()
                .filter { it > 0 && it < durationSec }
                .map { it / durationSec }
                .toList()

            val segments = sponsorSegments
                .asSequence()
                .filter { it.endTime > it.startTime && it.startTime < durationSec }
                .map {
                    Segment(
                        startFraction = (it.startTime / durationSec).coerceIn(0f, 1f),
                        endFraction = (it.endTime / durationSec).coerceIn(0f, 1f),
                        category = it.category,
                    )
                }
                .toList()

            return TvSeekBarMarks(chapters, segments)
        }
    }
}
