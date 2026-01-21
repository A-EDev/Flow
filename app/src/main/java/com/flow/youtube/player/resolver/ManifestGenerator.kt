package com.flow.youtube.player.resolver

import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.services.youtube.ItagItem

object ManifestGenerator {
    
    fun generateOtfManifest(stream: Stream, itagItem: ItagItem, durationSeconds: Long): String? {
        return try {
            YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(
                stream.content,
                itagItem,
                durationSeconds
            )
        } catch (e: Exception) {
            null
        }
    }

    fun generateProgressiveManifest(stream: Stream, itagItem: ItagItem, durationSeconds: Long): String? {
        return try {
            YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                stream.content,
                itagItem,
                durationSeconds
            )
        } catch (e: Exception) {
            null
        }
    }
}
