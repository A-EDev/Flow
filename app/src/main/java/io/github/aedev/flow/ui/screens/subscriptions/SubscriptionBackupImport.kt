package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.local.ChannelSubscription
import org.json.JSONObject

/**
 * The channels in a NewPipe subscription export. Entries without a name or a `/channel/` or `/user/`
 * URL are skipped; the avatar is left blank for the screen to load later.
 */
internal fun parseNewPipeSubscriptionExport(json: String): List<ChannelSubscription> {
    val root = JSONObject(json)
    if (!root.has("subscriptions")) return emptyList()
    val entries = root.getJSONArray("subscriptions")
    return (0 until entries.length()).mapNotNull { index ->
        val item = entries.getJSONObject(index)
        val url = item.optString("url")
        val name = item.optString("name")
        val channelId =
            when {
                url.contains("/channel/") -> url.substringAfter("/channel/")
                url.contains("/user/") -> url.substringAfter("/user/")
                else -> ""
            }.substringBefore("/").substringBefore("?")
        if (name.isEmpty() || channelId.isEmpty()) return@mapNotNull null
        ChannelSubscription(
            channelId = channelId,
            channelName = name,
            channelThumbnail = "",
            subscribedAt = System.currentTimeMillis(),
        )
    }
}
