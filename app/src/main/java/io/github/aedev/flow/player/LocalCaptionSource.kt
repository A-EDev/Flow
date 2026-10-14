package io.github.aedev.flow.player

import io.github.aedev.flow.player.stream.ResolvedCaption

/** A local file's captions and the timing saved for it. */
data class LocalCaptions(
    val captions: List<ResolvedCaption>,
    val offsetMs: Long,
)

/** Captions for a device file or download a queue advances onto, where no screen prepares them. */
fun interface LocalCaptionSource {
    suspend fun captionsFor(
        videoId: String,
        path: String,
    ): LocalCaptions
}
