package io.github.aedev.flow.data.repository

import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        fun getCachedSegments(videoId: String): List<SponsorBlockSegment>? = segmentCache.get(videoId)

        suspend fun fetchSegments(videoId: String): SponsorBlockFetchResult =
            withContext(Dispatchers.IO) {
                segmentCache.get(videoId)?.let {
                    return@withContext if (it.isEmpty()) SponsorBlockFetchResult.Empty else SponsorBlockFetchResult.Success(it)
                }
                try {
                    val request =
                        Request
                            .Builder()
                            .url(skipSegmentsUrl(videoId, gson))
                            .build()

                    val response = client.newCall(request).execute()
                    response.use { resp ->
                        sponsorBlockFetchOutcomeForStatus(resp.code)?.let { return@withContext it }
                        if (resp.isSuccessful) {
                            val responseBody = resp.body.string()
                            val listType = object : TypeToken<List<SponsorBlockSegment>>() {}.type
                            val segments =
                                if (responseBody.isBlank()) {
                                    emptyList()
                                } else {
                                    gson.fromJson<List<SponsorBlockSegment>>(responseBody, listType).orEmpty()
                                }
                            segmentCache.put(videoId, segments)
                            return@withContext if (segments.isEmpty()) {
                                SponsorBlockFetchResult.Empty
                            } else {
                                SponsorBlockFetchResult.Success(segments)
                            }
                        } else {
                            return@withContext SponsorBlockFetchResult.HttpFailure(resp.code)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    return@withContext SponsorBlockFetchResult.NetworkFailure(e.javaClass.simpleName)
                }
            }

        suspend fun getSegments(videoId: String): List<SponsorBlockSegment> =
            when (val result = fetchSegments(videoId)) {
                is SponsorBlockFetchResult.Success -> result.segments

                SponsorBlockFetchResult.Empty,
                is SponsorBlockFetchResult.HttpFailure,
                is SponsorBlockFetchResult.NetworkFailure,
                -> emptyList()
            }

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
                        "https://sponsor.ajay.app/api/skipSegments"
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
            private val segmentCache = LruCache<String, List<SponsorBlockSegment>>(100)

            internal fun skipSegmentsUrl(
                videoId: String,
                gson: Gson = Gson(),
            ): HttpUrl =
                "https://sponsor.ajay.app/api/skipSegments"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("videoID", videoId)
                    .addQueryParameter("categories", gson.toJson(SponsorBlockCategories.ALL))
                    .addQueryParameter("actionTypes", gson.toJson(SponsorBlockCategories.FETCH_ACTION_TYPES))
                    .build()
        }
    }

internal fun sponsorBlockFetchOutcomeForStatus(statusCode: Int): SponsorBlockFetchResult? =
    when {
        statusCode == 404 -> SponsorBlockFetchResult.Empty
        statusCode in 200..299 -> null
        else -> SponsorBlockFetchResult.HttpFailure(statusCode)
    }

sealed interface SponsorBlockFetchResult {
    data class Success(
        val segments: List<SponsorBlockSegment>,
    ) : SponsorBlockFetchResult

    data object Empty : SponsorBlockFetchResult

    data class HttpFailure(
        val statusCode: Int,
    ) : SponsorBlockFetchResult

    data class NetworkFailure(
        val reason: String,
    ) : SponsorBlockFetchResult
}
