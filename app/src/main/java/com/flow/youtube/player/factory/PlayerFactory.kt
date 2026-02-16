package com.flow.youtube.player.factory

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
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.player.renderer.CustomRenderersFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Factory for creating and configuring ExoPlayer instances.
 */
@UnstableApi
class PlayerFactory {
    
    companion object {
        private const val TAG = "PlayerFactory"
    }
    
    /**
     * Create a configured bandwidth meter.
     */
    fun createBandwidthMeter(context: Context): DefaultBandwidthMeter {
        return DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(PlayerConfig.INITIAL_BANDWIDTH_ESTIMATE)
            .setResetOnNetworkTypeChange(false)
            .build()
    }
    
    /**
     * Create a configured track selector.
     * Reads the user's preferred audio language from PlayerPreferences.
     */
    fun createTrackSelector(context: Context): DefaultTrackSelector {
        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        
        val prefs = PlayerPreferences(context)
        val audioLangPref = runBlocking { prefs.preferredAudioLanguage.first() }
        
        return DefaultTrackSelector(context, trackSelectionFactory).apply {
            val builder = buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowMultipleAdaptiveSelections(true)
                .setForceHighestSupportedBitrate(false)
                .setViewportSizeToPhysicalDisplaySize(context, true)
                .setMaxVideoSize(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)
            
            when (audioLangPref) {
                "original", "" -> {
                }
                else -> {
                    builder.setPreferredAudioLanguage(audioLangPref)
                }
            }
            
            setParameters(builder.build())
        }
    }
    
    /**
     * Create a configured load control with buffer settings.
     */
    fun createLoadControl(context: Context): DefaultLoadControl {
        val allocator = DefaultAllocator(true, PlayerConfig.ALLOCATOR_BUFFER_SIZE)
        
        // Fetch buffer settings from preferences
        val prefs = PlayerPreferences(context)
        val minBufferMs = runBlocking { prefs.minBufferMs.first() }.coerceAtLeast(15000)
        val maxBufferMs = runBlocking { prefs.maxBufferMs.first() }.coerceAtLeast(50000)
        val bufferForPlaybackMs = runBlocking { prefs.bufferForPlaybackMs.first() }.coerceAtLeast(3000)
        val bufferRebufferMs = runBlocking { prefs.bufferForPlaybackAfterRebufferMs.first() }.coerceAtLeast(5000)

        Log.d(TAG, "Buffer config: min=${minBufferMs}ms, max=${maxBufferMs}ms, playback=${bufferForPlaybackMs}ms, rebuffer=${bufferRebufferMs}ms")

        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferRebufferMs)
            .setBackBuffer(PlayerConfig.BACK_BUFFER_DURATION_MS, true)
            .setPrioritizeTimeOverSizeThresholds(false) 
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()
    }
    
    /**
     * Create a custom renderers factory with silence skipping support.
     */
    fun createRenderersFactory(
        context: Context,
        silenceSkippingProcessor: SilenceSkippingAudioProcessor
    ): DefaultRenderersFactory {
        return object : CustomRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(silenceSkippingProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
         .setEnableDecoderFallback(true)
    }
    
    /**
     * Create a fully configured ExoPlayer instance.
     */
    fun createPlayer(
        context: Context,
        trackSelector: DefaultTrackSelector,
        loadControl: DefaultLoadControl,
        renderersFactory: DefaultRenderersFactory,
        dataSourceFactory: DataSource.Factory?
    ): ExoPlayer {
        val factory = dataSourceFactory ?: DefaultDataSource.Factory(context)
        
        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .also {
                Log.d(TAG, "ExoPlayer instance created")
            }
    }
}
