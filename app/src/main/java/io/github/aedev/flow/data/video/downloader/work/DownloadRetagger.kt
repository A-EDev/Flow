package io.github.aedev.flow.data.video.downloader.work

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.video.downloader.tags.CoverArtLoader
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTagReader
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.github.aedev.flow.data.video.downloader.tags.Mp4TagWriter
import io.github.aedev.flow.data.video.storage.DownloadFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What one pass over older downloads did. */
data class RetagResult(
    val tagged: Int,
    val skipped: Int,
    /** Videos left for a later pass because their watch page did not load. */
    val deferred: Int = 0,
)

/**
 * Tags the downloads older versions saved without tags, once. Only plain files the app wrote are
 * touched: each is tagged as a copy beside it, checked to still play, and then renamed over the
 * original, so an interrupted pass never leaves a broken file. Documents in a picked folder are
 * skipped, since a provider can only be written over in place.
 */
@Singleton
class DownloadRetagger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadDao: DownloadDao,
        private val tagReader: DownloadTagReader,
        private val tagWriter: Mp4TagWriter,
        private val enricher: DownloadMetadataEnricher,
        private val coverArt: CoverArtLoader,
        private val validator: DownloadValidator,
    ) {
        /**
         * Tags every candidate. A video whose watch page does not load is deferred rather than
         * tagged without its channel and counts, unless [acceptPartial] says this is the last try.
         */
        suspend fun run(
            acceptPartial: Boolean,
            onProgress: suspend (done: Int, total: Int) -> Unit,
        ): RetagResult {
            val rows = downloadDao.getAllDownloadsWithItemsOnce().filter(::isCandidate)
            val outcomes =
                rows.mapIndexed { index, row ->
                    retag(row, acceptPartial).also { onProgress(index + 1, rows.size) }
                }
            return RetagResult(
                tagged = outcomes.count { it == Outcome.TAGGED },
                skipped = outcomes.count { it == Outcome.SKIPPED },
                deferred = outcomes.count { it == Outcome.DEFERRED },
            )
        }

        private enum class Outcome { TAGGED, SKIPPED, DEFERRED, ALREADY_TAGGED }

        private suspend fun retag(
            row: DownloadWithItems,
            acceptPartial: Boolean,
        ): Outcome {
            val file = File(row.items.single().filePath)
            if (!file.isFile) return Outcome.SKIPPED
            if (tagReader.read(Uri.fromFile(file))?.flow != null) return Outcome.ALREADY_TAGGED
            val legacy = LegacyDownloadRequest.from(row)
            val request =
                enricher.enrich(
                    if (row.isAudioOnly && legacy.tags.kind == DownloadKind.VIDEO) {
                        legacy.copy(tags = legacy.tags.copy(kind = DownloadKind.MUSIC))
                    } else {
                        legacy
                    },
                )
            if (!request.isMusic && request.tags.channelId == null && !acceptPartial) return Outcome.DEFERRED
            val cover =
                row.download.thumbnailPath
                    ?.let(::File)
                    ?.takeIf { it.isFile }
                    ?.readBytes()
                    ?: coverArt.load(request.tags.thumbnailUrl, request.tags.kind)
            return if (writeTagged(row, file, request.tags, cover)) Outcome.TAGGED else Outcome.SKIPPED
        }

        private suspend fun writeTagged(
            row: DownloadWithItems,
            file: File,
            tags: DownloadTags,
            cover: ByteArray?,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val copy = File(file.parentFile, ".${file.name}$COPY_SUFFIX")
                try {
                    if ((file.parentFile?.usableSpace ?: 0L) < file.length() * 2) return@withContext false
                    file.copyTo(copy, overwrite = true)
                    tagWriter.write(copy, tags, cover)
                    val expectedMs = row.download.duration * 1000
                    if (!validator.isPlayable(
                            copy,
                            expectVideo = !row.isAudioOnly,
                            expectedDurationMs = expectedMs,
                        )
                    ) {
                        return@withContext false
                    }
                    if (!copy.renameTo(file)) return@withContext false
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    true
                } catch (e: IOException) {
                    Log.w(TAG, "${row.download.videoId}: not tagged", e)
                    false
                } finally {
                    copy.delete()
                }
            }

        internal companion object {
            const val TAG = "DownloadRetagger"
            private const val COPY_SUFFIX = ".retag"
            private const val RECOVERED_PREFIX = "recovered_"
            private val TAGGABLE = setOf("mp4", "m4a")

            /** A finished download from before tags, under a real video id, saved as one MP4 or M4A file the app owns. */
            fun isCandidate(row: DownloadWithItems): Boolean {
                val item = row.items.singleOrNull() ?: return false
                return row.overallStatus == DownloadItemStatus.COMPLETED &&
                    row.download.requestJson == null &&
                    !row.download.videoId.startsWith(RECOVERED_PREFIX) &&
                    !DownloadFiles.isDocument(item.filePath) &&
                    item.filePath.substringAfterLast('.', "").lowercase() in TAGGABLE
            }
        }
    }
