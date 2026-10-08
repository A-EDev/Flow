package io.github.aedev.flow.data.local

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

private const val ENGLISH_TAKEOUT_WATCH_HISTORY = "history/watch-history.html"
private const val NEURO_CANDIDATE_LIMIT = 800
private const val AVATAR_FETCHES = 5
private const val SUBSCRIPTION_BATCH = 25

/**
 * The all-in-one Google Takeout import: reads an export in a single pass and saves what it found.
 * Files are recognised by their contents, not their names, which Takeout translates into the
 * account's language.
 */
internal class YouTubeTakeoutImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val viewHistory: ViewHistory,
    private val subscriptions: SubscriptionRepository,
    private val saveLikes: suspend (List<TakeoutLike>) -> Int,
    private val channelAvatar: suspend (String) -> String,
    private val learnFromHistory: suspend (Collection<VideoHistoryEntry>) -> Unit,
) {
    private class Found {
        val subscriptions = mutableListOf<YouTubeTakeoutSubscription>()
        val playlistTitlesByDirectory = mutableMapOf<String, MutableList<String>>()
        val playlistVideos = mutableMapOf<String, List<String>>()
        var likes: List<TakeoutLike>? = null
        var watches = 0
        val learnable = LinkedHashMap<String, VideoHistoryEntry>()
    }

    suspend fun import(
        uri: Uri,
        onProgress: ((label: String, current: Int, total: Int) -> Unit)? = null,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val found = Found()
                context.contentResolver.openInputStream(uri)?.use { raw -> readArchive(raw, found, YouTubeTakeoutCsvBudget(), onProgress) }
                    ?: return@withContext Result.failure(Exception("Could not open file"))

                val subscriptionsImported = saveSubscriptions(found.subscriptions, onProgress)
                val (playlistsImported, playlistVideosImported) = savePlaylists(found)
                val likesImported = found.likes?.let { saveLikes(it) } ?: 0

                if (subscriptionsImported == 0 && found.watches == 0 && playlistsImported == 0 && likesImported == 0) {
                    return@withContext Result.failure(Exception("no_content"))
                }
                if (found.watches > 0) runCatching { learnFromHistory(found.learnable.values) }

                Result.success(
                    buildList {
                        if (subscriptionsImported > 0) add("$subscriptionsImported subscriptions")
                        if (found.watches > 0) add("${found.watches} history entries")
                        if (playlistsImported > 0) add("$playlistsImported playlists ($playlistVideosImported videos)")
                        if (likesImported > 0) add(context.getString(R.string.import_takeout_part_likes, likesImported))
                    }.joinToString(", "),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** A Takeout watch-history HTML file on its own, as the single-file history import takes it; returns how many were saved. */
    suspend fun importHtmlHistory(input: InputStream): Int {
        val found = Found()
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            readTakeoutHtmlActivity(reader, System.currentTimeMillis(), requireActivityMarkup = false) { saveWatches(it, found) }
        }
        if (found.watches > 0) runCatching { learnFromHistory(found.learnable.values) }
        return found.watches
    }

    private suspend fun readArchive(
        raw: InputStream,
        found: Found,
        budget: YouTubeTakeoutCsvBudget,
        onProgress: ((String, Int, Int) -> Unit)?,
    ) {
        ZipInputStream(raw.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val english = name.endsWith(ENGLISH_TAKEOUT_WATCH_HISTORY, ignoreCase = true)
                when {
                    english || isYouTubeTakeoutHtmlEntry(name) -> {
                        onProgress?.invoke("Watch history", 0, 0)
                        // Each file is stamped below the last, so two history files never share a time.
                        val start = System.currentTimeMillis() - found.watches
                        readTakeoutHtmlActivity(zip.bufferedReader(Charsets.UTF_8), start, requireActivityMarkup = !english) {
                            saveWatches(it, found)
                        }
                    }

                    !entry.isDirectory && found.likes == null && isMyActivityYouTubeEntry(name) -> {
                        onProgress?.invoke(context.getString(R.string.import_label_youtube_likes), 0, 0)
                        found.likes = readMyActivityLikes(zip).likes
                    }

                    !entry.isDirectory && isYouTubeTakeoutCsvEntry(name) -> {
                        budget.startEntry()
                        when (val content = readYouTubeTakeoutCsv(zip.bufferedReader(Charsets.UTF_8), budget)) {
                            is YouTubeTakeoutCsvContent.Subscriptions -> {
                                onProgress?.invoke("Subscriptions", 0, 0)
                                found.subscriptions += content.rows
                            }

                            is YouTubeTakeoutCsvContent.PlaylistVideos -> {
                                found.playlistVideos[name] = content.videoIds
                            }

                            is YouTubeTakeoutCsvContent.PlaylistMetadata -> {
                                found.playlistTitlesByDirectory.getOrPut(name.takeoutParentPath()) { mutableListOf() } += content.titles
                            }

                            YouTubeTakeoutCsvContent.Unsupported -> {
                                Unit
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private suspend fun saveWatches(
        watches: List<TakeoutWatch>,
        found: Found,
    ) {
        if (watches.isEmpty()) return
        val entries = watches.map { it.toHistoryEntry() }
        viewHistory.bulkSaveHistoryEntries(entries)
        entries.forEach { entry ->
            if (found.learnable.size < NEURO_CANDIDATE_LIMIT && entry.title.isNotBlank()) found.learnable.putIfAbsent(entry.videoId, entry)
        }
        found.watches += entries.size
        yield()
    }

    private suspend fun saveSubscriptions(
        rows: List<YouTubeTakeoutSubscription>,
        onProgress: ((String, Int, Int) -> Unit)?,
    ): Int {
        if (rows.isEmpty()) return 0
        onProgress?.invoke("Subscriptions", 0, rows.size)
        val semaphore = Semaphore(AVATAR_FETCHES)
        val completed = AtomicInteger(0)
        val imported = mutableListOf<ChannelSubscription>()
        supervisorScope {
            rows.chunked(SUBSCRIPTION_BATCH).forEach { batch ->
                imported +=
                    batch
                        .map { sub ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    val avatar = runCatching { channelAvatar(sub.channelId) }.getOrDefault("")
                                    onProgress?.invoke("Subscriptions", completed.incrementAndGet(), rows.size)
                                    ChannelSubscription(
                                        channelId = sub.channelId,
                                        channelName = sub.channelName,
                                        channelThumbnail = avatar,
                                        subscribedAt = System.currentTimeMillis(),
                                    )
                                }
                            }
                        }.awaitAll()
            }
        }
        subscriptions.subscribeAll(imported)
        val names = rows.map { it.channelName }.filter { it.isNotEmpty() }
        if (names.isNotEmpty()) runCatching { FlowNeuroEngine.bootstrapFromSubscriptions(context, names) }
        return imported.size
    }

    /** Returns how many playlists were saved and how many videos they hold. */
    private suspend fun savePlaylists(found: Found): Pair<Int, Int> {
        validateYouTubeTakeoutPlaylistCount(
            videoFileCount = found.playlistVideos.size,
            metadataTitleCount = found.playlistTitlesByDirectory.values.sumOf { it.size },
        )
        val fallbackName = context.getString(R.string.imported_playlist_fallback)
        var playlists = 0
        var videos = 0
        found.playlistVideos.keys
            .groupBy { it.takeoutParentPath() }
            .forEach { (directory, files) ->
                val titles = found.playlistTitlesByDirectory[directory].orEmpty()
                resolveYouTubeTakeoutPlaylistNames(files, titles, fallbackName).forEach { (file, name) ->
                    val videoIds = found.playlistVideos.getValue(file)
                    savePlaylist(name, videoIds)
                    playlists++
                    videos += videoIds.size
                }
            }
        return playlists to videos
    }

    private suspend fun savePlaylist(
        name: String,
        videoIds: List<String>,
    ) {
        val isWatchLater = name.equals("watch later", ignoreCase = true)
        val entity =
            PlaylistEntity(
                id = if (isWatchLater) PlaylistRepository.WATCH_LATER_ID else "yt_takeout_${UUID.randomUUID()}",
                name = if (isWatchLater) "Watch Later" else name,
                description = context.getString(R.string.imported_from_google_takeout),
                thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first()),
                isPrivate = isWatchLater,
                createdAt = System.currentTimeMillis(),
                isMusic = false,
                isUserCreated = true,
            )
        fillPlaylist(entity, videoIds.map { videoEntity(it, title = "", artists = "", isMusic = false) })
    }

    /** Creates [playlist] if it is new, then appends the videos it does not hold yet after its last one. */
    private suspend fun fillPlaylist(
        playlist: PlaylistEntity,
        videos: List<VideoEntity>,
    ) {
        if (videos.isEmpty()) return
        database.withTransaction {
            val dao = database.playlistDao()
            if (dao.getPlaylist(playlist.id) == null) dao.insertPlaylist(playlist)
            val held = dao.getVideoIdsInPlaylist(playlist.id).toHashSet()
            var position = (dao.getMaxPlaylistPosition(playlist.id) ?: -1L) + 1L
            videos.forEach { video ->
                database.videoDao().insertVideoOrIgnore(video)
                if (!held.add(video.id)) return@forEach
                dao.insertPlaylistVideoCrossRef(PlaylistVideoCrossRef(playlistId = playlist.id, videoId = video.id, position = position++))
            }
        }
    }
}

private fun TakeoutWatch.toHistoryEntry() =
    VideoHistoryEntry(
        videoId = videoId,
        position = 0L,
        duration = 0L,
        timestamp = watchedAt,
        title = title,
        thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
        channelName = channelName,
        channelId = channelId,
        isMusic = isMusic,
    )

private fun videoEntity(
    videoId: String,
    title: String,
    artists: String,
    isMusic: Boolean,
) = VideoEntity(
    id = videoId,
    title = title,
    channelName = artists,
    channelId = "",
    thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
    duration = 0,
    viewCount = 0L,
    uploadDate = "",
    description = "",
    channelThumbnailUrl = "",
    isMusic = isMusic,
)
