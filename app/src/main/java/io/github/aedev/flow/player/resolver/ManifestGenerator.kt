package io.github.aedev.flow.player.resolver

import android.util.Log
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.services.youtube.ItagItem

object ManifestGenerator {
    private const val TAG = "ManifestGenerator"
    
    fun generateOtfManifest(stream: Stream, itagItem: ItagItem, durationSeconds: Long): String? {
        return try {
            YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(
                stream.content,
                itagItem,
                durationSeconds
            )
        } catch (e: Exception) {
            Log.w(TAG, "OTF manifest generation failed for itag=${itagItem.id}: ${e.javaClass.simpleName}: ${e.message}")
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
            Log.w(TAG, "Progressive manifest generation failed for itag=${itagItem.id}: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
