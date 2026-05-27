//==================================================================================================
//This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
//==================================================================================================

package io.github.aedev.flow.data.lyrics.paxsenix

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import io.github.aedev.flow.data.lyrics.TTMLParser
import io.github.aedev.flow.data.lyrics.paxsenix.models.AppleMusicSearchResponse
import io.github.aedev.flow.data.lyrics.paxsenix.models.LyricsResponse
import io.github.aedev.flow.data.lyrics.paxsenix.models.SearchResult
import io.github.aedev.flow.network.AppProxyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Paxsenix {
    private const val TAG = "Paxsenix"
    private const val PAXSENIX_BASE = "https://lyrics.paxsenix.org"
    private const val APPLE_MUSIC_API_BASE = "https://amp-api.music.apple.com/v1/catalog/us"

    @Volatile
    private var initialized = false
    private var appVersion: String = "Unknown"

    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    @Volatile
    private var tokenManager: AppleTokenManager? = null

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            } catch (e: Exception) { "Unknown" }
            tokenManager = AppleTokenManager()
            initialized = true
        }
    }

    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\([^)]*\d{4}[^)]*\)""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> = runCatching {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val searchQueries = buildList {
            add("$cleanedTitle $cleanedArtist")
            add(cleanedTitle)
            if (!album.isNullOrBlank()) add("$cleanedTitle $cleanedArtist $album")
        }

        var allResults: List<Pair<SearchResult, Double>> = emptyList()

        for (query in searchQueries) {
            if (allResults.isEmpty()) {
                val searchResults = search(query)
                if (searchResults.isNotEmpty()) {
                    allResults = scoreAndFilterResults(searchResults, title, artist, duration)
                }
            }
        }

        if (allResults.isEmpty()) throw IllegalStateException("No tracks found on Paxsenix")

        var bestLyrics: String? = null
        var bestQuality = 0

        for ((result, _) in allResults.take(10)) {
            val lrc = fetchLyricsForTrack(result.id) ?: continue
            if (lrc.isEmpty()) continue

            val quality = getQuality(lrc)
            if (quality > bestQuality) {
                bestQuality = quality
                bestLyrics = lrc
            }
            if (bestQuality == 3) break
        }

        bestLyrics ?: throw IllegalStateException("No lyrics available from Paxsenix")
    }

    private suspend fun search(query: String): List<SearchResult> {
        return try {
            val tm = tokenManager ?: return emptyList()
            val token = tm.getToken() ?: return emptyList()
            val results = searchWithToken(token, query)
            if (results == null) {
                tm.clearToken()
                val newToken = tm.getToken() ?: return emptyList()
                searchWithToken(newToken, query) ?: emptyList()
            } else {
                results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
            emptyList()
        }
    }

    private fun searchWithToken(token: String, query: String): List<SearchResult>? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$APPLE_MUSIC_API_BASE/search?term=$encodedQuery&types=songs&limit=25&l=en-US&platform=web&format[resources]=map&include[songs]=artists&extend=artistUrl"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("x-apple-renewal", "true")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) {
            response.close()
            return null
        }
        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val parsed = try {
            gson.fromJson(body, AppleMusicSearchResponse::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Apple Music search response: ${e.message}")
            return emptyList()
        }

        val songs = parsed.results.songs?.data ?: return emptyList()

        return songs.mapNotNull { songData ->
            val detail = parsed.resources?.songs?.get(songData.id) ?: return@mapNotNull null
            val attr = detail.attributes
            SearchResult(
                id = songData.id,
                trackName = attr.name,
                artistName = attr.artistName,
                albumName = attr.albumName,
                duration = attr.durationInMillis?.toInt()?.div(1000),
                artwork = attr.artwork?.url?.replace("{w}", "100")?.replace("{h}", "100")?.replace("{f}", "png")
            )
        }
    }

    private fun getQuality(lrc: String): Int {
        if (lrc.isBlank()) return 0
        val hasWordTimings = (lrc.contains("<") && lrc.contains(">") && (lrc.contains("|") || lrc.contains(":"))) ||
                lrc.contains(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>"))
        if (hasWordTimings) return 3
        val hasLineTimings = lrc.contains(Regex("\\[\\d\\d:\\d\\d\\.\\d{2,3}\\]")) ||
                lrc.contains(Regex("^\\[bg:.*\\]", RegexOption.MULTILINE))
        if (hasLineTimings) return 2
        return 1
    }

    private fun scoreAndFilterResults(
        results: List<SearchResult>,
        title: String,
        artist: String,
        duration: Int
    ): List<Pair<SearchResult, Double>> {
        val durationMs = duration * 1000
        val cleanupRegex = Regex("""\s*\(.*?\)|\s*\[.*?\]""")
        val cleanedTitle = title.replace(cleanupRegex, "").lowercase().trim()
        val cleanedArtist = cleanArtist(artist).lowercase()
        val targetIsMixed = title.contains("mixed", ignoreCase = true)
        val targetIsRemix = title.contains("remix", ignoreCase = true)

        return results.map { result ->
            var score = 0.0
            val resultTitle = result.displayName
            val resultArtist = result.displayArtist

            result.duration?.let { d ->
                val diff = abs(d - durationMs)
                when {
                    diff <= 2000 -> score += 100
                    diff <= 5000 -> score += 50
                    diff <= 10000 -> score += 10
                    else -> score -= 50
                }
            }

            val resultTitleCleaned = resultTitle.replace(cleanupRegex, "").lowercase().trim()
            when {
                resultTitleCleaned == cleanedTitle -> score += 80
                resultTitleCleaned.contains(cleanedTitle) || cleanedTitle.contains(resultTitleCleaned) -> score += 40
            }

            val resultIsMixed = resultTitle.contains("mixed", ignoreCase = true)
            val resultIsRemix = resultTitle.contains("remix", ignoreCase = true)
            if (resultIsMixed && !targetIsMixed) score -= 60
            if (resultIsRemix && !targetIsRemix) score -= 40

            val resultArtistLower = resultArtist.lowercase()
            when {
                resultArtistLower.contains(cleanedArtist) -> score += 50
                else -> {
                    val artistWords = cleanedArtist.split(Regex("\\s+")).filter { it.length > 2 }
                    if (artistWords.any { resultArtistLower.contains(it) }) score += 25
                }
            }

            result to score
        }.sortedByDescending { it.second }.filter { it.second > 0 }.take(10)
    }

    private fun fetchLyricsForTrack(id: String): String? {
        return try {
            val url = "$PAXSENIX_BASE/apple-music/lyrics?id=$id"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Flow/$appVersion")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: return null
            val lyricsResponse = gson.fromJson(body, LyricsResponse::class.java)

            if (!lyricsResponse.ttmlContent.isNullOrBlank()) {
                val lrc = convertTTMLToLRC(lyricsResponse.ttmlContent)
                if (lrc.isNotEmpty()) return lrc
            }

            if (!lyricsResponse.elrcMultiPerson.isNullOrBlank()) return lyricsResponse.elrcMultiPerson
            if (!lyricsResponse.elrc.isNullOrBlank()) return lyricsResponse.elrc
            if (!lyricsResponse.plain.isNullOrBlank()) return lyricsResponse.plain

            if (lyricsResponse.content.isEmpty()) return null

            val hasWordLevel = lyricsResponse.type == "Syllable"
            if (!hasWordLevel) {
                return lyricsResponse.content
                    .map { line -> line.text.joinToString(" ") { it.text } }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            }

            buildString {
                lyricsResponse.content.forEach { line ->
                    val timeMs = line.timestamp
                    val minutes = timeMs / 1000 / 60
                    val seconds = (timeMs / 1000) % 60
                    val centiseconds = (timeMs % 1000) / 10

                    val agent = when {
                        line.background -> "{bg}"
                        line.oppositeTurn -> "{agent:v2}"
                        else -> "{agent:v1}"
                    }

                    val lineText = line.text.joinToString(" ") { it.text }

                    if (lineText.isNotBlank()) {
                        appendLine(String.format(Locale.US, "[%02d:%02d.%02d]%s%s", minutes, seconds, centiseconds, agent, lineText))

                        if (line.text.isNotEmpty()) {
                            val wordsData = line.text.joinToString("|") { word ->
                                "${word.text}:${word.timestamp.toDouble() / 1000}:${word.endtime.toDouble() / 1000}"
                            }
                            if (wordsData.isNotEmpty()) appendLine("<$wordsData>")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics for track $id: ${e.message}")
            null
        }
    }

    private fun convertTTMLToLRC(ttml: String): String {
        return try {
            val entries = TTMLParser.parseTTMLToLyricsEntries(ttml)
            if (entries.isEmpty()) return ""
            entries.joinToString("\n") { entry ->
                val minutes = entry.time / 60000
                val seconds = (entry.time / 1000) % 60
                val millis = entry.time % 1000
                val agentPrefix = when {
                    entry.isBackground -> "{bg}"
                    entry.agent != null -> "{agent:${entry.agent}}"
                    else -> ""
                }
                val wordBlock = entry.words?.joinToString("|") { w ->
                    "${w.text}:${w.startTime.toDouble() / 1000}:${w.endTime.toDouble() / 1000}"
                }
                val mainLine = "[%02d:%02d.%03d]%s%s".format(minutes, seconds, millis, agentPrefix, entry.text)
                if (wordBlock != null) "$mainLine\n<$wordBlock>" else mainLine
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTML conversion failed: ${e.message}")
            ""
        }
    }

    private class AppleTokenManager {
        @Volatile
        private var cachedToken: String? = null
        private val mutex = Mutex()

        suspend fun getToken(): String? = mutex.withLock {
            cachedToken?.let { return it }
            try {
                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val mainRequest = Request.Builder()
                    .url("https://beta.music.apple.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val mainBody = httpClient.newCall(mainRequest).execute().use { it.body?.string() ?: "" }

                val indexJsRegex = Regex("""/assets/index~[^/]+\.js""")
                val indexJsMatch = indexJsRegex.find(mainBody)
                    ?: throw Exception("Could not find index JS URL")

                val jsRequest = Request.Builder()
                    .url("https://beta.music.apple.com${indexJsMatch.value}")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val jsBody = httpClient.newCall(jsRequest).execute().use { it.body?.string() ?: "" }

                val tokenRegex = Regex("""eyJh([^"]*)""")
                val tokenMatch = tokenRegex.find(jsBody)
                    ?: throw Exception("Could not find token")

                val token = tokenMatch.value
                cachedToken = token
                token
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Apple Music token: ${e.message}")
                null
            }
        }

        fun clearToken() {
            cachedToken = null
        }
    }
}
