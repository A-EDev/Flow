package io.github.aedev.flow.data.backup

import io.github.aedev.flow.data.local.ChannelSubscription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/** Where a NewPipe entry points: a channel id, or a link that has to be resolved to one first. */
sealed interface NewPipeChannelRef {
    data class Id(
        val channelId: String,
    ) : NewPipeChannelRef

    data class Handle(
        val handle: String,
    ) : NewPipeChannelRef {
        val url: String get() = "https://www.youtube.com/@$handle"
    }

    /** A `/user/` or `/c/` link, which only YouTube can map to a channel. */
    data class Legacy(
        val url: String,
    ) : NewPipeChannelRef
}

data class NewPipeSubscriptionEntry(
    val ref: NewPipeChannelRef,
    val name: String,
)

data class NewPipeSubscriptionExport(
    val json: String,
    val skipped: Int,
)

/**
 * NewPipe's subscription export: `{app_version, app_version_int, subscriptions: [{service_id, url, name}]}`.
 * NewPipe decodes it strictly, so [encode] writes exactly those keys, and only channels it can open:
 * a `/channel/UC…` id or an `@handle`.
 */
object NewPipeSubscriptionCodec {
    private const val YOUTUBE_SERVICE_ID = 0

    private val ChannelIdPattern = Regex("UC[0-9A-Za-z_-]{22}")
    private val HandlePattern = Regex("[0-9A-Za-z_.\\-\\p{L}\\p{M}]{1,100}")
    private val YouTubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    private val reader = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Export(
        @SerialName("app_version") val appVersion: String,
        @SerialName("app_version_int") val appVersionInt: Int,
        val subscriptions: List<Item>,
    )

    @Serializable
    private data class Item(
        @SerialName("service_id") val serviceId: Int,
        val url: String,
        val name: String,
    )

    @Serializable
    private data class Import(
        val subscriptions: List<ImportItem>,
    )

    @Serializable
    private data class ImportItem(
        @SerialName("service_id") val serviceId: Int = YOUTUBE_SERVICE_ID,
        val url: String = "",
        val name: String = "",
    )

    fun encode(
        subscriptions: List<ChannelSubscription>,
        appVersion: String,
        appVersionInt: Int,
    ): NewPipeSubscriptionExport {
        val items =
            subscriptions.mapNotNull { subscription ->
                val url = channelUrl(subscription.channelId) ?: return@mapNotNull null
                Item(YOUTUBE_SERVICE_ID, url, subscription.channelName.ifBlank { subscription.channelId.trim() })
            }
        val json = Json.encodeToString(Export.serializer(), Export(appVersion, appVersionInt, items))
        return NewPipeSubscriptionExport(json, skipped = subscriptions.size - items.size)
    }

    /** Fails on anything that is not a NewPipe export, so a wrong file never reads as an empty one. */
    fun decode(json: String): Result<List<NewPipeSubscriptionEntry>> =
        runCatching {
            reader
                .decodeFromString(Import.serializer(), json)
                .subscriptions
                .filter { it.serviceId == YOUTUBE_SERVICE_ID }
                .mapNotNull { item -> parseChannelUrl(item.url)?.let { NewPipeSubscriptionEntry(it, item.name.trim()) } }
                .distinctBy { it.ref }
        }

    /** A stored channel id as a URL NewPipe can open, or null when the id is neither a UC id nor an @handle. */
    private fun channelUrl(channelId: String): String? {
        val id = channelId.trim()
        return when {
            ChannelIdPattern.matches(id) -> "https://www.youtube.com/channel/$id"
            id.startsWith("@") && HandlePattern.matches(id.drop(1)) -> "https://www.youtube.com/$id"
            else -> null
        }
    }

    private fun parseChannelUrl(url: String): NewPipeChannelRef? {
        val trimmed = url.trim()
        val uri = runCatching { URI(if ("://" in trimmed) trimmed else "https://$trimmed") }.getOrNull() ?: return null
        if (uri.host?.lowercase() !in YouTubeHosts) return null
        val segments =
            uri.path
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        val first = segments.firstOrNull() ?: return null
        val second = segments.getOrNull(1)
        return when {
            first == "channel" && second != null && ChannelIdPattern.matches(second) -> {
                NewPipeChannelRef.Id(second)
            }

            first.startsWith("@") && HandlePattern.matches(first.drop(1)) -> {
                NewPipeChannelRef.Handle(first.drop(1))
            }

            (first == "user" || first == "c") && !second.isNullOrBlank() -> {
                NewPipeChannelRef.Legacy("https://www.youtube.com/$first/$second")
            }

            else -> {
                null
            }
        }
    }
}
