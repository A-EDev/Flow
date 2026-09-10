package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Deferred
import org.schabi.newpipe.extractor.stream.StreamInfo

/** The NewPipe leg's outcome: the info it produced, or the last error that stopped it producing one. */
typealias NewPipeOutcome = Pair<StreamInfo?, Throwable?>

/** Everything [PlaybackLoadResolver] needs that the player screen owns. */
data class PlaybackResolutionRequest(
    val videoId: String,
    val isWifi: Boolean,
    val escalateToSabr: Boolean,
    val resumePositionOverrideMs: Long?,
    val allowShorts: Boolean,
)

/** Why a resolution produced nothing to play, and therefore which error string the screen shows. */
enum class PlaybackFailure {
    /** Both extraction stacks came back empty. */
    EXTRACTION,

    /** The whole resolution ran past its budget. */
    TIMEOUT,

    /** An exception nobody in the pipeline expected. */
    UNEXPECTED,
}

/**
 * One thing the player screen can act on, handed over in the order the pipeline produces it.
 *
 * A single resolution emits one step in the common case and two when a downloaded copy starts
 * playing before the network legs finish, or when NewPipe metadata lands before the merged result
 * is assembled. The screen owns every `_uiState` write and every hand-off to the player manager;
 * this type carries only the values those need.
 */
sealed interface ResolvedPlayback {
    /**
     * NewPipe returned metadata for the video that is loading. Emitted before the merged result is
     * assembled so the session identity and the media notification are armed at the same point in
     * the load as they were before the pipeline moved out of the ViewModel.
     */
    data class PrimaryMetadata(
        val streamInfo: StreamInfo,
    ) : ResolvedPlayback

    /**
     * A downloaded copy of the video exists and should start playing now.
     *
     * [clearStreamInfo] is set only on the give-up path, where a partially applied stream result
     * may still be on screen.
     */
    data class LocalCopyReady(
        val localFilePath: String,
        val offlineSegments: List<SponsorBlockSegment>?,
        val clearStreamInfo: Boolean = false,
    ) : ResolvedPlayback

    /**
     * Resolution failed but a downloaded copy exists. A null [localFilePath] means the copy went
     * missing between the two checks: the load stops reporting an error but nothing is prepared.
     */
    data class LocalCopyAfterFailure(
        val localFilePath: String?,
        val offlineSegments: List<SponsorBlockSegment>?,
    ) : ResolvedPlayback

    /**
     * Streams could not be resolved while a downloaded copy is already playing from an earlier
     * [LocalCopyReady]: only the surrounding metadata is filled in.
     */
    data class OfflineFallback(
        val localFilePath: String?,
        val offlineSegments: List<SponsorBlockSegment>?,
        val relatedVideos: List<Video>,
    ) : ResolvedPlayback

    /** NewPipe and InnerTube merged into one playable result. */
    data class Merged(
        val streamInfo: StreamInfo,
        val streams: MergedPlayback,
        val relatedVideos: List<Video>,
        val savedPositionMs: Long,
        val autoplayEnabled: Boolean,
        val offlineSegments: List<SponsorBlockSegment>?,
        val sponsorBlockBackfillNeeded: Boolean,
        val isUpcomingContent: Boolean,
        val upcomingReleaseTimeMs: Long?,
        val resumeOverrideRequested: Boolean,
    ) : ResolvedPlayback

    /** A live stream whose manifest only InnerTube produced. */
    data class Live(
        val result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
        val relatedVideos: List<Video>,
        val lateStreamInfo: Deferred<NewPipeOutcome>?,
    ) : ResolvedPlayback

    /** A VOD whose streams only InnerTube produced, optionally ahead of NewPipe's metadata. */
    data class VodFromInnerTube(
        val result: InnerTubeVideoStreamExtractor.VideoExtractionResult,
        val relatedVideos: List<Video>,
        val preferredQuality: VideoQuality,
        val preferredAudioLanguage: String,
        val preferredCodecKey: String,
        val resumePositionOverrideMs: Long?,
        val lateStreamInfo: Deferred<NewPipeOutcome>?,
        val streamError: Throwable?,
    ) : ResolvedPlayback

    /** The video has not premiered yet, so the screen shows a countdown rather than an error. */
    data class Upcoming(
        val relatedVideos: List<Video>,
        val releaseTimeMs: Long?,
    ) : ResolvedPlayback

    /** Nothing playable, and not a premiere. A null [relatedVideos] leaves the current list alone. */
    data class Failed(
        val failure: PlaybackFailure,
        val cause: Throwable?,
        val relatedVideos: List<Video>?,
    ) : ResolvedPlayback
}
