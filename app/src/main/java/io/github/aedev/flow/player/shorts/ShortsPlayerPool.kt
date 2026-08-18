package io.github.aedev.flow.player.shorts

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.player.analytics.PlaybackAnalyticsLogger
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.cache.SharedPlayerCacheProvider
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.datasource.YouTubeHttpDataSource
import io.github.aedev.flow.player.factory.LoadControlFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ShortsPlayerPool — 3-player pool for instant swipe transitions.
 *
 * Architecture:
 * - 3 ExoPlayer instances with roles: PREVIOUS, CURRENT, NEXT
 * - Aggressive buffer settings (2.5s min / 15s max / 500ms playback / 1.5s rebuffer)
 * - REPEAT_MODE_ONE for looping shorts
 * - RESIZE_MODE_ZOOM for full-screen display
 * - Role rotation on swipe: no player creation/destruction, just role reassignment
 * - Pre-loading: NEXT player starts buffering before user swipes
 *
 * Lifecycle:
 * 1. initialize(context) — creates 3 players
 * 2. prepareCurrent(videoUrl, audioUrl) — loads media into CURRENT player
 * 3. prepareNext(videoUrl, audioUrl) — pre-loads NEXT player
 * 4. swipeForward() — rotates: CURRENT→PREVIOUS, NEXT→CURRENT, PREVIOUS→NEXT
 * 5. swipeBackward() — rotates: CURRENT→NEXT, PREVIOUS→CURRENT, NEXT→PREVIOUS
 * 6. release() — destroys all players
 */
@OptIn(UnstableApi::class)
class ShortsPlayerPool private constructor() {
    companion object {
        private const val TAG = "ShortsPlayerPool"
        private const val POOL_SIZE = 3

        @Volatile
        private var instance: ShortsPlayerPool? = null

        fun getInstance(): ShortsPlayerPool =
            instance ?: synchronized(this) {
                instance ?: ShortsPlayerPool().also { instance = it }
            }
    }

    private val players = arrayOfNulls<ExoPlayer>(POOL_SIZE)
    private val playerVideoIds = arrayOfNulls<String>(POOL_SIZE)

    // Tracks which content index (absolute position in the list) currently owns this player slot
    private val playerOwnerIndices = arrayOfNulls<Int>(POOL_SIZE)

    // Track the last video and audio URLs per slot so we can hot-swap audio/quality
    private val playerVideoUrls = arrayOfNulls<String>(POOL_SIZE)
    private val playerAudioUrls = arrayOfNulls<String?>(POOL_SIZE)

    private var isInitialized = false
    private var dataSourceFactory: DefaultDataSource.Factory? = null

    private var activeIndex: Int = -1

    private val _ownershipGeneration = MutableStateFlow(0)
    val ownershipGeneration: StateFlow<Int> = _ownershipGeneration.asStateFlow()

    private fun bumpOwnership() {
        _ownershipGeneration.value += 1
    }

    /**
     * Reads through the shared media cache once it exists.
     *
     * Without it every loop of a REPEAT_MODE_ONE short and every swipe back re-downloaded the
     * whole file — the main video player has read through [SharedPlayerCacheProvider] all along,
     * and this pool was the one playback path that did not.
     *
     * Null until the cache has been opened, which cannot happen here: [initialize] runs on the main
     * thread and opening the cache touches SQLite and the filesystem.
     */
    @Volatile
    private var cachedDataSourceFactory: DataSource.Factory? = null

    private var poolScope: CoroutineScope? = null
    private var preferredAudioLanguage: String = "original"
    private var shortsPlaybackMode: String = "loop"
    private var basePlaybackSpeed: Float = 1f

    private val preferenceObservers = ShortsPreferenceObservers()

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    private val _currentVideo = MutableStateFlow<ShortVideo?>(null)
    val currentVideo: StateFlow<ShortVideo?> = _currentVideo.asStateFlow()

    fun setCurrentVideo(video: ShortVideo?) {
        _currentVideo.value = video
    }

    fun playbackPosition(): Long = findActivePlayer()?.currentPosition ?: 0L

    fun playbackDuration(): Long = findActivePlayer()?.duration?.coerceAtLeast(0L) ?: 0L

    fun isPlaying(): Boolean = findActivePlayer()?.isPlaying == true

    // INITIALIZATION
    fun initialize(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext
        Log.d(TAG, "Initializing 3-player pool for Shorts")
        dataSourceFactory = DefaultDataSource.Factory(appContext, YouTubeHttpDataSource.Factory())
        attachSharedCache(appContext)
        val preferences = PlayerPreferences(appContext)
        preferenceObservers.start(
            preferredAudioLanguage = preferences.preferredAudioLanguage,
            playbackMode = preferences.shortsPlaybackMode,
            playbackSpeed = preferences.shortsPlaybackSpeed,
            onPreferredAudioLanguage = { language ->
                preferredAudioLanguage = language
                updateTrackSelectors(language)
            },
            onPlaybackMode = { mode ->
                shortsPlaybackMode = mode
                Log.d(TAG, "Shorts playback mode changed to: $mode")
            },
            onPlaybackSpeed = { speed ->
                setBasePlaybackSpeed(speed)
            },
        )

        try {
            for (i in 0 until POOL_SIZE) {
                players[i] = createShortsPlayer(appContext)
                playerOwnerIndices[i] = null
                playerVideoIds[i] = null
            }
            isInitialized = true
            Log.d(TAG, "Player pool initialized with $POOL_SIZE players")
        } catch (error: Throwable) {
            release()
            throw error
        }
    }

    /**
     * Points playback at the shared media cache, opening it off the main thread if some other
     * player has not already. Until that completes the pool keeps streaming uncached, so a cold
     * cache delays the benefit rather than the first frame.
     */
    private fun attachSharedCache(appContext: Context) {
        SharedPlayerCacheProvider.existing()?.let {
            cachedDataSourceFactory = buildCachedFactory(appContext, it)
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { poolScope = it }
        scope.launch {
            runCatching {
                PlayerCacheManager.preload(appContext)
                SharedPlayerCacheProvider.existing()
            }.onSuccess { cache ->
                if (cache != null) {
                    cachedDataSourceFactory = buildCachedFactory(appContext, cache)
                    Log.d(TAG, "Shorts playback now reads through the shared media cache")
                }
            }.onFailure { Log.w(TAG, "Shared cache unavailable; Shorts will stream uncached", it) }
        }
    }

    private fun buildCachedFactory(
        appContext: Context,
        cache: Cache,
    ): DataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext, YouTubeHttpDataSource.Factory()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun updateTrackSelectors(language: String) {
        players.filterNotNull().forEach { player ->
            val trackSelector = player.trackSelector as? DefaultTrackSelector
            trackSelector?.let { selector ->
                val builder = selector.buildUponParameters()
                when (language) {
                    "original", "" -> {
                    }

                    else -> {
                        builder.setPreferredAudioLanguage(language)
                    }
                }
                selector.setParameters(builder)
            }
        }
    }

    private fun createShortsPlayer(context: Context): ExoPlayer {
        val (maxVideoWidth, maxVideoHeight) = maxVideoSizeForHeap(context)

        val loadControl = LoadControlFactory.forShorts()

        val trackSelector =
            DefaultTrackSelector(
                context,
                AdaptiveTrackSelection.Factory(),
            ).apply {
                val builder =
                    buildUponParameters()
                        .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setForceHighestSupportedBitrate(false)
                        .setViewportSizeToPhysicalDisplaySize(context, true)
                        .setMaxVideoSize(maxVideoWidth, maxVideoHeight)

                if (preferredAudioLanguage != "original" && preferredAudioLanguage.isNotEmpty()) {
                    builder.setPreferredAudioLanguage(preferredAudioLanguage)
                }

                setParameters(builder.build())
            }

        val renderersFactory =
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

        return ExoPlayer
            .Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false,
            ).setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory!!))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                addAnalyticsListener(PlaybackAnalyticsLogger(TAG) { _currentVideoId.value })
                addListener(
                    object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            ShortsStartupTrace.onFirstFrame(_currentVideoId.value)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            ShortsStartupTrace.onState(
                                _currentVideoId.value,
                                when (playbackState) {
                                    Player.STATE_IDLE -> "IDLE"
                                    Player.STATE_BUFFERING -> "BUFFERING"
                                    Player.STATE_READY -> "READY"
                                    else -> "ENDED"
                                },
                            )
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            ShortsStartupTrace.onState(_currentVideoId.value, "ERROR ${error.errorCodeName}: ${error.message}")
                        }
                    },
                )
            }
    }

    private fun maxVideoSizeForHeap(context: Context): Pair<Int, Int> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 256
        val isLowMemoryDevice = activityManager?.isLowRamDevice == true || memoryClassMb <= 256
        return when {
            isLowMemoryDevice -> 1080 to 1920
            memoryClassMb <= 384 -> 1440 to 2560
            else -> 2160 to 3840
        }
    }

    // PLAYER ACCESS

    /**
     * The player a page should bind its `PlayerView` to.
     *
     * Deliberately returns the slot before any media is loaded: the surface has to be attached to
     * the ExoPlayer instance ahead of [prepare] or the first frame arrives with nowhere to go. It
     * refuses only when the slot currently belongs to a *different* index, which is what used to
     * hand a mid-fling page the still-playing previous short.
     *
     * Callers must re-read this when [ownershipGeneration] changes.
     */
    fun playerForAttach(index: Int): ExoPlayer? {
        if (!isInitialized || index < 0) return null
        val slot = ShortsSlotRules.slotFor(index, POOL_SIZE)
        if (!ShortsSlotRules.canAttach(playerOwnerIndices[slot], index)) return null
        return players[slot]
    }

    /**
     * The player that actually holds this index's media, or null while the slot is unprepared or
     * owned by someone else. Everything that reads playback state or issues a command uses this, so
     * a control can never land on a short the user is not looking at.
     */
    fun ownedPlayer(index: Int): ExoPlayer? {
        if (!isInitialized || index < 0) return null
        val slot = ShortsSlotRules.slotFor(index, POOL_SIZE)
        if (!ShortsSlotRules.isOwnedBy(playerOwnerIndices[slot], index)) return null
        return players[slot]
    }

    fun getVideoUrlForIndex(index: Int): String? {
        if (!isInitialized || index < 0) return null
        val slot = index % POOL_SIZE
        return playerVideoUrls[slot].takeIf { playerOwnerIndices[slot] == index }
    }

    fun getCurrentVideoId(): String? = _currentVideoId.value

    // MEDIA LOADING

    /**
     * Prepare the player for a specific index with video + audio streams.
     * @param index The absolute list position of the video
     * @param shouldPlay If true, starts playback immediately (for current item). If false, just buffers (for next/prev).
     */
    fun prepare(
        index: Int,
        videoId: String,
        videoUrl: String,
        audioUrl: String?,
        shouldPlay: Boolean,
    ) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return

        val isSameVideo = playerVideoIds[slot] == videoId && playerOwnerIndices[slot] == index

        if (isSameVideo) {
            if (shouldPlay && !player.isPlaying) {
                Log.d(TAG, "Player at index $index (slot $slot) already prepared. Resuming.")
                activatePlayer(index)
            }
            return
        }

        Log.d(TAG, "Preparing player at index $index (slot $slot) for video $videoId. AutoPlay: $shouldPlay")

        // Stop any previous playback in this slot
        player.stop()
        player.clearMediaItems()

        // Update ownership
        playerOwnerIndices[slot] = index
        playerVideoIds[slot] = videoId
        playerVideoUrls[slot] = videoUrl
        playerAudioUrls[slot] = audioUrl
        bumpOwnership()

        // Load media
        if (shouldPlay) ShortsStartupTrace.onPrepared(videoId)
        preparePlayerInternal(player, videoUrl, audioUrl)

        // Set playback state
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = shouldPlay
        // Set repeat mode based on playback preference: "loop" → REPEAT_MODE_ONE, "auto_next" → REPEAT_MODE_OFF
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        if (shouldPlay) {
            _currentVideoId.value = videoId

            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
        } else {
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false,
            )
        }
    }

    /**
     * Activates the player at the given index (play) and pauses all others.
     * Call this when a page settles.
     */
    fun activatePlayer(index: Int) {
        if (!isInitialized) return
        val activeSlot = index % POOL_SIZE

        activeIndex = index

        for (i in 0 until POOL_SIZE) {
            val player = players[i] ?: continue
            val isTarget = (i == activeSlot)

            if (isTarget) {
                if (playerOwnerIndices[i] == index) {
                    player.playWhenReady = true
                    player.setPlaybackSpeed(basePlaybackSpeed)
                    player.setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        true,
                    )
                    _currentVideoId.value = playerVideoIds[i]
                } else {
                    _currentVideoId.value = null
                }
            } else {
                player.playWhenReady = false
            }
        }
    }

    fun releaseUnusedPlayers(currentIndex: Int) {
        if (!isInitialized) return

        var released = false
        for (i in 0 until POOL_SIZE) {
            val ownerIndex = playerOwnerIndices[i] ?: continue
            if (ShortsSlotRules.shouldRelease(ownerIndex, currentIndex)) {
                Log.d(TAG, "Releasing stale player slot $i (owned by index $ownerIndex, current is $currentIndex)")
                players[i]?.stop()
                players[i]?.clearMediaItems()
                playerVideoIds[i]?.let { ShortsStartupTrace.forget(it) }
                playerOwnerIndices[i] = null
                playerVideoIds[i] = null
                playerVideoUrls[i] = null
                playerAudioUrls[i] = null
                released = true
            }
        }
        if (released) bumpOwnership()
    }

    /**
     * Hot-swap the audio track for an already-prepared player slot, keeping the same video URL.
     * Used for the Shorts audio track selector.
     */
    fun reloadWithAudioUrl(
        index: Int,
        videoId: String,
        newAudioUrl: String?,
    ) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return
        if (playerOwnerIndices[slot] != index || playerVideoIds[slot] != videoId) return

        val videoUrl = playerVideoUrls[slot] ?: return
        val wasPlaying = player.isPlaying || player.playWhenReady

        player.stop()
        player.clearMediaItems()
        playerAudioUrls[slot] = newAudioUrl

        preparePlayerInternal(player, videoUrl, newAudioUrl)
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = wasPlaying
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Hot-swap the video quality (URL) for an already-prepared player slot.
     * Used for the Shorts quality selector.
     */
    fun reloadWithVideoUrl(
        index: Int,
        videoId: String,
        newVideoUrl: String,
    ) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return
        if (playerOwnerIndices[slot] != index || playerVideoIds[slot] != videoId) return

        val wasPlaying = player.isPlaying || player.playWhenReady
        val position = player.currentPosition

        player.stop()
        player.clearMediaItems()
        playerVideoUrls[slot] = newVideoUrl

        val audioUrl = playerAudioUrls[slot]
        preparePlayerInternal(player, newVideoUrl, audioUrl)
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = wasPlaying
        player.seekTo(position)
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun preparePlayerInternal(
        player: ExoPlayer,
        videoUrl: String,
        audioUrl: String?,
    ) {
        val factory = cachedDataSourceFactory ?: dataSourceFactory ?: return

        if (audioUrl != null && audioUrl != videoUrl) {
            val videoSource =
                ProgressiveMediaSource
                    .Factory(factory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))
            val audioSource =
                ProgressiveMediaSource
                    .Factory(factory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
            val mergingSource = MergingMediaSource(true, true, videoSource, audioSource)
            player.setMediaSource(mergingSource)
        } else {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
        }

        player.prepare()
        player.setPlaybackSpeed(basePlaybackSpeed)
    }

    // PLAYBACK CONTROL

    /**
     * The player for [activeIndex], or null when the active page's media has not loaded yet.
     *
     * Returning null is the point: a command issued during that window is dropped rather than
     * applied to whichever slot happened to still hold the previous short's id.
     */
    private fun findActivePlayer(): ExoPlayer? = ownedPlayer(activeIndex)

    fun play() {
        findActivePlayer()?.let { player ->
            player.setPlaybackSpeed(basePlaybackSpeed)
            player.playWhenReady = true
        }
    }

    fun pause() {
        findActivePlayer()?.playWhenReady = false
    }

    fun togglePlayPause() {
        val player = findActivePlayer() ?: return
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        findActivePlayer()?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        findActivePlayer()?.setPlaybackSpeed(speed)
    }

    fun resetPlaybackSpeed() {
        findActivePlayer()?.setPlaybackSpeed(basePlaybackSpeed)
    }

    fun setBasePlaybackSpeed(speed: Float) {
        basePlaybackSpeed = speed
        findActivePlayer()?.setPlaybackSpeed(speed)
    }

    fun getBasePlaybackSpeed(): Float = basePlaybackSpeed

    /** Pause ALL players */
    fun pauseAll() {
        for (i in 0 until POOL_SIZE) {
            players[i]?.playWhenReady = false
        }
    }

    /**
     * Release all players and reset state.
     * Call when leaving the Shorts screen.
     */
    fun release() {
        Log.d(TAG, "Releasing player pool")
        preferenceObservers.stop()
        poolScope?.cancel()
        poolScope = null
        cachedDataSourceFactory = null
        for (i in 0 until POOL_SIZE) {
            players[i]?.stop()
            players[i]?.release()
            players[i] = null
            playerVideoIds[i] = null
            playerOwnerIndices[i] = null
            playerVideoUrls[i] = null
            playerAudioUrls[i] = null
        }
        dataSourceFactory = null
        isInitialized = false
        activeIndex = -1
        _currentVideoId.value = null
        _currentVideo.value = null
        bumpOwnership()
    }

    fun isReady(): Boolean = isInitialized && players[0] != null
}
