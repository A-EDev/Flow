package io.github.aedev.flow.data.lyrics

import android.util.Log
import com.google.gson.Gson
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * LyricsPlus/Binimum provider, adapted from Metrolist.
 *
 * This source often has fuller word-level lyrics than LRCLib and can fill gaps
 * that BetterLyrics/SimpMusic miss.
 */
class LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"

    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus-seven.vercel.app"
    )

    @Volatile
    private var lastWorkingServer: String? = null

    private data class LyricsPlusResponse(
        val type: String? = null,
        val lyrics: List<LyricLine>? = null
    )

    private data class LyricLine(
        val time: Long = 0,
        val duration: Long = 0,
        val text: String = "",
        val syllabus: List<LyricWord>? = null,
        val element: LineElement? = null
    )

    private data class LineElement(
        val singer: String? = null
    )

    private data class LyricWord(
        val time: Long = 0,
        val duration: Long = 0,
        val text: String = "",
        val isBackground: Boolean = false
    )

    private data class BinimumLyricsApiResponse(
        val results: List<BinimumLyricsResult> = emptyList()
    )

    private data class BinimumLyricsResult(
        val timing_type: String? = null,
        val lyricsUrl: String? = null
    )

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): Result<List<LyricsEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            fetchBinimumLyrics(title, artist, duration, album)
                ?.takeIf { entries -> entries.any { !it.words.isNullOrEmpty() } }
                ?: fetchLyricsPlus(title, artist, duration, album)
                ?: throw IllegalStateException("LyricsPlus lyrics unavailable")
        }
    }

    private fun prioritizedServers(): List<String> {
        val last = lastWorkingServer
        return if (last != null && last in baseUrls) {
            listOf(last) + baseUrls.filter { it != last }
        } else {
            baseUrls
        }
    }

    private fun fetchLyricsPlus(
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): List<LyricsEntry>? {
        if (title.isBlank() || artist.isBlank()) return null

        for (baseUrl in prioritizedServers()) {
            try {
                val url = buildString {
                    append(baseUrl.trimEnd('/'))
                    append("/v2/lyrics/get")
                    append("?title=${title.urlEncoded()}")
                    append("&artist=${artist.urlEncoded()}")
                    if (duration > 0) append("&duration=$duration")
                    if (!album.isNullOrBlank()) append("&album=${album.urlEncoded()}")
                }
                val body = executeGet(url) ?: continue
                val response = gson.fromJson(body, LyricsPlusResponse::class.java)
                val entries = convertLyricsPlus(response)
                if (!entries.isNullOrEmpty()) {
                    lastWorkingServer = baseUrl
                    return entries
                }
            } catch (e: Exception) {
                Log.d(TAG, "LyricsPlus failed from $baseUrl: ${e.message}")
            }
        }

        return null
    }

    private fun fetchBinimumLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): List<LyricsEntry>? {
        if (title.isBlank() || artist.isBlank()) return null

        return try {
            val url = buildString {
                append(BINIMUM_API_BASE_URL)
                append("?track=${title.urlEncoded()}")
                append("&artist=${artist.urlEncoded()}")
                if (duration > 0) append("&duration=$duration")
                if (!album.isNullOrBlank()) append("&album=${album.urlEncoded()}")
            }
            val body = executeGet(url) ?: return null
            val response = gson.fromJson(body, BinimumLyricsApiResponse::class.java)
            val selected = response.results.firstOrNull { !it.lyricsUrl.isNullOrBlank() } ?: return null
            val ttml = executeGet(selected.lyricsUrl!!) ?: return null
            TTMLParser.parseTTMLToLyricsEntries(ttml).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.d(TAG, "Binimum lyrics failed: ${e.message}")
            null
        }
    }

    private fun convertLyricsPlus(response: LyricsPlusResponse?): List<LyricsEntry>? {
        val lines = response?.lyrics?.takeIf { it.isNotEmpty() } ?: return null
        val isWordSync = response.type.equals("Word", ignoreCase = true)
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val allWords = line.syllabus.orEmpty()
            val mainWords = allWords.filter { !it.isBackground }
            val bgWords = allWords.filter { it.isBackground }

            val mainText = when {
                isWordSync && mainWords.isNotEmpty() -> buildText(mainWords)
                mainWords.isEmpty() && bgWords.isNotEmpty() -> ""
                else -> line.text.trim()
            }

            if (mainText.isNotBlank()) {
                result += LyricsEntry(
                    time = line.time,
                    text = mainText,
                    words = if (isWordSync) mainWords.toWordTimestamps() else null,
                    agent = line.element?.singer,
                    isBackground = false
                )
            }

            if (bgWords.isNotEmpty()) {
                val bgText = buildText(bgWords)
                if (bgText.isNotBlank()) {
                    result += LyricsEntry(
                        time = bgWords.minOf { it.time },
                        text = bgText,
                        words = if (isWordSync) bgWords.toWordTimestamps() else null,
                        agent = "bg",
                        isBackground = true
                    )
                }
            }
        }

        return result.sorted().takeIf { it.isNotEmpty() }
    }

    private fun List<LyricWord>.toWordTimestamps(): List<WordTimestamp>? {
        return filter { it.text.isNotBlank() }
            .map {
                WordTimestamp(
                    text = it.text.trim(),
                    startTime = it.time,
                    endTime = it.time + it.duration.coerceAtLeast(1)
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    private fun buildText(words: List<LyricWord>): String {
        return words.joinToString("") { it.text }.trim()
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val TAG = "LyricsPlusProvider"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val BINIMUM_API_BASE_URL = "https://lyrics-api.binimum.org/"
    }
}
