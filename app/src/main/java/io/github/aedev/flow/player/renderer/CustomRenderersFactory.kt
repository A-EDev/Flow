package io.github.aedev.flow.player.renderer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleDecoder
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.renderer.subtitle.DelayedSubtitleDecoder
import io.github.aedev.flow.player.renderer.subtitle.Srv3SubtitleParser
import io.github.aedev.flow.player.subtitle.SubtitleDelay
import java.util.ArrayList

/**
 * A [DefaultRenderersFactory] that uses [CustomMediaCodecVideoRenderer] for video rendering
 * and optionally installs custom [AudioProcessor]s (e.g. the parametric EQ) into the audio sink.
 */
open class CustomRenderersFactory(
    context: Context,
    private val audioProcessors: Array<AudioProcessor> = emptyArray(),
    private val subtitleDelay: SubtitleDelay = SubtitleDelay(),
) : DefaultRenderersFactory(context) {
    /**
     * Media3's decoders plus srv3 (YouTube's styled, positioned caption XML), with every track a
     * Media3 parser reads shifted by [subtitleDelay]. CEA-608/708 keep Media3's own decoders.
     */
    private val subtitleDecoderFactory =
        object : SubtitleDecoderFactory {
            private val parsers = DefaultSubtitleParserFactory()

            override fun supportsFormat(format: Format): Boolean =
                format.sampleMimeType == Srv3SubtitleParser.MIME_TYPE || SubtitleDecoderFactory.DEFAULT.supportsFormat(format)

            override fun createDecoder(format: Format): SubtitleDecoder =
                when {
                    format.sampleMimeType == Srv3SubtitleParser.MIME_TYPE -> {
                        DelayedSubtitleDecoder("Srv3SubtitleDecoder", Srv3SubtitleParser(), subtitleDelay)
                    }

                    parsers.supportsFormat(format) -> {
                        DelayedSubtitleDecoder("Delayed${format.sampleMimeType}", parsers.create(format), subtitleDelay)
                    }

                    else -> {
                        SubtitleDecoderFactory.DEFAULT.createDecoder(format)
                    }
                }
        }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        if (audioProcessors.isEmpty()) {
            return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        }
        return DefaultAudioSink
            .Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(audioProcessors)
            .build()
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        // Mirrors DefaultRenderersFactory.buildVideoRenderers so the custom renderer keeps the same
        // configuration; this override is the reason setEnableMediaCodecVideoRendererDurationToProgressUs
        // on the factory does nothing for us, so the flag is applied here instead.
        out.add(
            CustomMediaCodecVideoRenderer(
                MediaCodecVideoRenderer
                    .Builder(context)
                    .setCodecAdapterFactory(codecAdapterFactory)
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                    .setEnableDurationToProgressUs(PlayerConfig.ENABLE_DYNAMIC_SCHEDULING),
            ),
        )
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            TextRenderer(output, outputLooper, subtitleDecoderFactory).apply {
                experimentalSetLegacyDecodingEnabled(true)
            },
        )
    }
}
