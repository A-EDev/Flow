package io.github.aedev.flow.data.scrobble

import android.util.Log
import io.github.aedev.flow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** ListenBrainz's submit API, signed in with the user token from their settings page. */
class ListenBrainzClient(
    private val http: () -> OkHttpClient,
) {
    suspend fun signIn(token: String): Result<ScrobbleAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url("$BASE_URL/validate-token")
                        .header(AUTH, "Token $token")
                        .get()
                        .build()
                val body =
                    http().newCall(request).execute().use { response ->
                        Json.parseToJsonElement(response.body.string()).jsonObject
                    }
                if (body["valid"]?.jsonPrimitive?.boolean != true) throw IOException(body.message() ?: "Invalid token")
                ScrobbleAccount(userName = body["user_name"]?.jsonPrimitive?.content.orEmpty(), secret = token)
            }
        }

    /** The user's most listened artists over the past year; empty when ListenBrainz has no stats yet. */
    suspend fun topArtists(
        account: ScrobbleAccount,
        limit: Int,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(
                            BASE_URL
                                .toHttpUrl()
                                .newBuilder()
                                .addPathSegments("stats/user")
                                .addPathSegment(account.userName)
                                .addPathSegment("artists")
                                .addQueryParameter("range", "year")
                                .addQueryParameter("count", limit.toString())
                                .build(),
                        ).header(AUTH, "Token ${account.secret}")
                        .get()
                        .build()
                http().newCall(request).execute().use { response ->
                    if (response.code == NO_CONTENT) return@use emptyList()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    Json
                        .parseToJsonElement(response.body.string())
                        .jsonObject["payload"]
                        ?.jsonObject
                        ?.get("artists")
                        ?.jsonArray
                        ?.mapNotNull {
                            it.jsonObject["artist_name"]
                                ?.jsonPrimitive
                                ?.content
                                ?.takeIf(String::isNotBlank)
                        }.orEmpty()
                }
            }
        }

    suspend fun nowPlaying(
        entry: ScrobbleEntry,
        account: ScrobbleAccount,
    ): SendOutcome = submit(PLAYING_NOW, listOf(entry), account)

    suspend fun scrobble(
        entries: List<ScrobbleEntry>,
        account: ScrobbleAccount,
    ): SendOutcome = submit(if (entries.size == 1) SINGLE else IMPORT, entries.take(MAX_BATCH), account)

    private suspend fun submit(
        type: String,
        entries: List<ScrobbleEntry>,
        account: ScrobbleAccount,
    ): SendOutcome =
        withContext(Dispatchers.IO) {
            val payload = listensJson(type, entries).toString().toRequestBody(JSON)
            val request =
                Request
                    .Builder()
                    .url("$BASE_URL/submit-listens")
                    .header(AUTH, "Token ${account.secret}")
                    .post(payload)
                    .build()
            try {
                http().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> SendOutcome.Sent
                        response.code == UNAUTHORIZED -> SendOutcome.SignedOut
                        response.code == TOO_MANY || response.code >= SERVER_ERROR -> SendOutcome.Retry
                        else -> SendOutcome.Sent
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "submit failed: ${e.message}")
                SendOutcome.Retry
            }
        }

    private fun JsonObject.message(): String? = this["message"]?.jsonPrimitive?.content

    companion object {
        const val MAX_BATCH = 100
        private const val BASE_URL = "https://api.listenbrainz.org/1"
        private const val AUTH = "Authorization"
        private const val PLAYING_NOW = "playing_now"
        private const val SINGLE = "single"
        private const val IMPORT = "import"
        private const val NO_CONTENT = 204
        private const val UNAUTHORIZED = 401
        private const val TOO_MANY = 429
        private const val SERVER_ERROR = 500
        private const val TAG = "ListenBrainz"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** The submit-listens body; a now-playing listen carries no timestamp. */
internal fun listensJson(
    type: String,
    entries: List<ScrobbleEntry>,
): JsonObject =
    buildJsonObject {
        put("listen_type", type)
        put(
            "payload",
            buildJsonArray {
                entries.forEach { entry ->
                    add(
                        buildJsonObject {
                            if (type != "playing_now") put("listened_at", entry.timestampSec)
                            put(
                                "track_metadata",
                                buildJsonObject {
                                    put("artist_name", entry.artist)
                                    put("track_name", entry.title)
                                    if (entry.album.isNotBlank()) put("release_name", entry.album)
                                    put(
                                        "additional_info",
                                        buildJsonObject {
                                            if (entry.durationSec > 0) put("duration_ms", entry.durationSec * 1000L)
                                            put("submission_client", "Flow")
                                            put("submission_client_version", BuildConfig.VERSION_NAME)
                                            if (entry.fromYouTube) put("music_service", "music.youtube.com")
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            },
        )
    }
