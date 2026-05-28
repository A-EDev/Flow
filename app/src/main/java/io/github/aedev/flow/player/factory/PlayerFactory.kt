package io.github.aedev.flow.player.factory

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.renderer.CustomRenderersFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@UnstableApi
class PlayerFactory {

    companion object {
        private const val TAG = "PlayerFactory"
    }

    private class CachedPrefs(
        val audioLanguage: String,
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferRebufferMs: Int,
        val playDuringCalls: Boolean
    )

    private var cachedPrefs: CachedPrefs? = null

    private fun ensurePrefs(context: Context): CachedPrefs {
        cachedPrefs?.let { return it }
        val prefs = PlayerPreferences(context)
        val result = runBlocking {
            CachedPrefs(
                audioLanguage = prefs.preferredAudioLanguage.first(),
                minBufferMs = prefs.minBufferMs.first(),
                maxBufferMs = prefs.maxBufferMs.first(),
                bufferForPlaybackMs = prefs.bufferForPlaybackMs.first(),
                bufferRebufferMs = prefs.bufferForPlaybackAfterRebufferMs.first(),
                playDuringCalls = prefs.playDuringCalls.first()
            )
        }
        cachedPrefs = result
        return result
    }

    suspend fun preloadPreferences(context: Context) {
        if (cachedPrefs != null) return
        val prefs = PlayerPreferences(context)
        cachedPrefs = CachedPrefs(
            audioLanguage = prefs.preferredAudioLanguage.first(),
            minBufferMs = prefs.minBufferMs.first(),
            maxBufferMs = prefs.maxBufferMs.first(),
            bufferForPlaybackMs = prefs.bufferForPlaybackMs.first(),
            bufferRebufferMs = prefs.bufferForPlaybackAfterRebufferMs.first(),
            playDuringCalls = prefs.playDuringCalls.first()
        )
    }

    fun createBandwidthMeter(context: Context): DefaultBandwidthMeter {
        return DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(PlayerConfig.INITIAL_BANDWIDTH_ESTIMATE)
            .setResetOnNetworkTypeChange(false)
            .build()
    }

    fun createTrackSelector(context: Context): DefaultTrackSelector {
        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        val prefs = ensurePrefs(context)

        return DefaultTrackSelector(context, trackSelectionFactory).apply {
            val builder = buildUponParameters()
                .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowMultipleAdaptiveSelections(true)
                .setForceHighestSupportedBitrate(false)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setViewportSizeToPhysicalDisplaySize(context, true)
                .setMaxVideoSize(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)

            when (prefs.audioLanguage) {
                "original", "" -> {}
                else -> builder.setPreferredAudioLanguage(prefs.audioLanguage)
            }

            setParameters(builder.build())
        }
    }

    fun createLoadControl(context: Context): DefaultLoadControl {
        val allocator = DefaultAllocator(true, PlayerConfig.ALLOCATOR_BUFFER_SIZE)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 256
        val isLowMemoryDevice = activityManager?.isLowRamDevice == true || memoryClassMb <= 256
        val maxSafeMinBufferMs = if (isLowMemoryDevice) {
            PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_MIN_BUFFER_MS
        } else {
            PlayerConfig.MAX_SAFE_MAIN_MIN_BUFFER_MS
        }
        val maxSafeBufferMs = if (isLowMemoryDevice) {
            PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_BUFFER_MS
        } else {
            PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS
        }
        val targetBufferBytes = when {
            isLowMemoryDevice -> PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES
            memoryClassMb <= 384 -> PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_BYTES
            else -> PlayerConfig.MAIN_TARGET_BUFFER_BYTES
        }
        val backBufferMs = if (isLowMemoryDevice) {
            PlayerConfig.LOW_MEMORY_BACK_BUFFER_DURATION_MS
        } else {
            PlayerConfig.BACK_BUFFER_DURATION_MS
        }

        val prefs = ensurePrefs(context)
        val minBufferMs = prefs.minBufferMs.coerceIn(2_500, maxSafeMinBufferMs)
        val maxBufferMs = prefs.maxBufferMs.coerceIn(minBufferMs + 5_000, maxSafeBufferMs)
        val bufferForPlaybackMs = prefs.bufferForPlaybackMs.coerceIn(250, minBufferMs)
        val bufferRebufferMs = prefs.bufferRebufferMs.coerceIn(750, maxBufferMs)

        Log.d(TAG, "Buffer config: min=${minBufferMs}ms, max=${maxBufferMs}ms, playback=${bufferForPlaybackMs}ms, rebuffer=${bufferRebufferMs}ms, target=${targetBufferBytes / 1024 / 1024}MB, back=${backBufferMs}ms, heap=${memoryClassMb}MB")

        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferRebufferMs)
            .setBackBuffer(backBufferMs, true)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setTargetBufferBytes(targetBufferBytes)
            .build()
    }

    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return CustomRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
    }

    fun createPlayer(
        context: Context,
        trackSelector: DefaultTrackSelector,
        loadControl: DefaultLoadControl,
        renderersFactory: DefaultRenderersFactory,
        dataSourceFactory: DataSource.Factory?
    ): ExoPlayer {
        val factory = dataSourceFactory ?: DefaultDataSource.Factory(context)
        val prefs = ensurePrefs(context)

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                !prefs.playDuringCalls
            )
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .also {
                it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                it.setWakeMode(C.WAKE_MODE_NETWORK)
                Log.d(TAG, "ExoPlayer instance created")
            }
    }
}
