package io.github.aedev.flow.discord

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

internal object DiscordLoginTokenExtractor {
    fun isAuthenticatedAppUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("discord.com", ignoreCase = true) &&
            uri.path.trimEnd('/') == "/app"
    }.getOrDefault(false)

    fun decodeJavascriptValue(value: String): String? = runCatching {
        val element = Json.parseToJsonElement(value)
        if (element is JsonNull) null else element.jsonPrimitive.content
    }.getOrNull()
        ?.trim()
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotEmpty() && it != "null" }
}
