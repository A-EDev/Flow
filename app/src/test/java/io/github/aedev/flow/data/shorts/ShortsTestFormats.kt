package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult

internal fun reelVideoFormat(
    itag: Int,
    mimeType: String,
    width: Int,
    height: Int,
    qualityLabel: String? = "${minOf(width, height)}p",
    bitrate: Int = 1_000_000,
    fps: Int = 30,
    url: String? = "https://example.invalid/videoplayback?itag=$itag",
): Format =
    Format(
        itag = itag,
        url = url,
        mimeType = mimeType,
        bitrate = bitrate,
        width = width,
        height = height,
        contentLength = 4_000_000L,
        quality = "medium",
        fps = fps,
        qualityLabel = qualityLabel,
        averageBitrate = bitrate,
        audioQuality = null,
        approxDurationMs = "28000",
        audioSampleRate = null,
        audioChannels = null,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        initRange = Format.Range(start = "0", end = "740"),
        indexRange = Format.Range(start = "741", end = "1560"),
    )

internal fun reelAudioFormat(
    itag: Int,
    mimeType: String = "audio/mp4; codecs=\"mp4a.40.2\"",
    bitrate: Int = 128_000,
    trackId: String? = null,
    displayName: String? = null,
    audioIsDefault: Boolean? = null,
    xtags: String? = null,
    url: String? = "https://example.invalid/videoplayback?itag=$itag&track=$trackId",
): Format =
    Format(
        itag = itag,
        url = url,
        mimeType = mimeType,
        bitrate = bitrate,
        width = null,
        height = null,
        contentLength = 400_000L,
        quality = "tiny",
        fps = null,
        qualityLabel = null,
        averageBitrate = bitrate,
        audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = "28000",
        audioSampleRate = 44_100,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        audioTrack =
            trackId?.let {
                Format.AudioTrack(displayName = displayName, id = it, audioIsDefault = audioIsDefault)
            },
        xtags = xtags,
        initRange = Format.Range(start = "0", end = "600"),
        indexRange = Format.Range(start = "601", end = "900"),
    )

internal fun reelExtraction(
    videoFormats: List<Format>,
    audioFormats: List<Format>,
    videoId: String = "reel1234567",
    expiresInSeconds: Int = 21_540,
    lengthSeconds: String = "28",
): VideoExtractionResult =
    VideoExtractionResult(
        videoFormats = videoFormats,
        audioFormats = audioFormats,
        playerResponse =
            PlayerResponse(
                responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
                playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
                playerConfig = null,
                streamingData =
                    PlayerResponse.StreamingData(
                        adaptiveFormats = videoFormats + audioFormats,
                        expiresInSeconds = expiresInSeconds,
                    ),
                videoDetails =
                    PlayerResponse.VideoDetails(
                        videoId = videoId,
                        title = "A reel",
                        author = "Reel Channel",
                        channelId = "UCreelchannel0000000000",
                        lengthSeconds = lengthSeconds,
                        viewCount = "1234567",
                    ),
                playbackTracking = null,
            ),
        usedClient = YouTubeClient.VISIONOS,
        sabrInfo = null,
    )
