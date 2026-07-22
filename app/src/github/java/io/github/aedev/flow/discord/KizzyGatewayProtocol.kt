package io.github.aedev.flow.discord

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object KizzyGatewayProtocol {
    const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    fun identify(token: String): String = buildJsonObject {
        put("op", 2)
        put(
            "d",
            buildJsonObject {
                put("token", token)
                put("capabilities", 65)
                put("compress", false)
                put("large_threshold", 100)
                put(
                    "properties",
                    buildJsonObject {
                        put("os", "Android")
                        put("browser", "Discord Android")
                        put("device", "Flow")
                    },
                )
            },
        )
    }.toString()

    fun heartbeat(sequence: Int?): String = buildJsonObject {
        put("op", 1)
        if (sequence == null) put("d", JsonNull) else put("d", sequence)
    }.toString()

    fun presence(
        payload: DiscordPresencePayload?,
        applicationId: String,
        resolvedImage: String?,
        activityName: String,
    ): String {
        val activities = buildJsonArray {
            if (payload != null) {
                add(
                    buildJsonObject {
                        put("name", activityName)
                        put("type", if (payload.type == DiscordActivityType.LISTENING) 2 else 3)
                        put("details", payload.details)
                        put("state", payload.state)
                        put("application_id", applicationId)

                        if (payload.startTimestampSeconds != null || payload.endTimestampSeconds != null) {
                            put(
                                "timestamps",
                                buildJsonObject {
                                    payload.startTimestampSeconds?.let { put("start", it * 1_000L) }
                                    payload.endTimestampSeconds?.let { put("end", it * 1_000L) }
                                },
                            )
                        }
                        if (!resolvedImage.isNullOrBlank()) {
                            put(
                                "assets",
                                buildJsonObject {
                                    put("large_image", resolvedImage)
                                    put("large_text", payload.largeImageText)
                                },
                            )
                        }
                    },
                )
            }
        }

        return buildJsonObject {
            put("op", 3)
            put(
                "d",
                buildJsonObject {
                    put("since", 0)
                    put("activities", activities)
                    put("status", "online")
                    put("afk", false)
                },
            )
        }.toString()
    }
}
