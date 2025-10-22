package com.flow.youtube.player

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@UnstableApi
object EnhancedPlayerManager {
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private val _playerState = MutableStateFlow(EnhancedPlayerState())
    val playerState: StateFlow<EnhancedPlayerState> = _playerState.asStateFlow()
    
    private var currentVideoId: String? = null
    private var availableVideoStreams: List<VideoStream> = emptyList()
    private var availableAudioStreams: List<AudioStream> = emptyList()
    private var availableSubtitles: List<SubtitlesStream> = emptyList()
    private var currentVideoStream: VideoStream? = null
    private var currentAudioStream: AudioStream? = null
    private var selectedSubtitleIndex: Int? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (player == null) {
            trackSelector = DefaultTrackSelector(context)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1500,
                    /* maxBufferMs = */ 30000,
                    /* bufferForPlaybackMs = */ 750,
                    /* bufferForPlaybackAfterRebufferMs = */ 1500
                )
                .build()

            player = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector!!)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build()
            
            setupPlayerListener()
            Log.d("EnhancedPlayerManager", "Player initialized")
        }
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.value = _playerState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    hasEnded = playbackState == Player.STATE_ENDED
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(isPlaying = playWhenReady)
            }
        })
    }

    fun setStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>
    ) {
        currentVideoId = videoId
        // dedupe lists by url and sort
        availableVideoStreams = videoStreams.distinctBy { it.url ?: "" }.sortedByDescending { it.height }
        availableAudioStreams = audioStreams.distinctBy { it.url ?: "" }
        availableSubtitles = subtitles
        // Set defaults: prefer provided videoStream/audioStream when present
        currentVideoStream = videoStream ?: availableVideoStreams.firstOrNull()
        // Prefer the provided audio stream, otherwise pick highest bitrate or preferring English if present
        currentAudioStream = audioStream ?: run {
            availableAudioStreams.firstOrNull { it.audioLocale?.language?.startsWith("en", true) == true }
                ?: availableAudioStreams.maxByOrNull { it.averageBitrate }
        }
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId,
            availableQualities = videoStreams.map { 
                QualityOption(
                    height = it.height,
                    label = "${it.height}p",
                    bitrate = it.bitrate.toLong()
                )
            },
            availableAudioTracks = audioStreams.mapIndexed { index, stream ->
                AudioTrackOption(
                    index = index,
                    label = stream.audioTrackName ?: stream.audioTrackId ?: "Track ${index + 1}",
                    language = stream.audioLocale?.displayLanguage ?: "Unknown",
                    bitrate = stream.averageBitrate.toLong()
                )
            },
            availableSubtitles = subtitles.map {
                SubtitleOption(
                    url = it.url ?: "",
                    language = it.languageTag ?: "Unknown",
                    label = it.displayLanguageName ?: it.languageTag ?: "Unknown",
                    isAutoGenerated = it.isAutoGenerated
                )
            },
            currentQuality = currentVideoStream?.height ?: 0,
            currentAudioTrack = availableAudioStreams.indexOf(currentAudioStream).coerceAtLeast(0)
        )
        
        if (currentVideoStream != null && currentAudioStream != null) {
            loadMedia(currentVideoStream, currentAudioStream!!)
        } else if (currentVideoStream != null) {
            loadMedia(currentVideoStream, currentAudioStream ?: availableAudioStreams.firstOrNull() ?: audioStream)
        } else {
            loadMedia(null, currentAudioStream ?: audioStream)
        }
    }

    @UnstableApi
    private fun loadMedia(videoStream: VideoStream?, audioStream: AudioStream) {
        player?.let { exoPlayer ->
            try {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (X11; Android) FlowPlayer")
                    .setConnectTimeoutMs(7000)
                    .setReadTimeoutMs(7000)
                    .setAllowCrossProtocolRedirects(true)
                val ctx = appContext ?: throw IllegalStateException("EnhancedPlayerManager not initialized with context")
                val dataSourceFactory = DefaultDataSource.Factory(ctx, httpFactory)
                
                // Normalize lists and dedupe by resolution / bitrate / url
                availableVideoStreams = availableVideoStreams
                    .distinctBy { it.url ?: "" }
                    .sortedByDescending { it.height }

                availableAudioStreams = availableAudioStreams
                    .distinctBy { it.url ?: "" }

                if (videoStream != null && !videoStream.url.isNullOrBlank()) {
                    // Decide whether the chosen video stream already contains audio (muxed) or is video-only.
                    val hasMuxedAudio = try {
                        // Best-effort: try reflections to detect muxed/adaptive properties
                        val method = videoStream::class.java.methods.firstOrNull { m ->
                            val n = m.name.lowercase()
                            n.contains("mux") || n.contains("hasaudio") || n.contains("isadaptive")
                        }
                        val r = method?.invoke(videoStream)
                        (r as? Boolean) == true
                    } catch (e: Exception) {
                        false
                    }
                    // Detect adaptive formats (HLS/DASH) by URL/path
                    val url = videoStream.url!!
                    val lower = url.lowercase()
                    val finalSource = if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("/hls") || lower.contains("/dash")) {
                        // Adaptive stream: hand MediaItem over to the player and let the factory handle it
                        val mediaItemBuilder = MediaItem.Builder().setUri(url)
                        // If subtitle selected, add subtitle configuration
                        val mediaItem = mediaItemBuilder.build()
                        // set directly and skip progressive merging
                        exoPlayer.setMediaItem(mediaItem)
                        null
                    } else if (hasMuxedAudio) {
                        // Single progressive stream including both audio+video -> play directly
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(url))
                    } else {
                        // Video-only + separate audio - merge progressive sources
                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(url))

                        val audioSrc = if (!audioStream.url.isNullOrBlank()) {
                            ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(audioStream.url!!))
                        } else null

                        if (audioSrc != null) MergingMediaSource(videoSource, audioSrc) else videoSource
                    }

                    // Attach subtitles if selected
                    val withSubs = if (selectedSubtitleIndex != null && selectedSubtitleIndex!! in availableSubtitles.indices) {
                        val sub = availableSubtitles[selectedSubtitleIndex!!]
                        val subUri = sub.url ?: ""
                        if (subUri.isNotBlank()) {
                            val mime = "text/vtt"
                                    val subtitleLang = sub.languageTag ?: sub.displayLanguageName
                                    val subtitleMediaItem = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUri))
                                        .setMimeType(mime)
                                        .setLanguage(subtitleLang)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()

                            val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(subtitleMediaItem, C.TIME_UNSET)

                            if (finalSource != null) MergingMediaSource(finalSource, subtitleSource) else {
                                // finalSource was applied directly to exoPlayer (adaptive). Recreate mediaItem with subtitle config
                                val mediaItem = MediaItem.Builder()
                                    .setUri(url)
                                    .setSubtitleConfigurations(listOf(subtitleMediaItem))
                                    .build()
                                exoPlayer.setMediaItem(mediaItem)
                                null
                            }
                        } else finalSource
                    } else finalSource
                    if (withSubs != null) exoPlayer.setMediaSource(withSubs)
                } else {
                    // Audio-only mode (for music)
                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(audioStream.url ?: ""))

                    exoPlayer.setMediaSource(audioSource)
                }
                
                exoPlayer.prepare()
                _playerState.value = _playerState.value.copy(isPrepared = true)
                
                Log.d("EnhancedPlayerManager", "Media loaded successfully")
            } catch (e: Exception) {
                Log.e("EnhancedPlayerManager", "Error loading media", e)
                _playerState.value = _playerState.value.copy(
                    error = "Failed to load media: ${e.message}"
                )
            }
        }
    }

    fun selectSubtitle(index: Int?) {
        // Set selected subtitle index and reload media to attach/detach subtitle track
        selectedSubtitleIndex = index
        // Re-load current streams to apply subtitle selection
        if (currentVideoStream != null && currentAudioStream != null) {
            loadMedia(currentVideoStream, currentAudioStream!!)
        }
    }



    fun switchQualityByHeight(height: Int) {
        val stream = availableVideoStreams.find { it.height == height }
        if (stream != null) {
            currentVideoStream = stream
            loadMedia(currentVideoStream, currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return)
            _playerState.value = _playerState.value.copy(currentQuality = height)
        }
    }

    fun switchQuality(height: Int) {
        val videoStream = availableVideoStreams.find { it.height == height }
        val audioStream = availableAudioStreams.maxByOrNull { it.averageBitrate }
        
        if (videoStream != null && audioStream != null) {
            val currentPosition = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            
            loadMedia(videoStream, audioStream)
            
            player?.seekTo(currentPosition)
            if (wasPlaying) {
                player?.play()
            }
            
            _playerState.value = _playerState.value.copy(currentQuality = height)
        }
    }

    fun switchAudioTrack(index: Int) {
        if (index in availableAudioStreams.indices) {
            val audioStream = availableAudioStreams[index]
            val videoStream = availableVideoStreams.find { it.height == _playerState.value.currentQuality }
            
            val currentPosition = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            
            loadMedia(videoStream, audioStream)
            
            player?.seekTo(currentPosition)
            if (wasPlaying) {
                player?.play()
            }
            
            _playerState.value = _playerState.value.copy(currentAudioTrack = index)
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun getPlayer(): ExoPlayer? = player

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun stop() {
        player?.stop()
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentVideoId = null
        )
    }

    fun release() {
        player?.release()
        player = null
        trackSelector = null
        _playerState.value = EnhancedPlayerState()
        Log.d("EnhancedPlayerManager", "Player released")
    }
}

data class EnhancedPlayerState(
    val currentVideoId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPrepared: Boolean = false,
    val hasEnded: Boolean = false,
    val currentQuality: Int = 0,
    val currentAudioTrack: Int = 0,
    val availableQualities: List<QualityOption> = emptyList(),
    val availableAudioTracks: List<AudioTrackOption> = emptyList(),
    val availableSubtitles: List<SubtitleOption> = emptyList(),
    val error: String? = null
)

data class QualityOption(
    val height: Int,
    val label: String,
    val bitrate: Long
)

data class AudioTrackOption(
    val index: Int,
    val label: String,
    val language: String,
    val bitrate: Long
)

data class SubtitleOption(
    val url: String,
    val language: String,
    val label: String,
    val isAutoGenerated: Boolean
)
