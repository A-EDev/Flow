//==================================================================================================
//This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
//==================================================================================================

package io.github.aedev.flow.data.lyrics.kugou

import android.util.Log
import com.google.gson.Gson
import io.github.aedev.flow.data.lyrics.kugou.models.DownloadLyricsResponse
import io.github.aedev.flow.data.lyrics.kugou.models.Keyword
import io.github.aedev.flow.data.lyrics.kugou.models.SearchLyricsResponse
import io.github.aedev.flow.data.lyrics.kugou.models.SearchSongResponse
import io.github.aedev.flow.network.AppProxyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

object KuGou {
    private const val TAG = "KuGou"
    private const val PAGE_SIZE = 8
    private const val HEAD_CUT_LIMIT = 30
    private const val DURATION_TOLERANCE = 8

    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): Result<String> =
        runCatching {
            val keyword = generateKeyword(title, artist, album)
            val candidate = getLyricsCandidate(keyword, duration)
                ?: throw IllegalStateException("No lyrics candidate")
            val downloaded = downloadLyrics(candidate.id, candidate.accesskey)
            val decoded = android.util.Base64.decode(downloaded.content, android.util.Base64.DEFAULT)
                .decodeToString()
            decoded.normalize()
        }

    private fun getLyricsCandidate(
        keyword: Keyword, duration: Int
    ): SearchLyricsResponse.Candidate? {
        val songs = searchSongs(keyword)
        for (song in songs.data.info) {
            if (duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE) {
                val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
                if (candidate != null) return candidate
            }
        }
        return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
    }

    private fun searchSongs(keyword: Keyword): SearchSongResponse {
        val searchQuery = buildString {
            append(keyword.title)
            append(" - ")
            append(keyword.artist)
            if (!keyword.album.isNullOrBlank()) {
                append(" ")
                append(keyword.album)
            }
        }
        val url = buildString {
            append("https://mobileservice.kugou.com/api/v3/search/song")
            append("?version=9108&plat=0&pagesize=$PAGE_SIZE&showtype=0")
            append("&keyword=${URLEncoder.encode(searchQuery, "UTF-8")}")
        }
        return executeGet(url, SearchSongResponse::class.java) ?: SearchSongResponse()
    }

    private fun searchLyricsByKeyword(keyword: Keyword, duration: Int): SearchLyricsResponse {
        val searchQuery = buildString {
            append(keyword.title)
            append(" - ")
            append(keyword.artist)
            if (!keyword.album.isNullOrBlank()) {
                append(" ")
                append(keyword.album)
            }
        }
        val url = buildString {
            append("https://lyrics.kugou.com/search")
            append("?ver=1&man=yes&client=pc")
            if (duration != -1) append("&duration=${duration * 1000}")
            append("&keyword=${URLEncoder.encode(searchQuery, "UTF-8")}")
        }
        return executeGet(url, SearchLyricsResponse::class.java) ?: SearchLyricsResponse()
    }

    private fun searchLyricsByHash(hash: String): SearchLyricsResponse {
        val url = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"
        return executeGet(url, SearchLyricsResponse::class.java) ?: SearchLyricsResponse()
    }

    private fun downloadLyrics(id: Long, accessKey: String): DownloadLyricsResponse {
        val url = "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$accessKey"
        return executeGet(url, DownloadLyricsResponse::class.java) ?: DownloadLyricsResponse()
    }

    private fun <T> executeGet(url: String, clazz: Class<T>): T? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    return null
                }
                val body = response.body?.string() ?: return null
                val trimmed = body.trimStart()
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                    Log.w(TAG, "Non-JSON response (${trimmed.take(80)}...) for $url")
                    return null
                }
                gson.fromJson(body, clazz)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            null
        }
    }

    private fun normalizeTitle(title: String) =
        title.replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "").replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "").replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "").replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) =
        artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")

    fun generateKeyword(title: String, artist: String, album: String? = null) =
        Keyword(normalizeTitle(title), normalizeArtist(artist), album)

    private fun String.normalize(): String =
        lines().filter { line -> line.matches(ACCEPTED_REGEX) }
            .let { lines ->
                var headCutLine = 0
                for (i in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[i].matches(BANNED_REGEX)) {
                        headCutLine = i + 1
                        break
                    }
                }
                val filteredLines = lines.drop(headCutLine)

                var tailCutLine = 0
                for (i in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) {
                        tailCutLine = i + 1
                        break
                    }
                }
                filteredLines.dropLast(tailCutLine).joinToString("\n")
            }

    @Suppress("RegExpRedundantEscape")
    private val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
    private val BANNED_REGEX = ".+].+[:：].+".toRegex()
}
