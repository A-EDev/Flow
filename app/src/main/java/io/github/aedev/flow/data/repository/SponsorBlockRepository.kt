package io.github.aedev.flow.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockRepository
    @Inject
    constructor() {
        private val client: OkHttpClient
            get() = AppProxyManager.applyTo(OkHttpClient.Builder()).build()
        private val gson = Gson()
        private val segmentListType = object : TypeToken<List<SponsorBlockSegment>>() {}.type

        suspend fun getSegments(videoId: String): List<SponsorBlockSegment> =
            withContext(Dispatchers.IO) {
                try {
                    val url = segmentsUrl(videoId)

                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .build()

                    val response = client.newCall(request).execute()
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val responseBody = resp.body?.string() ?: return@withContext emptyList()
                            return@withContext parseSegments(responseBody) ?: emptyList()
                        } else {
                            return@withContext emptyList()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext emptyList()
                }
            }

        /**
         * Read segments back from a stored payload. Null means "nothing stored or nothing readable",
         * which callers treat differently from an empty segment list.
         */
        fun parseSegments(json: String?): List<SponsorBlockSegment>? {
            if (json.isNullOrBlank()) return null
            return try {
                gson.fromJson(json, segmentListType)
            } catch (e: Exception) {
                null
            }
        }

        /** Serialize segments for the download store, in the shape [parseSegments] reads back. */
        fun serializeSegments(segments: List<SponsorBlockSegment>): String = gson.toJson(segments)

        /**
         * Submit a new SponsorBlock segment.
         * Uses query parameters as required by the SponsorBlock API.
         * @return true if the submission was accepted (HTTP 200), false otherwise.
         */
        suspend fun submitSegment(
            videoId: String,
            startTime: Float,
            endTime: Float,
            category: String,
            userId: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val uuid =
                        java.util.UUID
                            .randomUUID()
                            .toString()
                            .replace("-", "")
                    val duration = (endTime - startTime)
                    val submitUrl =
                        SKIP_SEGMENTS_URL
                            .toHttpUrl()
                            .newBuilder()
                            .addQueryParameter("videoID", videoId)
                            .addQueryParameter("startTime", startTime.toString())
                            .addQueryParameter("endTime", endTime.toString())
                            .addQueryParameter("category", category)
                            .addQueryParameter("userID", userId)
                            .addQueryParameter("userAgent", "FlowYouTube/1.0")
                            .addQueryParameter("UUID", uuid)
                            .addQueryParameter("duration", duration.toString())
                            .build()

                    val request =
                        Request
                            .Builder()
                            .url(submitUrl)
                            .post("".toRequestBody())
                            .build()

                    val response = client.newCall(request).execute()
                    response.use { resp -> resp.isSuccessful }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

        companion object {
            private const val SKIP_SEGMENTS_URL = "https://sponsor.ajay.app/api/skipSegments"

            /** The lookup for [videoId], asking for every category and action type Flow handles. */
            internal fun segmentsUrl(videoId: String): HttpUrl =
                SKIP_SEGMENTS_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("videoID", videoId)
                    .addQueryParameter("categories", SponsorBlockCategories.all.toJsonArray())
                    .addQueryParameter("actionTypes", SponsorBlockCategories.actionTypes.toJsonArray())
                    .build()

            private fun List<String>.toJsonArray(): String = JsonArray(map { JsonPrimitive(it) }).toString()
        }
    }
