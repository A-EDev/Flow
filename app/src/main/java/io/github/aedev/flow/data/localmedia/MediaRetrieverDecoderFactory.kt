package io.github.aedev.flow.data.localmedia

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext

/** Runs a `MediaMetadataRetriever`-backed Coil decoder on [PerformanceDispatcher.mediaRetriever]. */
class MediaRetrieverDecoderFactory(
    private val delegate: Decoder.Factory,
) : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val decoder = delegate.create(result, options, imageLoader) ?: return null
        return object : Decoder {
            override suspend fun decode(): DecodeResult? = withContext(PerformanceDispatcher.mediaRetriever) { decoder.decode() }
        }
    }
}
