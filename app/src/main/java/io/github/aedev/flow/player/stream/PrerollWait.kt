package io.github.aedev.flow.player.stream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * How long GVS holds a video's streams back behind the pre-roll its /player answer placed. An answer
 * with a start ad gets 403 on every byte of the content, the first included, until about that long
 * after the answer. Measured on TV_TIZEN 2026-10-08 (5 s skip offset: refused, then served 2 s
 * later); NewTube measured the same rule on WEB_EMBED at about 0.8 of the skip offset.
 */
internal object PrerollWait {
    const val MAX_WAIT_MS = 8_000L
    private const val SHARE_OF_SKIP_OFFSET = 0.8
    private const val START_PLACEMENT = "AD_PLACEMENT_KIND_START"

    fun of(adPlacements: JsonArray?): Long {
        val skipOffsets =
            adPlacements
                .orEmpty()
                .mapNotNull { (it as? JsonObject)?.child("adPlacementRenderer") }
                .filter { it.child("config")?.child("adPlacementConfig")?.text("kind") == START_PLACEMENT }
                .map { renderer ->
                    renderer
                        .child("renderer")
                        ?.child("instreamVideoAdRenderer")
                        ?.text("skipOffsetMilliseconds")
                        ?.toLongOrNull()
                        ?.let { (it * SHARE_OF_SKIP_OFFSET).toLong() }
                        ?: MAX_WAIT_MS
                }
        return skipOffsets.maxOrNull()?.coerceAtMost(MAX_WAIT_MS) ?: 0L
    }

    private fun JsonElement.child(name: String): JsonObject? = (this as? JsonObject)?.get(name) as? JsonObject

    private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.content
}
