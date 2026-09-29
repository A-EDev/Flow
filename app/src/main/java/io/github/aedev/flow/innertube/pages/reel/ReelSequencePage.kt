package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.booleanOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.renderer.largestImageUrl
import io.github.aedev.flow.innertube.pages.stringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * One position in the reel feed. The sequence names the reel and how to open it, nothing more: no
 * client returns a title, channel or count on an entry (probed 2026-09-19), so those are looked up
 * per reel through `reel_item_watch` or `/player`.
 */
data class ReelEntry(
    val videoId: String,
    val playerParams: String? = null,
    val params: String? = null,
    val posterUrl: String? = null,
    val softRefreshContinuation: String? = null,
)

data class ReelSequencePage(
    val entries: List<ReelEntry>,
    val continuation: String?,
)

fun JsonObject.toReelSequencePage(): ReelSequencePage {
    val entries =
        this["entries"]
            .arrayOrNull()
            ?.mapNotNull { entry ->
                val endpoint =
                    entry
                        .objectOrNull()
                        ?.get("command")
                        .objectOrNull()
                        ?.get("reelWatchEndpoint")
                        .objectOrNull() ?: return@mapNotNull null
                val videoId = endpoint["videoId"].stringOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (endpoint.isAd()) return@mapNotNull null
                ReelEntry(
                    videoId = videoId,
                    playerParams = endpoint["playerParams"].stringOrNull(),
                    params = endpoint["params"].stringOrNull(),
                    posterUrl = endpoint["thumbnail"].largestImageUrl(),
                    softRefreshContinuation = endpoint["softRefreshContinuation"].stringOrNull(),
                )
            }.orEmpty()
    // ANDROID, IOS and VISIONOS answer with a bare token; WEB and MWEB wrap it in an endpoint.
    val continuation =
        this["continuation"].stringOrNull()
            ?: this["continuationEndpoint"].objectOrNull()?.let { endpoint ->
                endpoint["continuationCommand"].objectOrNull()?.get("token").stringOrNull()
                    ?: endpoint["reelWatchSequenceEndpoint"].objectOrNull()?.get("sequenceParams").stringOrNull()
            }
    return ReelSequencePage(entries, continuation?.takeIf { it.isNotBlank() })
}

/**
 * The seedless chain and channel sequences interleave ad creatives (3–7 of a 15–29 entry page on
 * IOS, probed 2026-09-21): `videoType` names them on the app clients, `adClientParams.isAd` on
 * every client, and they carry no overlay. They are ordinary uploads to `/player`, so nothing
 * later in the pipeline could tell them apart.
 */
private fun JsonObject.isAd(): Boolean =
    this["videoType"].stringOrNull() == "REEL_VIDEO_TYPE_AD" ||
        this["adClientParams"].objectOrNull()?.get("isAd").booleanOrNull() == true
