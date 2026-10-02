package io.github.aedev.flow.player.musicvideo

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.video.MusicVideoVersions
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.utils.MusicPlayerUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The music queue as the Song/Video switch sees it. */
internal interface MusicVideoQueue {
    /** The playing entry's index and the queue it indexes. */
    val entries: Flow<Pair<Int, List<MusicTrack>>>

    fun showsVideo(videoId: String): Boolean

    fun replace(
        index: Int,
        expectedId: String,
        track: MusicTrack,
        showsVideo: Boolean,
    ): Boolean
}

private object PlayerManagerQueue : MusicVideoQueue {
    override val entries =
        combine(EnhancedMusicPlayerManager.currentQueueIndex, EnhancedMusicPlayerManager.queue) { index, queue -> index to queue }

    override fun showsVideo(videoId: String) = EnhancedMusicPlayerManager.showsVideo(videoId)

    override fun replace(
        index: Int,
        expectedId: String,
        track: MusicTrack,
        showsVideo: Boolean,
    ) = EnhancedMusicPlayerManager.replaceQueueTrack(index, expectedId, track, showsVideo)
}

/**
 * The music player's Song/Video switch. In Video mode the playing entry and the one after it play
 * as videos: a track that is a video shows its picture, and a song is swapped for its official
 * video, which keeps the song's title and art. Song mode swaps them back. A swap keeps the place in
 * the song, and listening stats, scrobbles and the radio keep counting the song, never its video.
 */
@Singleton
class MusicVideoSwitch internal constructor(
    private val versions: MusicVideoVersions,
    private val queue: MusicVideoQueue,
    private val prepareVideo: suspend (String) -> Boolean,
    private val warn: (Int) -> Unit,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        versions: MusicVideoVersions,
    ) : this(
        versions = versions,
        queue = PlayerManagerQueue,
        prepareVideo = { videoId ->
            val formats =
                MusicPlayerUtils
                    .playerResponseForPlayback(videoId)
                    .getOrNull()
                    ?.videoFormats
                    .orEmpty()
            MusicVideoFormats.pick(formats, Int.MAX_VALUE) != null
        },
        warn = { message -> EnhancedMusicPlayerManager.showPlaybackWarning(context.getString(message)) },
    )

    private data class Settle(
        val wantsVideo: Boolean,
        val index: Int,
        val current: MusicTrack?,
        val next: MusicTrack?,
    )

    private val wanted = MutableStateFlow(false)
    val videoMode: StateFlow<Boolean> = wanted.asStateFlow()

    private val looking = MutableStateFlow(false)

    /** Whether the playing entry's video is being looked up. */
    val isLoading: StateFlow<Boolean> = looking.asStateFlow()

    private val songs = ConcurrentHashMap<String, MusicTrack>()

    @Volatile private var swapTarget: String? = null

    @Volatile private var askedForVideo = false

    fun select(video: Boolean) {
        askedForVideo = video
        wanted.value = video
    }

    /** The song a swapped-in video stands for. */
    fun songFor(mediaId: String): MusicTrack? = songs[mediaId]

    /** The id a listen of [mediaId] counts toward: its song's when it is a swapped-in video. */
    fun listenId(mediaId: String): String = songs[mediaId]?.videoId ?: mediaId

    /** Whether the player moving to [mediaId] is this switch's own swap. Every transition clears it. */
    fun consumeSwap(mediaId: String?): Boolean {
        val swapped = mediaId != null && mediaId == swapTarget
        swapTarget = null
        return swapped
    }

    /** Keeps the queue in step with the switch for as long as [scope] lives. */
    fun attach(
        scope: CoroutineScope,
        enabled: Flow<Boolean>,
    ) {
        scope.launch {
            combine(enabled, wanted, queue.entries) { on, video, (index, entries) ->
                if (!on && video) wanted.value = false
                Settle(on && video, index, entries.getOrNull(index), entries.getOrNull(index + 1))
            }.distinctUntilChanged()
                .collectLatest { settle ->
                    settle.current?.let { settleEntry(settle.index, it, settle.wantsVideo, isCurrent = true) }
                    settle.next?.let { settleEntry(settle.index + 1, it, settle.wantsVideo, isCurrent = false) }
                }
        }
    }

    private suspend fun settleEntry(
        index: Int,
        entry: MusicTrack,
        wantsVideo: Boolean,
        isCurrent: Boolean,
    ) {
        if (LocalMediaIds.isLocal(entry.videoId)) {
            if (isCurrent && wantsVideo) refuse(R.string.music_video_unavailable)
            return
        }
        val song = songs[entry.videoId]
        val showsVideo = queue.showsVideo(entry.videoId)
        val video =
            if (wantsVideo && !showsVideo) {
                findVideo(song ?: entry, isCurrent) ?: return
            } else {
                null
            }
        val change = MusicVideoPlanner.replacement(entry, showsVideo, song, video, wantsVideo) ?: return
        val stoodFor = song ?: entry
        if (change.track.videoId != stoodFor.videoId) songs[change.track.videoId] = stoodFor
        if (isCurrent) swapTarget = change.track.videoId
        val replaced = queue.replace(index, entry.videoId, change.track, change.showsVideo)
        if (!replaced && isCurrent) swapTarget = null
    }

    private suspend fun findVideo(
        song: MusicTrack,
        isCurrent: Boolean,
    ): MusicTrack? {
        if (isCurrent) looking.value = true
        val video =
            try {
                versions.videoFor(song)?.takeIf { prepareVideo(it.videoId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isCurrent) refuse(R.string.music_video_failed)
                return null
            } finally {
                if (isCurrent) looking.value = false
            }
        if (isCurrent && video == null) refuse(R.string.music_video_unavailable)
        if (isCurrent) askedForVideo = false
        return video
    }

    // Only a switch the viewer just flipped falls back to Song; later songs without a video keep
    // their artwork while the mode stays on Video.
    private fun refuse(
        @StringRes message: Int,
    ) {
        if (!askedForVideo) return
        askedForVideo = false
        wanted.value = false
        warn(message)
    }
}
