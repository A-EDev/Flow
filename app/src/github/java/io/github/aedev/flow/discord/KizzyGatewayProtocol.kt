package io.github.aedev.flow.discord

import org.json.JSONArray
import org.json.JSONObject

internal object KizzyGatewayProtocol {
    const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    fun identify(token: String): String = JSONObject()
        .put("op", 2)
        .put(
            "d",
            JSONObject()
                .put("token", token)
                .put("capabilities", 65)
                .put("compress", false)
                .put("large_threshold", 100)
                .put(
                    "properties",
                    JSONObject()
                        .put("os", "Android")
                        .put("browser", "Discord Android")
                        .put("device", "Flow"),
                ),
        )
        .toString()

    fun heartbeat(sequence: Int?): String = JSONObject()
        .put("op", 1)
        .put("d", sequence ?: JSONObject.NULL)
        .toString()

    fun presence(
        payload: DiscordPresencePayload?,
        applicationId: String,
        resolvedImage: String?,
    ): String {
        val activities = JSONArray()
        if (payload != null) {
            val activity = JSONObject()
                .put("name", "Flow")
                .put("type", if (payload.type == DiscordActivityType.LISTENING) 2 else 3)
                .put("details", payload.details)
                .put("state", payload.state)
                .put("application_id", applicationId)

            if (payload.startTimestampSeconds != null || payload.endTimestampSeconds != null) {
                activity.put(
                    "timestamps",
                    JSONObject().apply {
                        payload.startTimestampSeconds?.let { put("start", it * 1_000L) }
                        payload.endTimestampSeconds?.let { put("end", it * 1_000L) }
                    },
                )
            }
            if (!resolvedImage.isNullOrBlank()) {
                activity.put(
                    "assets",
                    JSONObject()
                        .put("large_image", resolvedImage)
                        .put("large_text", payload.largeImageText),
                )
            }
            activities.put(activity)
        }

        return JSONObject()
            .put("op", 3)
            .put(
                "d",
                JSONObject()
                    .put("since", 0)
                    .put("activities", activities)
                    .put("status", "online")
                    .put("afk", false),
            )
            .toString()
    }
}
