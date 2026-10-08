package io.github.aedev.flow.data.notes

import io.github.aedev.flow.data.local.dao.NoteDao
import io.github.aedev.flow.data.local.entity.NoteEntity
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class NoteKind {
    Channel,
    Video,
    ;

    fun idFor(targetId: String): String = "${name.lowercase()}:$targetId"
}

/**
 * What a note is about: a video and its channel, or a channel alone (then [channelName] is blank and
 * [thumbnailUrl] is its avatar).
 */
data class NoteSubject(
    val title: String,
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Int = 0,
    val channelAvatarUrl: String = "",
    val channelHandle: String = "",
    val subscriberCountText: String = "",
)

fun Video.toNoteSubject(): NoteSubject =
    NoteSubject(
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = duration,
        channelAvatarUrl = channelThumbnailUrl,
    )

data class Note(
    val targetId: String,
    val kind: NoteKind,
    val text: String,
    val updatedAt: Long,
    val subject: NoteSubject? = null,
    val position: Int? = null,
)

@Singleton
class NotesRepository
    @Inject
    constructor(
        private val noteDao: NoteDao,
    ) {
        fun observe(
            kind: NoteKind,
            targetId: String,
        ): Flow<Note?> = noteDao.observe(kind.idFor(targetId)).map { it?.toNote() }

        /** Newest edit first. */
        fun observeAll(): Flow<List<Note>> = noteDao.observeAll().map { notes -> notes.mapNotNull { it.toNote() } }

        fun observeCount(): Flow<Int> = noteDao.observeCount()

        suspend fun get(
            kind: NoteKind,
            targetId: String,
        ): Note? = noteDao.get(kind.idFor(targetId))?.toNote()

        /**
         * Blank text deletes: an emptied note is the user removing it, not an empty note. A save with no
         * [subject] keeps the one already stored.
         */
        suspend fun save(
            kind: NoteKind,
            targetId: String,
            text: String,
            subject: NoteSubject? = null,
        ) {
            val id = kind.idFor(targetId)
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                noteDao.deleteById(id)
                return
            }
            val stored = noteDao.get(id)?.toNote()
            noteDao.upsert(
                Note(targetId, kind, trimmed, System.currentTimeMillis(), subject ?: stored?.subject, stored?.position).toEntity(),
            )
        }

        suspend fun saveSubject(
            kind: NoteKind,
            targetId: String,
            subject: NoteSubject,
        ) = noteDao.updateSubject(
            id = kind.idFor(targetId),
            title = subject.title,
            channelName = subject.channelName,
            channelId = subject.channelId,
            thumbnailUrl = subject.thumbnailUrl,
            durationSeconds = subject.durationSeconds,
            channelAvatarUrl = subject.channelAvatarUrl,
            channelHandle = subject.channelHandle,
            subscriberCountText = subject.subscriberCountText,
        )

        /** Stores [notes] in this order, for the Custom order sort. */
        suspend fun saveOrder(notes: List<Note>) = noteDao.updatePositions(notes.map { it.kind.idFor(it.targetId) })

        suspend fun delete(
            kind: NoteKind,
            targetId: String,
        ) = noteDao.deleteById(kind.idFor(targetId))

        suspend fun all(): List<Note> = noteDao.getAll().mapNotNull { it.toNote() }

        suspend fun restore(notes: List<Note>) {
            if (notes.isEmpty()) return
            noteDao.upsertAll(notes.map { it.toEntity() })
        }
    }

private fun Note.toEntity(): NoteEntity =
    NoteEntity(
        id = kind.idFor(targetId),
        targetId = targetId,
        kind = kind.name,
        text = text,
        updatedAt = updatedAt,
        title = subject?.title,
        channelName = subject?.channelName,
        channelId = subject?.channelId,
        thumbnailUrl = subject?.thumbnailUrl,
        durationSeconds = subject?.durationSeconds,
        channelAvatarUrl = subject?.channelAvatarUrl,
        channelHandle = subject?.channelHandle,
        subscriberCountText = subject?.subscriberCountText,
        position = position,
    )

private fun NoteEntity.toNote(): Note? {
    val parsedKind = NoteKind.entries.firstOrNull { it.name == kind } ?: return null
    val subject =
        title?.takeIf { it.isNotBlank() }?.let {
            NoteSubject(
                title = it,
                channelName = channelName.orEmpty(),
                channelId = channelId.orEmpty(),
                thumbnailUrl = thumbnailUrl.orEmpty(),
                durationSeconds = durationSeconds ?: 0,
                channelAvatarUrl = channelAvatarUrl.orEmpty(),
                channelHandle = channelHandle.orEmpty(),
                subscriberCountText = subscriberCountText.orEmpty(),
            )
        }
    return Note(targetId = targetId, kind = parsedKind, text = text, updatedAt = updatedAt, subject = subject, position = position)
}
