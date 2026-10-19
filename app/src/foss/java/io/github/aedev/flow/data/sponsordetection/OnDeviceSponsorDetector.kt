package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.player.stream.ResolvedCaption

internal class OnDeviceSponsorDetector(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    fun isModelInstalled(): Boolean = false

    suspend fun predict(
        videoId: String,
        subtitles: List<ResolvedCaption>,
        onProvisional: suspend (List<SponsorBlockSegment>) -> Unit = {},
    ): List<SponsorBlockSegment> = emptyList()

    suspend fun close() = Unit
}
