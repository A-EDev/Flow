package io.github.aedev.flow.ui.screens.notes

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
 * Fills in what a note written before notes kept it is about, once per note: from watch history,
 * saved videos and subscriptions first, and only the rest from the network, a few at a time.
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
            val missing = synchronized(attempted) { notes.filter { it.subject == null && attempted.add(it.key) } }
            if (missing.isEmpty()) return
            val saved = videoDao.getVideosByIds(missing.filter { it.kind == NoteKind.Video }.map { it.targetId }).associateBy { it.id }
            val remote =
                missing.filter { note ->
                    val subject =
                        when (note.kind) {
                            NoteKind.Video -> {
                                viewHistory.getVideoHistory(note.targetId).first()?.let {
                                    NoteSubject(it.title, it.channelName, it.channelId, it.thumbnailUrl, (it.duration / 1000L).toInt())
                                } ?: saved[note.targetId]?.let {
                                    NoteSubject(it.title, it.channelName, it.channelId, it.thumbnailUrl, it.duration)
                                }
                            }

                            NoteKind.Channel -> {
                                subscriptionRepository.getSubscription(note.targetId).first()?.let {
                                    NoteSubject(title = it.channelName, channelId = it.channelId, thumbnailUrl = it.channelThumbnail)
                                }
                            }
                        }?.takeIf { it.title.isNotBlank() }
                    subject?.let { notesRepository.saveSubject(note.kind, note.targetId, it) }
                    subject == null
                }
            remote.chunked(PARALLEL_FETCHES).forEach { batch ->
                coroutineScope {
                    batch
                        .map { note -> async(networkDispatcher) { note to withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetch(note) } } }
                        .awaitAll()
                        .forEach { (note, subject) -> subject?.let { notesRepository.saveSubject(note.kind, note.targetId, it) } }
                }
            }
        }

        private suspend fun fetch(note: Note): NoteSubject? =
            withContext(networkDispatcher) {
                when (note.kind) {
                    NoteKind.Video -> {
                        val details =
                            YouTube.player(note.targetId, client = YouTubeClient.ANDROID).getOrNull()?.videoDetails
                                ?: return@withContext null
                        NoteSubject(
                            title = details.title.orEmpty(),
                            channelName = details.author.orEmpty(),
                            channelId = details.channelId,
                            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(note.targetId),
                            durationSeconds = details.lengthSeconds.toIntOrNull() ?: 0,
                        ).takeIf { it.title.isNotBlank() }
                    }

                    NoteKind.Channel -> {
                        val header = YouTube.channelLanding(note.targetId).getOrNull()?.header ?: return@withContext null
                        NoteSubject(title = header.title, channelId = header.id, thumbnailUrl = header.avatarUrl)
                            .takeIf { it.title.isNotBlank() }
                    }
                }
            }

        private companion object {
            const val PARALLEL_FETCHES = 4
            const val FETCH_TIMEOUT_MS = 10_000L
        }
    }
