package io.github.aedev.flow.player.stream

import android.net.Uri
import org.schabi.newpipe.extractor.stream.VideoStream

object VideoCodecUtils {
    private val AV1_ITAGS = setOf(394, 395, 396, 397, 398, 399, 400, 401, 571, 694, 695, 696, 697, 698, 699, 700, 701)
    private val VP9_ITAGS = setOf(
        242, 243, 244, 245, 246, 247, 248, 271, 272,
        302, 303, 308, 313, 315,
        330, 331, 332, 333, 334, 335, 336, 337
    )
    private val H264_ITAGS = setOf(
        133, 134, 135, 136, 137, 138, 160,
        264, 266, 298, 299, 300, 301, 304, 305,
        18, 22, 43, 59
    )

    fun codecKeyFromMimeType(mimeType: String): String {
        val m = mimeType.lowercase()
        val codecs = m.substringAfter("codecs=\"", "").substringBefore("\"")
        return when {
            "av01" in codecs -> "av1"
            "vp09" in codecs || "vp9" in codecs -> "vp9"
            "vp08" in codecs || "vp8" in codecs -> "vp8"
            "hev1" in codecs || "hvc1" in codecs -> "hevc"
            "avc1" in codecs -> "h264"
            "webm" in m -> "vp9"
            else -> "h264"
        }
    }

    fun codecKeyFromStream(stream: VideoStream): String {
        val url = try {
            stream.content.takeIf { it.isNotBlank() } ?: stream.url ?: ""
        } catch (_: Exception) {
            ""
        }
        val itag = try {
            Uri.parse(url).getQueryParameter("itag")?.toIntOrNull()
        } catch (_: Exception) {
            null
        }

        when (itag) {
            in AV1_ITAGS -> return "av1"
            in VP9_ITAGS -> return "vp9"
            in H264_ITAGS -> return "h264"
        }

        val fmtMime = try { stream.format?.mimeType?.lowercase() ?: "" } catch (_: Exception) { "" }
        val fmtName = try { stream.format?.name?.lowercase() ?: "" } catch (_: Exception) { "" }
        return when {
            "av01" in fmtMime || "av01" in fmtName || "av1" in fmtName -> "av1"
            "vp09" in fmtMime || "vp9" in fmtMime || "vp9" in fmtName -> "vp9"
            "vp08" in fmtMime || "vp8" in fmtMime || "vp8" in fmtName -> "vp8"
            "webm" in fmtName || "webm" in fmtMime -> "vp9"
            "hev1" in fmtMime || "hvc1" in fmtMime || "hevc" in fmtName -> "hevc"
            else -> "h264"
        }
    }

    fun codecLabelFromKey(key: String): String = when (key) {
        "av1" -> "AV1"
        "vp9" -> "VP9"
        "vp8" -> "VP8"
        "hevc" -> "HEVC"
        "h264" -> "H264"
        else -> key.uppercase()
    }

    fun playbackCodecRank(stream: VideoStream): Int = playbackCodecRank(codecKeyFromStream(stream))

    fun playbackCodecRank(codecKey: String): Int = when (codecKey) {
        "vp9" -> 0
        "h264" -> 1
        "vp8" -> 2
        "hevc" -> 3
        "av1" -> 4
        else -> 5
    }
}
