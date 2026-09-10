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
import io.github.aedev.flow.player.PlaybackResolverReadiness
import io.github.aedev.flow.player.PlaybackResolverWinner
import io.github.aedev.flow.player.PlaybackStartupPolicy
import io.github.aedev.flow.player.awaitFirstPlaybackResolver
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
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File
import javax.inject.Inject

/** The three preference reads every stream resolution needs. */
internal data class StreamPreferences(
    val quality: VideoQuality,
    val audioLanguage: String,
    val codecKey: String,
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
                val streamInfoDeferred = scope.async(networkDispatcher) { fetchStreamInfo(videoId) }
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
                    val offlineSegments = storedSponsorBlockSegments(videoId)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) return
                    offlineLocalPath?.let { onStep(ResolvedPlayback.LocalCopyReady(it, offlineSegments)) }

                    if (!NetworkState.isOnline(context)) {
                        Log.d(TAG, "Offline with a local copy of $videoId — skipping stream resolution")
                        streamInfoDeferred.cancel()
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
                        streamInfoDeferred = streamInfoDeferred,
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
                                clearStreamInfo = true,
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
            streamInfoDeferred: Deferred<NewPipeOutcome>,
            innerTubeDeferred: Deferred<InnerTubeVideoStreamExtractor.VideoExtractionResult?>,
            savedPositionDeferred: Deferred<Long>,
            autoplayDeferred: Deferred<Boolean>,
            isCurrent: () -> Boolean,
            resolveUpcoming: suspend (String, Boolean) -> UpcomingPremiere,
            onStep: suspend (ResolvedPlayback) -> Unit,
        ) {
            val videoId = request.videoId
            Log.d(TAG, "Loading video $videoId with preferred quality: ${preferences.quality.label} (isWifi=${request.isWifi})")

            var streamResolution: NewPipeOutcome = null to null
            var innerTubeResult: InnerTubeVideoStreamExtractor.VideoExtractionResult? = null
            var lateStreamInfoDeferred: Deferred<NewPipeOutcome>? = null

            if (request.escalateToSabr) {
                streamInfoDeferred.cancel()
                innerTubeResult = innerTubeDeferred.await()
            } else {
                when (val winner = awaitFirstPlaybackResolver(streamInfoDeferred, innerTubeDeferred)) {
                    is PlaybackResolverWinner.Primary -> {
                        streamResolution = winner.value
                        if (classifyNewPipeResult(winner.value.first) == PlaybackResolverReadiness.NEEDS_FALLBACK) {
                            innerTubeResult = innerTubeDeferred.await()
                        } else {
                            Log.d(TAG, "NewPipe resolved playback first for $videoId; skipping redundant InnerTube wait")
                            innerTubeDeferred.cancel()
                        }
                    }

                    is PlaybackResolverWinner.Fallback -> {
                        innerTubeResult = winner.value
                        if (winner.value != null && innerTubeCanStartPlayback(winner.value)) {
                            Log.d(TAG, "InnerTube resolved playback first for $videoId; preparing before NewPipe metadata")
                            lateStreamInfoDeferred = streamInfoDeferred
                        } else {
                            streamResolution = streamInfoDeferred.await()
                        }
                    }
                }
            }

            val (streamInfo, streamError) = streamResolution
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

            // Extract related videos directly from the stream info (avoids extra network call)
            val relatedVideos =
                if (streamInfo != null) {
                    repository
                        .getRelatedVideosFromStreamInfo(streamInfo)
                        .filter { request.allowShorts || !it.isShort }
                } else {
                    emptyList()
                }

            if (streamInfo != null && streamInfo.streamType == StreamType.NONE && !isOfflineAvailable) {
                // A stream type of NONE with no countdown to show leaves the load unresolved, as it
                // has since the premiere state was introduced.
                val upcoming = resolveUpcoming(videoId, true)
                if (upcoming.isUpcoming) {
                    onStep(ResolvedPlayback.Upcoming(relatedVideos, upcoming.scheduledStartMs))
                }
                return
            }

            if (streamInfo != null) {
                onStep(ResolvedPlayback.PrimaryMetadata(streamInfo))
                onStep(
                    assembleMerged(
                        request = request,
                        streamInfo = streamInfo,
                        innerTubeResult = innerTubeResult,
                        preferences = preferences,
                        downloadedFilePath = downloadedFilePath,
                        isOfflineAvailable = isOfflineAvailable,
                        relatedVideos = relatedVideos,
                        savedPositionDeferred = savedPositionDeferred,
                        autoplayDeferred = autoplayDeferred,
                        resolveUpcoming = resolveUpcoming,
                    ),
                )
            } else if (liveFromInnerTube && innerTubeResult != null) {
                Log.w(TAG, "Live fallback for $videoId via InnerTube manifest (NewPipe StreamInfo null)")
                onStep(ResolvedPlayback.Live(innerTubeResult, relatedVideos, lateStreamInfoDeferred))
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
                Log.w(TAG, "VOD fallback for $videoId via InnerTube (NewPipe StreamInfo null)")
                onStep(
                    ResolvedPlayback.VodFromInnerTube(
                        result = innerTubeResult,
                        relatedVideos = relatedVideos,
                        preferredQuality = preferences.quality,
                        preferredAudioLanguage = preferences.audioLanguage,
                        preferredCodecKey = preferences.codecKey,
                        resumePositionOverrideMs = request.resumePositionOverrideMs,
                        lateStreamInfo = lateStreamInfoDeferred,
                        streamError = streamError,
                    ),
                )
            } else {
                Log.e(TAG, "Stream info is null for $videoId and no offline copy found.")
                onStep(upcomingOrFailure(videoId, PlaybackFailure.EXTRACTION, streamError, relatedVideos, resolveUpcoming))
            }
        }

        private suspend fun assembleMerged(
            request: PlaybackResolutionRequest,
            streamInfo: StreamInfo,
            innerTubeResult: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
            preferences: StreamPreferences,
            downloadedFilePath: String?,
            isOfflineAvailable: Boolean,
            relatedVideos: List<Video>,
            savedPositionDeferred: Deferred<Long>,
            autoplayDeferred: Deferred<Boolean>,
            resolveUpcoming: suspend (String, Boolean) -> UpcomingPremiere,
        ): ResolvedPlayback.Merged {
            val videoId = request.videoId
            // A downloaded copy overrides the resolved streams, whether it is a full video or audio only.
            val localFilePath = downloadedFilePath?.takeIf { File(it).exists() }
            val streams =
                MergedPlaybackAssembly.assemble(
                    streamInfo = streamInfo,
                    innerTubeResult = innerTubeResult,
                    preferredQuality = preferences.quality,
                    preferredAudioLanguage = preferences.audioLanguage,
                    preferredCodecKey = preferences.codecKey,
                    escalateToSabr = request.escalateToSabr,
                    localFilePath = localFilePath,
                )

            // Stored SponsorBlock segments are what an offline play uses; a download that has none
            // yet is backfilled by the caller so the next play does not go looking again.
            val storedSponsorBlockJson =
                if (localFilePath != null) videoDownloadManager.getSponsorBlockData(videoId) else null
            val offlineSegments = storedSponsorBlockJson?.let { sponsorBlockRepository.parseSegments(it) }

            val upcoming =
                if (!streams.hasPlayableContent && !isOfflineAvailable) {
                    resolveUpcoming(videoId, streams.isLiveType || streamInfo.streamType == StreamType.NONE)
                } else {
                    UpcomingPremiere.NOT_UPCOMING
                }
            if (upcoming.isUpcoming) {
                PlayerDiagnostics.logWarning(
                    "Upcoming",
                    "no playable content videoId=$videoId type=${streamInfo.streamType} " +
                        "liveType=${streams.isLiveType} release=${upcoming.scheduledStartMs}",
                )
            }

            return ResolvedPlayback.Merged(
                streamInfo = streamInfo,
                streams = streams,
                relatedVideos = relatedVideos,
                // Resolved once for both the UI state and the playback preparation; the read was
                // started in parallel with extraction.
                savedPositionMs = savedPositionDeferred.await(),
                autoplayEnabled = autoplayDeferred.await(),
                offlineSegments = offlineSegments,
                sponsorBlockBackfillNeeded = localFilePath != null && storedSponsorBlockJson == null,
                isUpcomingContent = upcoming.isUpcoming,
                upcomingReleaseTimeMs = upcoming.scheduledStartMs,
                resumeOverrideRequested = request.resumePositionOverrideMs != null,
            )
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
                ResolvedPlayback.Upcoming(relatedVideos.orEmpty(), upcoming.scheduledStartMs)
            } else {
                ResolvedPlayback.Failed(failure, cause, relatedVideos)
            }
        }

        private suspend fun fetchStreamInfo(videoId: String): NewPipeOutcome {
            var info: StreamInfo? = null
            var lastError: Throwable? = null
            var attempt = 0
            while (info == null && attempt < NEWPIPE_ATTEMPTS) {
                try {
                    attempt++
                    info = withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) { repository.getVideoStreamInfo(videoId) }
                    if (info == null && attempt < NEWPIPE_ATTEMPTS) {
                        Log.w(TAG, "Stream info fetch failed (attempt $attempt), retrying in ${attempt * RETRY_BACKOFF_MS}ms...")
                        delay(attempt * RETRY_BACKOFF_MS)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ContentNotAvailableException) {
                    Log.e(TAG, "Content restriction for $videoId: ${e.javaClass.simpleName}: ${e.message}")
                    lastError = e
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load stream info (attempt $attempt)", e)
                    lastError = e
                    if (attempt < NEWPIPE_ATTEMPTS) delay(attempt * RETRY_BACKOFF_MS)
                }
            }
            if (info == null) Log.e(TAG, "Stream info fetch failed after $NEWPIPE_ATTEMPTS attempts")
            return info to lastError
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

        private fun classifyNewPipeResult(streamInfo: StreamInfo?): PlaybackResolverReadiness {
            if (streamInfo == null) return PlaybackResolverReadiness.NEEDS_FALLBACK
            return PlaybackStartupPolicy.classifyNewPipeResult(
                hasProgressiveVideo = streamInfo.videoStreams.isNotEmpty(),
                hasVideoOnly = streamInfo.videoOnlyStreams.isNotEmpty(),
                hasAudio = streamInfo.audioStreams.isNotEmpty(),
                hasDashManifest = !streamInfo.dashMpdUrl.isNullOrEmpty(),
                hasHlsManifest = !streamInfo.hlsUrl.isNullOrEmpty(),
                isKnownUpcoming = streamInfo.streamType == StreamType.NONE,
            )
        }

        private companion object {
            const val TAG = "PlaybackLoadResolver"
            const val NEWPIPE_ATTEMPTS = 3
            const val NEWPIPE_TIMEOUT_MS = 10_000L
            const val INNERTUBE_TIMEOUT_MS = 25_000L
            const val RETRY_BACKOFF_MS = 300L
            const val LOAD_TIMEOUT_MS = 30_000L
            const val SABR_LOAD_TIMEOUT_MS = 120_000L

            fun innerTubeHasPlayableVod(result: InnerTubeVideoStreamExtractor.VideoExtractionResult): Boolean {
                if (result.isLive) return false
                if (result.sabrInfo != null) return true
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
