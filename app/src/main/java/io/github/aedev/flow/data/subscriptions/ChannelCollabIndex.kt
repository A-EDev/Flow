package io.github.aedev.flow.data.subscriptions

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelPage
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the collaborations a followed channel took part in but did not upload (#840).
 *
 * A collaboration lists only on its uploader's RSS feed and Videos tab. The one place a collaborator
 * shows it is the "Collaborations" shelf on its Home tab, a heavy browse, so channels are looked at
 * a few per refresh: each at most once a day, and once a week when its Home tab has no
 * collaborations at all.
 */
@Singleton
class ChannelCollabIndex internal constructor(
    private val landing: suspend (channelId: String) -> Result<ChannelPage>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor() : this(landing = { YouTube.channelLanding(it) })

    private val nextLookAt = ConcurrentHashMap<String, Long>()

    /**
     * Looks at the followed channels most overdue, and returns each one it could read with the
     * collaborations it took part in that no followed channel uploaded.
     */
    suspend fun scan(followed: List<String>): Map<String, List<Video>> {
        val followedIds = followed.toHashSet()
        val due = dueChannels(followed, nextLookAt, clock(), MAX_CHANNELS_PER_PASS)
        if (due.isEmpty()) return emptyMap()
        val found = LinkedHashMap<String, List<Video>>()
        due.chunked(PARALLEL_LOOKS).forEach { batch ->
            coroutineScope {
                batch
                    .map { channelId -> async { channelId to look(channelId, followedIds) } }
                    .awaitAll()
                    .forEach { (channelId, collabs) -> if (collabs != null) found[channelId] = collabs }
            }
        }
        return found
    }

    private suspend fun look(
        channelId: String,
        followed: Set<String>,
    ): List<Video>? =
        try {
            val page = withTimeoutOrNull(LOOK_TIMEOUT_MS) { landing(channelId).getOrNull() }
            if (page == null) {
                null
            } else {
                val collabs = page.collaborationsOf(channelId, followed)
                val wait = if (page.hasCollaborations(channelId)) LOOK_INTERVAL_MS else QUIET_LOOK_INTERVAL_MS
                nextLookAt[channelId] = clock() + wait
                collabs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[$channelId] collaboration look failed: ${e.message}")
            null
        }

    private companion object {
        const val TAG = "ChannelCollabIndex"
        const val MAX_CHANNELS_PER_PASS = 12
        const val PARALLEL_LOOKS = 2
        const val LOOK_TIMEOUT_MS = 15_000L
        const val LOOK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val QUIET_LOOK_INTERVAL_MS = 7L * LOOK_INTERVAL_MS
    }
}

/** The channels due a look, the never-seen first and then the longest-waiting, at most [max]. */
internal fun dueChannels(
    followed: List<String>,
    nextLookAt: Map<String, Long>,
    now: Long,
    max: Int,
): List<String> =
    followed
        .distinct()
        .filter { (nextLookAt[it] ?: 0L) <= now }
        .sortedBy { nextLookAt[it] ?: 0L }
        .take(max)

/**
 * The Home tab's videos that name [channelId] among their collaborators but were uploaded by a
 * channel the viewer does not follow; one the viewer follows already reaches the feed through its
 * own uploads, with an exact date. Found by shape, never by the shelf's translated title.
 */
internal fun ChannelPage.collaborationsOf(
    channelId: String,
    followed: Set<String>,
): List<Video> =
    homeVideos()
        .filter { video ->
            video.channelId.isNotBlank() &&
                video.channelId != channelId &&
                video.channelId !in followed &&
                video.collaborators.any { it.channelId == channelId }
        }.distinctBy { it.id }

private fun ChannelPage.hasCollaborations(channelId: String): Boolean =
    homeVideos().any { video -> video.channelId != channelId && video.collaborators.any { it.channelId == channelId } }

private fun ChannelPage.homeVideos(): List<Video> =
    initialTab
        ?.sections
        .orEmpty()
        .flatMap { it.items }
        .mapNotNull { (it as? FeedItem.VideoItem)?.video }
