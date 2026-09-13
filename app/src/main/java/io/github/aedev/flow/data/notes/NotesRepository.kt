package io.github.aedev.flow.data.notes

import io.github.aedev.flow.data.local.dao.NoteDao
import io.github.aedev.flow.data.local.entity.NoteEntity
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

data class Note(
    val targetId: String,
    val kind: NoteKind,
    val text: String,
    val updatedAt: Long,
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

        /** Blank text deletes: an emptied note is the user removing it, not an empty note. */
        suspend fun save(
            kind: NoteKind,
            targetId: String,
            text: String,
        ) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                noteDao.deleteById(kind.idFor(targetId))
                return
            }
            noteDao.upsert(
                NoteEntity(
                    id = kind.idFor(targetId),
                    targetId = targetId,
                    kind = kind.name,
                    text = trimmed,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        suspend fun delete(
            kind: NoteKind,
            targetId: String,
        ) = noteDao.deleteById(kind.idFor(targetId))

        suspend fun all(): List<Note> = noteDao.getAll().mapNotNull { it.toNote() }

        suspend fun restore(notes: List<Note>) {
            if (notes.isEmpty()) return
            noteDao.upsertAll(
                notes.map { note ->
                    NoteEntity(
                        id = note.kind.idFor(note.targetId),
                        targetId = note.targetId,
                        kind = note.kind.name,
                        text = note.text,
                        updatedAt = note.updatedAt,
                    )
                },
            )
        }
    }

private fun NoteEntity.toNote(): Note? {
    val parsedKind = NoteKind.entries.firstOrNull { it.name == kind } ?: return null
    return Note(targetId = targetId, kind = parsedKind, text = text, updatedAt = updatedAt)
}
