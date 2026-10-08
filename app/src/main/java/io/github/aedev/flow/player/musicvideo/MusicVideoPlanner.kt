package io.github.aedev.flow.player.musicvideo

import io.github.aedev.flow.data.music.model.MusicTrack

/** The Song/Video switch's decisions, kept apart from the player so they can be tested alone. */
internal object MusicVideoPlanner {
    // The swapped-in version is often shorter or longer; a place past its end would end it at once.
    private const val END_MARGIN_MS = 2_000L
    private const val MS_PER_SECOND = 1_000L

    /** A queue entry to put in place, and whether it plays with its picture. */
    data class Replacement(
        val track: MusicTrack,
        val showsVideo: Boolean,
    )

    /**
     * What the queue entry [entry] becomes. [song] is the song [entry] stands in for when it is a
     * swapped-in video, [video] is [entry]'s video form when one was looked up, and [showsVideo]
     * is whether [entry] already plays with its picture. Null leaves the entry as it is.
     */
    fun replacement(
        entry: MusicTrack,
        showsVideo: Boolean,
        song: MusicTrack?,
        video: MusicTrack?,
        wantsVideo: Boolean,
    ): Replacement? =
        when {
            wantsVideo && showsVideo -> null
            wantsVideo -> video?.let { Replacement(it, showsVideo = true) }
            song != null -> Replacement(song, showsVideo = false)
            showsVideo -> Replacement(entry, showsVideo = false)
            else -> null
        }

    /** [positionMs] kept inside a track of [durationSeconds], which is unknown when not positive. */
    fun positionWithin(
        positionMs: Long,
        durationSeconds: Int,
    ): Long {
        val position = positionMs.coerceAtLeast(0L)
        if (durationSeconds <= 0) return position
        val lastMs = (durationSeconds * MS_PER_SECOND - END_MARGIN_MS).coerceAtLeast(0L)
        return position.coerceAtMost(lastMs)
    }
}
