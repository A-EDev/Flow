package io.github.aedev.flow.player

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.primaryArtistKey

/**
 * Decides whether a playlist change opens a new radio session or continues the current one.
 *
 * The queue alone cannot tell the two apart: an in-queue skip and an explicit "Start radio" both
 * arrive as PLAYLIST_CHANGED, and a radio seeded from the playing track always looks like the
 * session it is meant to replace. An explicit request therefore carries its own seed.
 */
internal object MusicRadioPlanner {
    data class QueueContext(
        val reseed: Boolean,
        val explicit: Boolean,
        val knownIds: List<String>,
    )

    fun resolveQueueContext(
        currentId: String,
        queueIds: List<String>,
        previousIds: List<String>?,
        explicitSeedId: String?,
    ): QueueContext {
        if (explicitSeedId == currentId) {
            return QueueContext(reseed = true, explicit = true, knownIds = queueIds)
        }

        val previous =
            previousIds
                ?: return QueueContext(reseed = true, explicit = false, knownIds = queueIds)

        // Same session when the track was already part of the previous queue: skips and queue
        // jumps rebuild the playlist (sometimes with a pruned list), but the user never left
        // their queue — only a track from OUTSIDE it reseeds.
        val sameContext = previous == queueIds || currentId in previous
        if (!sameContext) {
            return QueueContext(reseed = true, explicit = false, knownIds = queueIds)
        }

        // A pruned rebuild (stale mirror) must not shrink the known context.
        val knownIds = if (queueIds.size < previous.size) (previous + queueIds).distinct() else queueIds
        return QueueContext(reseed = false, explicit = false, knownIds = knownIds)
    }

    /**
     * The list under the toggle is the up-next buffer, so it is ordered once here and consumed
     * from the head. Ordering it for display and re-ordering it again at append time is what made
     * the queue fill with tracks other than the ones on screen.
     */
    const val MAX_POOL_SIZE = 100

    /**
     * Songs one artist may hold in the station at a time.
     *
     * US webcasting's sound recording performance complement allows four songs per artist in any
     * three hours — roughly fifty tracks — and that is the loosest any licensed radio product
     * runs. Artist variety is also the dimension listeners actually register as diversity, so two
     * across a hundred-track pool sits deliberately well inside the legal floor.
     */
    const val MAX_TRACKS_PER_ARTIST = 2

    /** The pool a fresh station starts with. */
    fun seedPool(
        candidates: List<MusicTrack>,
        currentId: String?,
        queueIds: Set<String>,
    ): List<MusicTrack> =
        capArtists(
            candidates
                .distinctBy { it.videoId }
                .filterNot { it.videoId == currentId || it.videoId in queueIds },
            emptyMap(),
        ).take(MAX_POOL_SIZE)

    /**
     * Drops an artist's surplus tracks, keeping the order it was handed. Ranking and artist spread
     * run first, so the survivors are that artist's best-placed entries rather than an arbitrary
     * two.
     */
    private fun capArtists(
        tracks: List<MusicTrack>,
        alreadyHeld: Map<String, Int>,
    ): List<MusicTrack> {
        val counts = HashMap(alreadyHeld)
        return tracks.filter { track ->
            val key = track.primaryArtistKey()
            val held = counts.getOrDefault(key, 0)
            if (held >= MAX_TRACKS_PER_ARTIST) {
                false
            } else {
                counts[key] = held + 1
                true
            }
        }
    }

    fun artistCounts(tracks: List<MusicTrack>): Map<String, Int> = tracks.groupingBy { it.primaryArtistKey() }.eachCount()

    /** Grows the pool without disturbing what is already in it, and without letting it run away. */
    fun growPool(
        existing: List<MusicTrack>,
        incoming: List<MusicTrack>,
        currentId: String?,
        queueIds: Set<String>,
    ): List<MusicTrack> {
        val room = MAX_POOL_SIZE - existing.size
        if (room <= 0) return existing
        val existingIds = existing.mapTo(HashSet()) { it.videoId }
        val fresh =
            capArtists(
                incoming
                    .distinctBy { it.videoId }
                    .filterNot { it.videoId == currentId || it.videoId in queueIds || it.videoId in existingIds },
                artistCounts(existing),
            ).take(room)
        return if (fresh.isEmpty()) existing else existing + fresh
    }

    /** What the queue takes next: the head of the list the user is looking at, in that order. */
    fun nextBatch(
        pool: List<MusicTrack>,
        queueIds: Set<String>,
        limit: Int,
    ): List<MusicTrack> = pool.filterNot { it.videoId in queueIds }.take(limit)
}
