package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.innertube.YouTubeSearchParams
import io.github.aedev.flow.utils.protobuf.ProtobufWriter
import java.util.Base64

internal object ReelParams {
    /** The seedless feed's first page, which carries one entry and the continuation of the rest. */
    const val INITIAL_SEQUENCE = "CA8%3D"

    val SEARCH_SHORTS_FILTER: String? = YouTubeSearchParams.build(contentType = YouTubeSearchParams.ContentType.SHORTS)

    /**
     * Protobuf `{1: videoId}`, base64url — the only accepted way to seed the sequence. The same
     * token in `params` is rejected with HTTP 400 (#931), and the response never contains the seed.
     */
    fun seedSequenceParams(videoId: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(ProtobufWriter.encode { writeString(1, videoId) })
}
