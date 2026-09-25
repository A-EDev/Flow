package io.github.aedev.flow.data.video

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.toMusicTrack
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Starts downloads with no dialog, at the default download quality and codec: "Download all" on a
 * playlist, Retry on a failed download, and a resume the service can no longer continue.
 */
@Singleton
class BackgroundDownloadQueuer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val optionsLoader: VideoDownloadOptionsLoader,
        private val videoDownloadManager: VideoDownloadManager,
        private val musicDownloadManager: DownloadManager,
        private val preferences: PlayerPreferences,
    ) {
        // Outlives the screen that asked, so leaving a playlist doesn't drop the rest of its videos.
        private val scope = CoroutineScope(SupervisorJob() + PerformanceDispatcher.networkIO)

        private val _batches = MutableStateFlow<Map<String, DownloadBatch>>(emptyMap())
        val batches: StateFlow<Map<String, DownloadBatch>> = _batches.asStateFlow()

        /**
         * Queues every video of a collection in the background, [BATCH_WORKERS] lookups at a time, skipping
         * what is already downloaded or downloading. A second request for a running batch is ignored.
         */
        fun queueAll(
            collectionId: String,
            videos: List<Video>,
        ) = runBatch(collectionId, videos.distinctBy { it.id }) { queue(it) }

        /** [queueAll] for songs, which keep their album and artists through the music downloader. */
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
            val pending = Channel<T>(Channel.UNLIMITED)
            items.forEach(pending::trySend)
            pending.close()
            repeat(BATCH_WORKERS) {
                scope.launch {
                    for (item in pending) {
                        val outcome = runCatching { work(item) }.getOrDefault(QueueOutcome.UNAVAILABLE)
                        _batches.update { batches ->
                            val batch = batches[collectionId] ?: return@update batches
                            batches + (collectionId to batch.record(outcome))
                        }
                    }
                }
            }
        }

        /** Forgets a finished batch once its result has been shown. */
        fun clearBatch(collectionId: String) {
            _batches.update { batches -> if (batches[collectionId]?.isFinished == true) batches - collectionId else batches }
        }

        /**
         * Queues [video]. Songs go through the music library's downloader so they show under Music.
         * With [replaceExisting], a failed or stalled download of the same video is started over.
         */
        suspend fun queue(
            video: Video,
            replaceExisting: Boolean = false,
        ): QueueOutcome {
            if (video.isMusic) return queueSong(video)
            val existing = videoDownloadManager.getDownloadWithItems(video.id)
            if (existing != null) {
                val finished = existing.overallStatus == DownloadItemStatus.COMPLETED
                if (finished || !replaceExisting) return QueueOutcome.ALREADY_PRESENT
            }
            val options = optionsLoader.load(video) ?: return QueueOutcome.UNAVAILABLE
            val choice = choose(options) ?: return QueueOutcome.UNAVAILABLE
            withContext(Dispatchers.Main) {
                FlowDownloadService.startDownload(
                    context = context,
                    video = video,
                    url = choice.videoUrl,
                    quality = choice.qualityLabel,
                    audioUrl = choice.audioUrl,
                    videoCodec = choice.videoCodec,
                )
            }
            return QueueOutcome.QUEUED
        }

        private suspend fun queueSong(video: Video): QueueOutcome = queueSong(video.toMusicTrack())

        private suspend fun queueSong(track: MusicTrack): QueueOutcome {
            if (musicDownloadManager.isDownloaded(track.videoId)) return QueueOutcome.ALREADY_PRESENT
            return if (musicDownloadManager.downloadTrack(track).isSuccess) QueueOutcome.QUEUED else QueueOutcome.UNAVAILABLE
        }

        private suspend fun choose(options: VideoDownloadOptions): Choice? {
            val targetHeight = preferences.defaultDownloadQuality.first().height
            val codec =
                preferences.defaultDownloadCodec
                    .first()
                    .takeIf { it != VideoCodec.AUTO }
                    ?.codecKey
            val language = preferences.preferredAudioLanguage.first()
            val videoStreams =
                DownloadStreamPolicy.buildDownloadVideoStreams(
                    innerTubeStreams = InnerTubeStreamBridge.convertVideoFormats(options.videoFormats),
                    videoOnlyStreams = emptyList(),
                    muxedStreams = emptyList(),
                )
            val audioStreams = InnerTubeStreamBridge.convertAudioFormats(options.audioFormats)
            val height =
                DefaultDownloadSelection.pickHeight(videoStreams.map(VideoCodecUtils::qualityHeightFromStream), targetHeight)
                    ?: return null
            val atHeight = videoStreams.filter { VideoCodecUtils.qualityHeightFromStream(it) == height }
            for (codecKey in DefaultDownloadSelection.rankCodecs(atHeight.map(VideoCodecUtils::codecKeyFromStream), codec)) {
                val stream = atHeight.first { VideoCodecUtils.codecKeyFromStream(it) == codecKey }
                val url = stream.getContent().takeIf { it.isNotBlank() } ?: continue
                val audioUrl =
                    if (stream.isVideoOnly) {
                        DownloadStreamPolicy
                            .pickCompatibleAudioForVideo(codecKey, audioStreams, language)
                            ?.getContent()
                            ?.takeIf { it.isNotBlank() } ?: continue
                    } else {
                        null
                    }
                return Choice(
                    videoUrl = url,
                    audioUrl = audioUrl,
                    qualityLabel = "${VideoCodecUtils.codecLabelFromKey(codecKey)} ${height}p",
                    videoCodec = codecKey.takeIf { it == "vp9" || it == "vp8" || it == "av1" },
                )
            }
            return null
        }

        private companion object {
            const val BATCH_WORKERS = 2
        }

        private data class Choice(
            val videoUrl: String,
            val audioUrl: String?,
            val qualityLabel: String,
            val videoCodec: String?,
        )
    }
