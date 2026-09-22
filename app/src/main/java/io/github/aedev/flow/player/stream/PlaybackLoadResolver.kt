package io.github.aedev.flow.player.stream

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.di.IoDispatcher
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/** The three preference reads every stream resolution needs. */
internal data class StreamPreferences(
    val quality: VideoQuality,
    val audioLanguage: String,
    val codecKey: String,
    val subtitleLanguage: String,
)

/**
 * Turns a video id into something the player screen can play.
 *
 * It runs both extraction stacks against each other, takes whichever resolves playback first,
 * escalates to a SABR session when expired URLs force it, folds NewPipe and InnerTube into one set
 * of streams, and falls back to a downloaded copy or a premiere countdown when nothing plays. It
 * owns every network call the load makes and holds no player-screen state: results are handed back
 * as [ResolvedPlayback] steps, in the order the screen has to act on them.
 *
 * The "play now, enrich later" hand-off is why this hands steps to a callback rather than returning
 * one value: an InnerTube result that can start playback is emitted while NewPipe is still running,
 * and that step carries the still-pending NewPipe leg so the caller can replace the metadata later.
 */
class PlaybackLoadResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val viewHistory: ViewHistory,
        private val playerPreferences: PlayerPreferences,
        private val videoDownloadManager: VideoDownloadManager,
        private val sponsorBlockRepository: SponsorBlockRepository,
        @NetworkIoDispatcher private val networkDispatcher: CoroutineDispatcher,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /**
         * @param scope the caller's load job, which owns the two extraction legs so a NewPipe leg
         *   that outlives the resolution (the late-metadata case) stays tied to that job.
         * @param isCurrent whether the load that started this resolution is still the current one.
         * @param resolveUpcoming the caller's premiere lookup, kept there because the decision reads
         *   the video the screen already has cached.
         */
        suspend fun resolve(
            scope: CoroutineScope,
            request: PlaybackResolutionRequest,
            isCurrent: () -> Boolean,
            resolveUpcoming: suspend (videoId: String, knownUpcoming: Boolean) -> UpcomingPremiere,
            onStep: suspend (ResolvedPlayback) -> Unit,
        ) {
            val videoId = request.videoId
            var isOfflineAvailable = false
            var offlineLocalPath: String? = null

            try {
                val innerTubeDeferred =
                    scope.async(networkDispatcher) { extractInnerTube(videoId, forceSabr = request.escalateToSabr) }

                // Startup-critical disk reads, resolved in parallel with stream extraction so the
                // playback-preparation path below never blocks on DataStore/DB.
                val savedPositionDeferred =
                    scope.async(ioDispatcher) {
                        request.resumePositionOverrideMs?.takeIf { it > 0L }
                            ?: viewHistory.getPlaybackPosition(videoId).first()
                    }
                val autoplayDeferred = scope.async(ioDispatcher) { playerPreferences.autoplayEnabled.first() }

                val (preferences, downloadedVideo) =
                    supervisorScope {
                        val prefsDeferred = async(ioDispatcher) { readStreamPreferences(request.isWifi) }
                        val downloadedDeferred = async(ioDispatcher) { findDownloadedVideo(videoId) }
                        prefsDeferred.await() to downloadedDeferred.await()
                    }

                // Check for offline file immediately (video downloads and audio-only downloads)
                val localFile = downloadedVideo?.let { File(it.filePath) }
                isOfflineAvailable = localFile?.exists() == true
                offlineLocalPath = localFile?.absolutePath?.takeIf { isOfflineAvailable }

                if (isOfflineAvailable) {
                    Log.d(TAG, "Found offline video at ${localFile?.absolutePath}")
                    val storedSponsorBlockJson = videoDownloadManager.getSponsorBlockData(videoId)
                    val offlineSegments = sponsorBlockRepository.parseSegments(storedSponsorBlockJson)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) return
                    offlineLocalPath?.let {
                        onStep(
                            ResolvedPlayback.LocalCopyReady(
                                localFilePath = it,
                                offlineSegments = offlineSegments,
                                needsSponsorBlockBackfill = storedSponsorBlockJson == null,
                            ),
                        )
                    }

                    if (!NetworkState.isOnline(context)) {
                        Log.d(TAG, "Offline with a local copy of $videoId — skipping stream resolution")
                        innerTubeDeferred.cancel()
                        return
                    }
                }

                val playbackLoadTimeoutMs = if (request.escalateToSabr) SABR_LOAD_TIMEOUT_MS else LOAD_TIMEOUT_MS
                withTimeout(playbackLoadTimeoutMs) {
                    resolveStreams(
                        request = request,
                        preferences = preferences,
                        downloadedFilePath = downloadedVideo?.filePath,
                        offlineAbsolutePath = localFile?.absolutePath,
                        isOfflineAvailable = isOfflineAvailable,
                        innerTubeDeferred = innerTubeDeferred,
                        savedPositionDeferred = savedPositionDeferred,
                        autoplayDeferred = autoplayDeferred,
                        isCurrent = isCurrent,
                        resolveUpcoming = resolveUpcoming,
                        onStep = onStep,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Video info load timed out for $videoId", e)
                if (isCurrent() && isOfflineAvailable) {
                    Log.d(TAG, "Ignoring timeout, playing offline video")
                    onStep(
                        ResolvedPlayback.LocalCopyAfterFailure(
                            localFilePath = offlineLocalPath,
                            offlineSegments = offlineLocalPath?.let { storedSponsorBlockSegments(videoId) },
                        ),
                    )
                } else if (isCurrent()) {
                    onStep(upcomingOrFailure(videoId, PlaybackFailure.TIMEOUT, null, null, resolveUpcoming))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading video $videoId", e)
                if (isCurrent() && isOfflineAvailable) {
                    Log.d(TAG, "Ignoring exception, playing offline video")
                    val localPath = findDownloadedVideo(videoId)?.filePath?.takeIf { File(it).exists() }
                    onStep(
                        ResolvedPlayback.LocalCopyAfterFailure(
                            localFilePath = localPath,
                            offlineSegments = localPath?.let { storedSponsorBlockSegments(videoId) },
                        ),
                    )
                } else if (isCurrent()) {
                    // Final fallback if everything fails
                    val localPath = findDownloadedVideo(videoId)?.filePath?.takeIf { File(it).exists() }
                    if (localPath != null) {
                        onStep(
                            ResolvedPlayback.LocalCopyReady(
                                localFilePath = localPath,
                                offlineSegments = storedSponsorBlockSegments(videoId),
                            ),
                        )
                    } else {
                        onStep(upcomingOrFailure(videoId, PlaybackFailure.UNEXPECTED, e, null, resolveUpcoming))
                    }
                }
            }
        }

        private suspend fun resolveStreams(
            request: PlaybackResolutionRequest,
            preferences: StreamPreferences,
            downloadedFilePath: String?,
            offlineAbsolutePath: String?,
            isOfflineAvailable: Boolean,
            innerTubeDeferred: Deferred<InnerTubeVideoStreamExtractor.VideoExtractionResult?>,
            savedPositionDeferred: Deferred<Long>,
            autoplayDeferred: Deferred<Boolean>,
            isCurrent: () -> Boolean,
            resolveUpcoming: suspend (String, Boolean) -> UpcomingPremiere,
            onStep: suspend (ResolvedPlayback) -> Unit,
        ) {
            val videoId = request.videoId
            Log.d(TAG, "Loading video $videoId with preferred quality: ${preferences.quality.label} (isWifi=${request.isWifi})")

            var innerTubeResult = innerTubeDeferred.await()
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return

            if (request.escalateToSabr && innerTubeResult == null) {
                // The blanket refusal below was written when every fast client was session-gated, so
                // a re-extraction could only hand back the URLs that had just 403'd. The fast path is
                // VISIONOS now, whose URLs GVS honours untokened for the whole video, so one
                // full-ladder retry is a real second chance — and the only thing standing between a
                // device that cannot mint a PoToken (no/broken WebView) and playback that never
                // resumes. Still bounded by MAX_STREAM_EXPIRY_RETRIES.
                Log.w(TAG, "Forced-SABR reload for $videoId produced no SABR session — retrying the full client ladder")
                innerTubeResult =
                    withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) {
                        InnerTubeVideoStreamExtractor.extract(videoId, forceSabr = false)
                    }?.takeIf { innerTubeCanStartPlayback(it) }
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return
            }

            if (request.escalateToSabr && innerTubeResult == null) {
                Log.e(TAG, "Forced-SABR reload for $videoId produced no playable session — giving up on this attempt")
                if (isCurrent()) {
                    onStep(ResolvedPlayback.Failed(PlaybackFailure.EXTRACTION, cause = null, relatedVideos = null))
                }
                return
            }

            val liveFromInnerTube =
                innerTubeResult?.isLive == true &&
                    (!innerTubeResult.liveHlsUrl.isNullOrEmpty() || !innerTubeResult.liveDashUrl.isNullOrEmpty())

            // The related lane, the autoplay candidates and the queue all read this one list, and
            // it is filled from the watch response once playback is under way rather than held for
            // here: a load that waited on it would be waiting on a second request.
            val relatedVideos = emptyList<Video>()

            if (liveFromInnerTube && innerTubeResult != null) {
                onStep(ResolvedPlayback.Live(innerTubeResult, relatedVideos))
            } else if (isOfflineAvailable) {
                Log.d(TAG, "Using offline video for $videoId (Network fetch failed)")
                onStep(
                    ResolvedPlayback.OfflineFallback(
                        localFilePath = offlineAbsolutePath,
                        offlineSegments = storedSponsorBlockSegments(videoId),
                        relatedVideos = relatedVideos,
                    ),
                )
            } else if (innerTubeResult != null && innerTubeHasPlayableVod(innerTubeResult)) {
                onStep(
                    ResolvedPlayback.VodFromInnerTube(
                        result = innerTubeResult,
                        relatedVideos = relatedVideos,
                        preferredQuality = preferences.quality,
                        preferredAudioLanguage = preferences.audioLanguage,
                        preferredCodecKey = preferences.codecKey,
                        preferredSubtitleLanguage = preferences.subtitleLanguage,
                        resumePositionOverrideMs = request.resumePositionOverrideMs,
                    ),
                )
            } else {
                Log.e(TAG, "InnerTube resolved nothing playable for $videoId and no offline copy found.")
                onStep(upcomingOrFailure(videoId, PlaybackFailure.EXTRACTION, null, relatedVideos, resolveUpcoming))
            }
        }

        private suspend fun upcomingOrFailure(
            videoId: String,
            failure: PlaybackFailure,
            cause: Throwable?,
            relatedVideos: List<Video>?,
            resolveUpcoming: suspend (String, Boolean) -> UpcomingPremiere,
        ): ResolvedPlayback {
            val upcoming = resolveUpcoming(videoId, false)
            return if (upcoming.isUpcoming) {
                ResolvedPlayback.Upcoming(relatedVideos.orEmpty(), upcoming.scheduledStartMs, upcoming.details)
            } else {
                ResolvedPlayback.Failed(failure, cause, relatedVideos)
            }
        }

        private suspend fun extractInnerTube(
            videoId: String,
            forceSabr: Boolean,
        ): InnerTubeVideoStreamExtractor.VideoExtractionResult? =
            try {
                if (forceSabr) {
                    InnerTubeVideoStreamExtractor.extract(videoId, forceSabr = true)
                } else {
                    withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) {
                        InnerTubeVideoStreamExtractor.extract(videoId, forceSabr = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "InnerTube extraction failed for $videoId: ${e.message}")
                null
            }

        private suspend fun readStreamPreferences(isWifi: Boolean): StreamPreferences =
            StreamPreferences(
                quality =
                    if (isWifi) {
                        playerPreferences.defaultQualityWifi.first()
                    } else {
                        playerPreferences.defaultQualityCellular.first()
                    },
                audioLanguage = playerPreferences.preferredAudioLanguage.first(),
                codecKey = playerPreferences.videoCodecPriority.first(),
                subtitleLanguage = playerPreferences.preferredSubtitleLanguage.first(),
            )

        private suspend fun findDownloadedVideo(videoId: String) =
            try {
                videoDownloadManager.downloadedVideos
                    .map { list -> list.find { it.video.id == videoId } }
                    .first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

        private suspend fun storedSponsorBlockSegments(videoId: String) =
            sponsorBlockRepository.parseSegments(videoDownloadManager.getSponsorBlockData(videoId))

        internal companion object {
            const val TAG = "PlaybackLoadResolver"
            const val INNERTUBE_TIMEOUT_MS = 25_000L
            const val LOAD_TIMEOUT_MS = 30_000L
            const val SABR_LOAD_TIMEOUT_MS = 120_000L

            /**
             * A SABR session used to count as playable on its own. It is not: the server never
             * sends an init segment, so ExoPlayer cannot sniff the stream and the video fails
             * however long the pipeline waits. Playability is the direct formats, and a result
             * without them should let the ladder move on rather than end the load.
             */
            fun innerTubeHasPlayableVod(result: InnerTubeVideoStreamExtractor.VideoExtractionResult): Boolean {
                if (result.isLive) return false
                val hasVideo = result.videoFormats.any { !it.url.isNullOrEmpty() }
                val hasAudio = result.audioFormats.any { !it.url.isNullOrEmpty() }
                return hasVideo && hasAudio
            }

            fun innerTubeCanStartPlayback(result: InnerTubeVideoStreamExtractor.VideoExtractionResult): Boolean {
                val hasLiveManifest =
                    result.isLive &&
                        (!result.liveHlsUrl.isNullOrEmpty() || !result.liveDashUrl.isNullOrEmpty())
                return hasLiveManifest || innerTubeHasPlayableVod(result)
            }
        }
    }
