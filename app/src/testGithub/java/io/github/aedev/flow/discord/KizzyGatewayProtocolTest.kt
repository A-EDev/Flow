package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class KizzyGatewayProtocolTest {
    @Test
    fun `identify contains user token without logging or transforming it`() {
        val payload = Json.parseToJsonElement(KizzyGatewayProtocol.identify("user-token")).jsonObject

        assertThat(payload.getValue("op").jsonPrimitive.content.toInt()).isEqualTo(2)
        assertThat(payload.getValue("d").jsonObject.getValue("token").jsonPrimitive.content)
            .isEqualTo("user-token")
    }

    @Test
    fun `watching activity uses supplied application and millisecond timestamps`() {
        val payload = Json.parseToJsonElement(
            KizzyGatewayProtocol.presence(
                payload = DiscordPresencePayload(
                    type = DiscordActivityType.WATCHING,
                    mediaId = "video",
                    details = "Title",
                    state = "by Creator",
                    largeImage = "https://example.com/image.jpg",
                    largeImageText = "Title",
                    startTimestampSeconds = 10,
                    endTimestampSeconds = 20,
                ),
                applicationId = "123",
                resolvedImage = "mp:external/asset",
                activityName = "Flow",
            ),
        ).jsonObject
        val activity = payload.getValue("d").jsonObject
            .getValue("activities").jsonArray[0].jsonObject

        assertThat(activity.getValue("type").jsonPrimitive.content.toInt()).isEqualTo(3)
        assertThat(activity.getValue("application_id").jsonPrimitive.content).isEqualTo("123")
        assertThat(activity.getValue("timestamps").jsonObject.getValue("start").jsonPrimitive.content.toLong())
            .isEqualTo(10_000L)
        assertThat(activity.getValue("assets").jsonObject.getValue("large_image").jsonPrimitive.content)
            .isEqualTo("mp:external/asset")
    }

    @Test
    fun `clear presence sends an empty activity list`() {
        val payload = Json.parseToJsonElement(
            KizzyGatewayProtocol.presence(null, "123", null, "Flow"),
        ).jsonObject

        assertThat(payload.getValue("d").jsonObject.getValue("activities").jsonArray).isEmpty()
    }
}
