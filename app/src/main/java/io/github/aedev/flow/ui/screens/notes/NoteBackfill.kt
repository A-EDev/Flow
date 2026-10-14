package io.github.aedev.flow.ui.screens.notes

import android.util.Log
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NoteSubject
import io.github.aedev.flow.data.notes.NotesRepository
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Fills in what a note is about when it was written before notes kept it, or before they kept the
 * channel's avatar, handle and subscriber count: from watch history, saved videos and subscriptions
 * first, and only the rest from the network, a few at a time, once per note.
 */
internal class NoteBackfill
    @Inject
    constructor(
        private val notesRepository: NotesRepository,
        private val viewHistory: ViewHistory,
        private val videoDao: VideoDao,
        private val subscriptionRepository: SubscriptionRepository,
        @NetworkIoDispatcher private val networkDispatcher: CoroutineDispatcher,
    ) {
        private val attempted = mutableSetOf<String>()

        suspend fun fill(notes: List<Note>) {
            val incomplete = synchronized(attempted) { notes.filter { it.lacksDetails() && attempted.add(it.key) } }
            if (incomplete.isEmpty()) return
            val saved = videoDao.getVideosByIds(incomplete.filter { it.kind == NoteKind.Video }.map { it.targetId }).associateBy { it.id }
            val remote =
                incomplete.filter { note ->
                    val local = localSubject(note, saved[note.targetId])
                    local?.let { notesRepository.saveSubject(note.kind, note.targetId, it) }
                    local == null || local.lacksDetails(note.kind)
                }
            val channelPages = mutableMapOf<String, ChannelHeader?>()
            remote.chunked(PARALLEL_FETCHES).forEach { batch ->
                coroutineScope {
                    batch
                        .map { note ->
                            async(networkDispatcher) { note to fetch(note, channelPages) }
                        }.awaitAll()
                        .forEach { (note, subject) -> subject?.let { notesRepository.saveSubject(note.kind, note.targetId, it) } }
                }
            }
        }

        private suspend fun localSubject(
            note: Note,
            saved: io.github.aedev.flow.data.local.entity.VideoEntity?,
        ): NoteSubject? {
            val known = note.subject
            return when (note.kind) {
                NoteKind.Video -> {
                    val base =
                        known
                            ?: viewHistory.getVideoHistory(note.targetId).first()?.let {
                                NoteSubject(it.title, it.channelName, it.channelId, it.thumbnailUrl, (it.duration / 1000L).toInt())
                            }
                            ?: saved?.let { NoteSubject(it.title, it.channelName, it.channelId, it.thumbnailUrl, it.duration) }
                            ?: return null
                    val avatar =
                        base.channelAvatarUrl.ifBlank {
                            saved?.channelThumbnailUrl?.takeIf { it.isNotBlank() }
                                ?: base.channelId.takeIf { it.isNotBlank() }?.let {
                                    subscriptionRepository
                                        .getSubscription(
                                            it,
                                        ).first()
                                        ?.channelThumbnail
                                }
                                ?: ""
                        }
                    base.copy(channelAvatarUrl = avatar).takeIf { it.title.isNotBlank() }
                }

                NoteKind.Channel -> {
                    known ?: subscriptionRepository.getSubscription(note.targetId).first()?.let {
                        NoteSubject(
                            title = it.channelName,
                            channelId = it.channelId,
                            thumbnailUrl = it.channelThumbnail,
                            channelAvatarUrl = it.channelThumbnail,
                        )
                    }
                }
            }
        }

        private suspend fun fetch(
            note: Note,
            channelPages: MutableMap<String, ChannelHeader?>,
        ): NoteSubject? =
            withContext(networkDispatcher) {
                when (note.kind) {
                    NoteKind.Video -> {
                        val known =
                            note.subject?.takeIf { it.title.isNotBlank() } ?: run {
                                val details =
                                    withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                                        (
                                            YouTube.player(note.targetId, client = YouTubeClient.ANDROID).getOrNull()
                                                ?: YouTube.player(note.targetId, client = YouTubeClient.MOBILE).getOrNull()
                                        )?.videoDetails
                                    } ?: run {
                                        Log.w(TAG, "No details found for ${note.targetId}; tried again on the next visit")
                                        return@withContext null
                                    }
                                NoteSubject(
                                    title = details.title.orEmpty(),
                                    channelName = details.author.orEmpty(),
                                    channelId = details.channelId,
                                    thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(note.targetId),
                                    durationSeconds = details.lengthSeconds.toIntOrNull() ?: 0,
                                )
                            }
                        val avatar = known.channelId.takeIf { it.isNotBlank() }?.let { channelHeader(it, channelPages)?.avatarUrl }
                        known.copy(channelAvatarUrl = avatar ?: known.channelAvatarUrl).takeIf { it.title.isNotBlank() }
                    }

                    NoteKind.Channel -> {
                        val header = channelHeader(note.targetId, channelPages) ?: return@withContext null
                        NoteSubject(
                            title = header.title,
                            channelId = header.id,
                            thumbnailUrl = header.avatarUrl,
                            channelAvatarUrl = header.avatarUrl,
                            channelHandle = header.handle.orEmpty(),
                            subscriberCountText = header.subscriberCountText.orEmpty(),
                        ).takeIf { it.title.isNotBlank() }
                    }
                }
            }

        /** One channel browse per channel per pass, however many notes name it. */
        private suspend fun channelHeader(
            channelId: String,
            pages: MutableMap<String, ChannelHeader?>,
        ): ChannelHeader? {
            synchronized(pages) { if (channelId in pages) return pages[channelId] }
            // Its own timeout: a slow channel page must not cost the video details found before it.
            val header = withTimeoutOrNull(FETCH_TIMEOUT_MS) { YouTube.channelLanding(channelId).getOrNull()?.header }
            synchronized(pages) { pages[channelId] = header }
            return header
        }

        private companion object {
            const val TAG = "NoteBackfill"
            const val PARALLEL_FETCHES = 4
            const val FETCH_TIMEOUT_MS = 10_000L
        }
    }

private fun Note.lacksDetails(): Boolean = subject?.lacksDetails(kind) ?: true

private fun NoteSubject.lacksDetails(kind: NoteKind): Boolean =
    title.isBlank() ||
        when (kind) {
            NoteKind.Video -> channelAvatarUrl.isBlank()
            NoteKind.Channel -> subscriberCountText.isBlank()
        }
