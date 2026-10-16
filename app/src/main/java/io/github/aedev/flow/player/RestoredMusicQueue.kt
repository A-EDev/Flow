package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.QueuePersistence
import io.github.aedev.flow.data.music.model.MusicTrack

/** What a saved music queue comes back as after the process was gone. */
internal data class RestoredMusicQueue(
    val queue: List<MusicTrack>,
    val index: Int,
    val track: MusicTrack?,
    val shuffleEnabled: Boolean,
    val automix: List<MusicTrack>,
    /** Where the restored track picks up; 0 starts it over. */
    val resumePositionMs: Long,
)

/** A song this close to its end starts over rather than playing only its last seconds. */
private const val RESUME_END_MARGIN_MS = 5_000L

internal fun restoredMusicQueue(saved: QueuePersistence.QueueState): RestoredMusicQueue? {
    if (saved.queue.isEmpty()) return null
    val track =
        saved.currentTrackId?.let { id -> saved.queue.find { it.videoId == id } }
            ?: saved.queue.getOrNull(saved.currentIndex)
    val durationMs = (track?.duration ?: 0) * 1000L
    val position = saved.currentPosition.coerceAtLeast(0L)
    val resumesInside = durationMs <= 0L || position < durationMs - RESUME_END_MARGIN_MS
    return RestoredMusicQueue(
        queue = saved.queue,
        index = saved.currentIndex.coerceIn(0, saved.queue.size - 1),
        track = track,
        shuffleEnabled = saved.shuffleEnabled,
        automix = saved.automix,
        resumePositionMs = if (track != null && resumesInside) position else 0L,
    )
}
