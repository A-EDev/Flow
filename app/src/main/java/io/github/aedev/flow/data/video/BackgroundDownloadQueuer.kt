package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.toMusicTrack
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.data.video.downloader.work.EnqueueOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A "Download all" in progress: how many of [total] videos have been looked at so far. */
data class DownloadBatch(
    val collectionId: String,
    val total: Int,
    val processed: Int = 0,
    val queued: Int = 0,
    val skipped: Int = 0,
) {
    val isFinished: Boolean get() = processed >= total

    fun record(outcome: QueueOutcome): DownloadBatch =
        copy(
            processed = processed + 1,
            queued = queued + if (outcome == QueueOutcome.QUEUED) 1 else 0,
            skipped = skipped + if (outcome == QueueOutcome.ALREADY_PRESENT) 1 else 0,
        )
}

/** What happened to one video handed to [BackgroundDownloadQueuer.queue]. */
enum class QueueOutcome {
    QUEUED,
    ALREADY_PRESENT,
    UNAVAILABLE,
}

/**
 * Queues downloads with no dialog, at the default download quality and codec: "Download all" on a
 * playlist or album. Queueing only writes the request; streams are resolved when each download's
 * turn comes, so a long batch never runs on URLs that expired while it waited.
 */
@Singleton
class BackgroundDownloadQueuer
    @Inject
    constructor(
        private val controller: DownloadController,
    ) {
        // Outlives the screen that asked, so leaving a playlist doesn't drop the rest of its videos.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _batches = MutableStateFlow<Map<String, DownloadBatch>>(emptyMap())
        val batches: StateFlow<Map<String, DownloadBatch>> = _batches.asStateFlow()

        /** Queues every video of a collection, skipping what is already downloaded or queued. */
        fun queueAll(
            collectionId: String,
            videos: List<Video>,
        ) = runBatch(collectionId, videos.distinctBy { it.id }) { queue(it) }

        /** [queueAll] for songs, which keep their album and artists in their tags. */
        fun queueSongs(
            collectionId: String,
            tracks: List<MusicTrack>,
        ) = runBatch(collectionId, tracks.distinctBy { it.videoId }) { queueSong(it) }

        private fun <T> runBatch(
            collectionId: String,
            items: List<T>,
            work: suspend (T) -> QueueOutcome,
        ) {
            if (items.isEmpty()) return
            val started = DownloadBatch(collectionId, total = items.size)
            var accepted = false
            _batches.update { batches ->
                if (batches[collectionId]?.isFinished == false) {
                    batches
                } else {
                    accepted = true
                    batches + (collectionId to started)
                }
            }
            if (!accepted) return
            scope.launch {
                items.forEach { item ->
                    val outcome = runCatching { work(item) }.getOrDefault(QueueOutcome.UNAVAILABLE)
                    _batches.update { batches ->
                        val batch = batches[collectionId] ?: return@update batches
                        batches + (collectionId to batch.record(outcome))
                    }
                }
            }
        }

        /** Forgets a finished batch once its result has been shown. */
        fun clearBatch(collectionId: String) {
            _batches.update { batches -> if (batches[collectionId]?.isFinished == true) batches - collectionId else batches }
        }

        /** Queues [video]; a song goes in as music, so it lands in the music folder with its tags. */
        suspend fun queue(video: Video): QueueOutcome =
            if (video.isMusic) queueSong(video.toMusicTrack()) else controller.enqueue(video.toDownloadRequest()).toQueueOutcome()

        private suspend fun queueSong(track: MusicTrack): QueueOutcome = controller.enqueue(track.toDownloadRequest()).toQueueOutcome()

        private fun EnqueueOutcome.toQueueOutcome(): QueueOutcome =
            when (this) {
                EnqueueOutcome.QUEUED -> QueueOutcome.QUEUED
                EnqueueOutcome.ALREADY_DOWNLOADED, EnqueueOutcome.ALREADY_QUEUED -> QueueOutcome.ALREADY_PRESENT
            }
    }
