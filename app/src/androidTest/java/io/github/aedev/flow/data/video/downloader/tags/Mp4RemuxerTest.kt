package io.github.aedev.flow.data.video.downloader.tags

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

/**
 * Remuxes streams encoded on the device (fragmented MP4 like YouTube's H.264/AAC DASH, WebM like
 * its VP9/Opus DASH), then checks the result through the platform extractor and retriever.
 */
@RunWith(AndroidJUnit4::class)
class Mp4RemuxerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val remuxer = Mp4Remuxer()

    private val tags =
        DownloadTags(
            kind = DownloadKind.MUSIC,
            videoId = "dQw4w9WgXcQ",
            title = "Remux title",
            artists = listOf("Artist One", "Artist Two"),
            channelId = "UC123",
            album = "Remux album",
            trackNumber = 2,
            trackTotal = 9,
            releaseDate = "2020-01-31",
            sourceUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            viewCount = 12L,
            lyrics = "line one",
        )

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "remux-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun h264AndAacFromFragmentedMp4BecomeOneInterleavedTaggedMp4() {
        val video = File(dir, "video.mp4").also { SyntheticMedia.fragmentedMp4(it, MimeTypes.VIDEO_H264) }
        val audio = File(dir, "audio.m4a").also { SyntheticMedia.fragmentedMp4(it, MimeTypes.AUDIO_AAC) }
        val out = File(dir, "out.mp4")

        val result = remuxer.remux(video.path, audio.path, out.path, tags)

        assertEquals(Mp4Remuxer.Result.Success(MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC), result)
        assertEquals(listOf(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC), trackMimes(out))
        assertInterleaved(out)

        Mp4TagWriter().write(out, tags, SyntheticMedia.jpeg())

        assertEquals(listOf(MimeTypes.VIDEO_H264, MimeTypes.AUDIO_AAC), trackMimes(out))
        assertPlatformTags(out)
        val embedded = runBlocking { DownloadTagReader(context).read(Uri.fromFile(out)) }
        assertEquals(tags, embedded?.flow)
        assertNotNull(embedded?.cover)
    }

    @Test
    fun aacOnlyBecomesAnM4aWithTags() {
        val audio = File(dir, "audio.m4a").also { SyntheticMedia.fragmentedMp4(it, MimeTypes.AUDIO_AAC) }
        val out = File(dir, "out.m4a")

        val result = remuxer.remux(null, audio.path, out.path, tags)

        assertEquals(Mp4Remuxer.Result.Success(MimeTypes.AUDIO_MP4, null, MimeTypes.AUDIO_AAC), result)
        assertEquals(listOf(MimeTypes.AUDIO_AAC), trackMimes(out))
        Mp4TagWriter().write(out, tags, SyntheticMedia.jpeg())
        assertPlatformTags(out)
    }

    @Test
    fun vp9FromWebmGoesIntoMp4() {
        assumeTrue(SyntheticMedia.hasEncoder(MimeTypes.VIDEO_VP9))
        val video = File(dir, "video.webm").also { SyntheticMedia.webm(it, MimeTypes.VIDEO_VP9) }
        val audio = File(dir, "audio.m4a").also { SyntheticMedia.fragmentedMp4(it, MimeTypes.AUDIO_AAC) }
        val out = File(dir, "out.mp4")

        val result = remuxer.remux(video.path, audio.path, out.path, tags)

        assertEquals(Mp4Remuxer.Result.Success(MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_VP9, MimeTypes.AUDIO_AAC), result)
        assertEquals(listOf(MimeTypes.VIDEO_VP9, MimeTypes.AUDIO_AAC), trackMimes(out))
        assertInterleaved(out)
    }

    @Test
    fun opusIsRefusedAndNoOutputIsLeft() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && SyntheticMedia.hasEncoder(MimeTypes.AUDIO_OPUS))
        val audio = File(dir, "audio.webm").also { SyntheticMedia.webm(it, MimeTypes.AUDIO_OPUS) }
        val out = File(dir, "out.m4a")

        val result = remuxer.remux(null, audio.path, out.path, tags)

        assertEquals(Mp4Remuxer.Reason.UNSUPPORTED_AUDIO, (result as Mp4Remuxer.Result.Failure).reason)
        assertFalse(out.exists())
    }

    @Test
    fun cancellationStopsAndDeletesTheOutput() {
        val audio = File(dir, "audio.m4a").also { SyntheticMedia.fragmentedMp4(it, MimeTypes.AUDIO_AAC) }
        val out = File(dir, "out.m4a")

        val result = remuxer.remux(null, audio.path, out.path, tags, isCancelled = { true })

        assertEquals(Mp4Remuxer.Reason.CANCELLED, (result as Mp4Remuxer.Result.Failure).reason)
        assertFalse(out.exists())
    }

    @Test
    fun missingInputFailsCleanly() {
        val result = remuxer.remux(null, File(dir, "nope.m4a").path, File(dir, "out.m4a").path)

        assertEquals(Mp4Remuxer.Reason.INPUT_MISSING, (result as Mp4Remuxer.Result.Failure).reason)
    }

    private fun trackMimes(file: File): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return List(extractor.trackCount) { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
        } finally {
            extractor.release()
        }
    }

    private fun assertPlatformTags(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            assertEquals(tags.title, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            assertEquals("Artist One, Artist Two", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            assertEquals(tags.album, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
            assertNotNull(retriever.embeddedPicture)
        } finally {
            retriever.release()
        }
    }

    /** Chunks of the two tracks alternate through mdat instead of one track following the other. */
    private fun assertInterleaved(file: File) {
        val perTrack = chunkOffsetsPerTrack(file.readBytes())
        assertEquals(2, perTrack.size)
        val order = perTrack.flatMapIndexed { track, offsets -> offsets.map { it to track } }.sortedBy { it.first }.map { it.second }
        val switches = order.zipWithNext().count { (a, b) -> a != b }
        assertTrue("expected interleaved chunks, got $switches track switches", switches >= 4)
    }

    private fun chunkOffsetsPerTrack(bytes: ByteArray): List<List<Long>> {
        val moov = boxes(bytes, 0, bytes.size).first { it.first == "moov" }
        return boxes(bytes, moov.second, moov.third).filter { it.first == "trak" }.map { trak ->
            var range = trak
            for (type in listOf("mdia", "minf", "stbl")) range = boxes(bytes, range.second, range.third).first { it.first == type }
            val table = boxes(bytes, range.second, range.third).first { it.first == "stco" || it.first == "co64" }
            val buffer = ByteBuffer.wrap(bytes, table.second + 4, table.third - table.second - 4)
            List(buffer.int) { if (table.first == "co64") buffer.long else buffer.int.toLong() and 0xFFFFFFFFL }
        }
    }

    /** (type, payload start, end) of each box in [from, to). */
    private fun boxes(
        bytes: ByteArray,
        from: Int,
        to: Int,
    ): List<Triple<String, Int, Int>> {
        val result = mutableListOf<Triple<String, Int, Int>>()
        var position = from
        while (position + 8 <= to) {
            val size32 = ByteBuffer.wrap(bytes, position, 4).int.toLong() and 0xFFFFFFFFL
            val type = String(bytes, position + 4, 4, Charsets.ISO_8859_1)
            val (header, size) =
                when (size32) {
                    0L -> 8 to (to - position).toLong()
                    1L -> 16 to ByteBuffer.wrap(bytes, position + 8, 8).long
                    else -> 8 to size32
                }
            result += Triple(type, position + header, (position + size).toInt())
            position += size.toInt()
        }
        return result
    }
}
