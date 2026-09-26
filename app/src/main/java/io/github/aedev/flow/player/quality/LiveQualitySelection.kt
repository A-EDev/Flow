package io.github.aedev.flow.player.quality

/** A quality the viewer chose by hand on one livestream; a [height] of 0 means they chose Auto. */
data class LiveQualityPick(
    val videoId: String,
    val height: Int,
)

/**
 * Remembers the viewer's live quality choice across a re-prepare of the same stream (reload, expiry
 * recovery, resync), which resets the track selector. A different stream starts from the default.
 */
object LiveQualitySelection {
    /** The pick still worth keeping once [videoId] is being prepared. */
    fun retainedPick(
        videoId: String,
        pick: LiveQualityPick?,
    ): LiveQualityPick? = pick?.takeIf { it.videoId == videoId }

    /** The quality class to apply once the stream's tracks are known; 0 leaves it on Auto. */
    fun targetHeight(
        videoId: String,
        pick: LiveQualityPick?,
        defaultHeight: Int,
    ): Int = retainedPick(videoId, pick)?.height ?: defaultHeight

    /** The highest offered class at or under [target], or the lowest one when all are above it. */
    fun snapToOffered(
        target: Int,
        offeredDescending: List<Int>,
    ): Int? = offeredDescending.firstOrNull { it <= target } ?: offeredDescending.lastOrNull()

    /**
     * The max frame size that selects [qualityClass]. A portrait stream's height is its long side,
     * so its class bounds the width instead.
     */
    fun maxVideoSize(
        qualityClass: Int,
        portrait: Boolean,
    ): Pair<Int, Int> = if (portrait) qualityClass to Int.MAX_VALUE else Int.MAX_VALUE to qualityClass
}
