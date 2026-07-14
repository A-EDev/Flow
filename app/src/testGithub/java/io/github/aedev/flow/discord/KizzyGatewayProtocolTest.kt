package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class KizzyGatewayProtocolTest {
    @Test
    fun `identify contains user token without logging or transforming it`() {
        val payload = JSONObject(KizzyGatewayProtocol.identify("user-token"))

        assertThat(payload.getInt("op")).isEqualTo(2)
        assertThat(payload.getJSONObject("d").getString("token")).isEqualTo("user-token")
    }

    @Test
    fun `watching activity uses supplied application and millisecond timestamps`() {
        val payload = JSONObject(
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
            ),
        )
        val activity = payload.getJSONObject("d").getJSONArray("activities").getJSONObject(0)

        assertThat(activity.getInt("type")).isEqualTo(3)
        assertThat(activity.getString("application_id")).isEqualTo("123")
        assertThat(activity.getJSONObject("timestamps").getLong("start")).isEqualTo(10_000L)
        assertThat(activity.getJSONObject("assets").getString("large_image"))
            .isEqualTo("mp:external/asset")
    }

    @Test
    fun `clear presence sends an empty activity list`() {
        val payload = JSONObject(KizzyGatewayProtocol.presence(null, "123", null))

        assertThat(payload.getJSONObject("d").getJSONArray("activities").length()).isEqualTo(0)
    }
}
