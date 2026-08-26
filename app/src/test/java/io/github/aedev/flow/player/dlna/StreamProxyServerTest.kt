package io.github.aedev.flow.player.dlna

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamProxyServerTest {
    @Test
    fun `video streams get an mp4 segment extension`() {
        assertThat(StreamProxyServer.segmentFileExtension("video/mp4")).isEqualTo("mp4")
    }

    @Test
    fun `audio streams get an m4a segment extension`() {
        assertThat(StreamProxyServer.segmentFileExtension("audio/mp4")).isEqualTo("m4a")
    }

    @Test
    fun `webm streams keep their own container extension`() {
        assertThat(StreamProxyServer.segmentFileExtension("video/webm")).isEqualTo("webm")
        assertThat(StreamProxyServer.segmentFileExtension("audio/webm")).isEqualTo("weba")
    }

    @Test
    fun `unknown content types fall back to mp4`() {
        assertThat(StreamProxyServer.segmentFileExtension("")).isEqualTo("mp4")
        assertThat(StreamProxyServer.segmentFileExtension("application/octet-stream")).isEqualTo("mp4")
    }

    /**
     * DlnaCastManager only builds HLS variants from mp4/avc video and mp4/m4a/aac audio, so these
     * are the only content types whose extension an ffmpeg-based renderer ever validates.
     */
    @Test
    fun `content types referenced from generated playlists map onto ffmpeg's allow-list`() {
        // libavformat/hls.c `allowed_segment_extensions` default; ffmpeg 7.1+ drops anything else.
        val allowed =
            setOf(
                "3gp",
                "aac",
                "avi",
                "ac3",
                "eac3",
                "flac",
                "mkv",
                "m3u8",
                "m4a",
                "m4s",
                "m4v",
                "mpg",
                "mov",
                "mp2",
                "mp3",
                "mp4",
                "mpeg",
                "mpegts",
                "ogg",
                "ogv",
                "oga",
                "ts",
                "vob",
                "wav",
            )
        val hlsContentTypes = listOf("video/mp4", "audio/mp4")
        assertThat(allowed).containsAtLeastElementsIn(hlsContentTypes.map(StreamProxyServer::segmentFileExtension))
    }
}
