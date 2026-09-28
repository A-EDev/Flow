package io.github.aedev.flow.data.video.downloader.work

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The last check before a download is called finished: the file opens, carries the tracks it
 * should, and runs about as long as the video. A download that fails it is never shown as done.
 */
@Singleton
class DownloadValidator
    @Inject
    constructor() {
        fun isPlayable(
            file: File,
            expectVideo: Boolean,
            expectedDurationMs: Long,
        ): Boolean {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val mimes = tracks.mapNotNull { it.getString(MediaFormat.KEY_MIME) }
                val hasAudio = mimes.any { it.startsWith("audio/") }
                val hasVideo = mimes.any { it.startsWith("video/") }
                val durationUs =
                    tracks.maxOfOrNull { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L } ?: 0L
                hasAudio && (hasVideo || !expectVideo) && durationMatches(durationUs / 1000L, expectedDurationMs)
            } catch (e: Exception) {
                Log.w(TAG, "Unreadable download ${file.name}", e)
                false
            } finally {
                extractor.release()
            }
        }

        internal companion object {
            private const val TAG = "DownloadValidator"
            private const val TOLERANCE_MS = 5_000L

            /** An unknown expectation passes; otherwise within 5 s or 5 %, whichever is looser. */
            fun durationMatches(
                actualMs: Long,
                expectedMs: Long,
            ): Boolean {
                if (expectedMs <= 0L) return true
                if (actualMs <= 0L) return false
                return abs(actualMs - expectedMs) <= maxOf(TOLERANCE_MS, expectedMs / 20)
            }
        }
    }
