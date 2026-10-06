package io.github.aedev.flow.data.notes

import io.github.aedev.flow.data.local.dao.NoteDao
import io.github.aedev.flow.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesRepositoryTest {
    private val rows = MutableStateFlow<Map<String, NoteEntity>>(emptyMap())

    private val dao =
        object : NoteDao {
            override fun observe(id: String): Flow<NoteEntity?> = rows.map { it[id] }

            override suspend fun get(id: String): NoteEntity? = rows.value[id]

            override suspend fun getAll(): List<NoteEntity> = rows.value.values.sortedByDescending { it.updatedAt }

            override fun observeAll(): Flow<List<NoteEntity>> = rows.map { it.values.sortedByDescending { note -> note.updatedAt } }

            override fun observeCount(): Flow<Int> = rows.map { it.size }

            override suspend fun updateSubject(
                id: String,
                title: String,
                channelName: String,
                channelId: String,
                thumbnailUrl: String,
                durationSeconds: Int,
                channelAvatarUrl: String,
                channelHandle: String,
                subscriberCountText: String,
            ) {
                val row = rows.value[id] ?: return
                rows.value = rows.value +
                    (
                        id to
                            row.copy(
                                title = title,
                                channelName = channelName,
                                channelId = channelId,
                                thumbnailUrl = thumbnailUrl,
                                durationSeconds = durationSeconds,
                                channelAvatarUrl = channelAvatarUrl,
                                channelHandle = channelHandle,
                                subscriberCountText = subscriberCountText,
                            )
                    )
            }

            override suspend fun updatePosition(
                id: String,
                position: Int,
            ) {
                val row = rows.value[id] ?: return
                rows.value = rows.value + (id to row.copy(position = position))
            }

            override suspend fun upsert(note: NoteEntity) {
                rows.value = rows.value + (note.id to note)
            }

            override suspend fun upsertAll(notes: List<NoteEntity>) {
                rows.value = rows.value + notes.associateBy { it.id }
            }

            override suspend fun delete(note: NoteEntity) = deleteById(note.id)

            override suspend fun deleteById(id: String) {
                rows.value = rows.value - id
            }
        }

    private val repository = NotesRepository(dao)

    @Test
    fun `a channel note and a video note with the same target do not collide`() =
        runTest {
            repository.save(NoteKind.Channel, "UC123", "why I unsubscribed")
            repository.save(NoteKind.Video, "UC123", "points from the video")

            assertEquals("why I unsubscribed", repository.observe(NoteKind.Channel, "UC123").first()?.text)
            assertEquals("points from the video", repository.observe(NoteKind.Video, "UC123").first()?.text)
        }

    @Test
    fun `saving over a note replaces it rather than adding a second`() =
        runTest {
            repository.save(NoteKind.Channel, "UC123", "first")
            repository.save(NoteKind.Channel, "UC123", "second")

            assertEquals("second", repository.observe(NoteKind.Channel, "UC123").first()?.text)
            assertEquals(1, repository.all().size)
        }

    @Test
    fun `clearing the text deletes the note instead of storing an empty one`() =
        runTest {
            repository.save(NoteKind.Channel, "UC123", "something")
            repository.save(NoteKind.Channel, "UC123", "   ")

            assertNull(repository.observe(NoteKind.Channel, "UC123").first())
            assertTrue(repository.all().isEmpty())
        }

    @Test
    fun `surrounding whitespace is not stored`() =
        runTest {
            repository.save(NoteKind.Video, "abc", "  trimmed  ")

            assertEquals("trimmed", repository.observe(NoteKind.Video, "abc").first()?.text)
        }

    @Test
    fun `a restored note round-trips through the same id`() =
        runTest {
            repository.restore(listOf(Note(targetId = "UC9", kind = NoteKind.Channel, text = "restored", updatedAt = 42L)))

            val note = repository.observe(NoteKind.Channel, "UC9").first()
            assertEquals("restored", note?.text)
            assertEquals(42L, note?.updatedAt)
        }

    @Test
    fun `an unknown kind on disk is skipped rather than crashing the list`() =
        runTest {
            dao.upsert(NoteEntity(id = "playlist:x", targetId = "x", kind = "Playlist", text = "future", updatedAt = 1L))
            repository.save(NoteKind.Channel, "UC1", "kept")

            assertEquals(listOf("kept"), repository.all().map { it.text })
        }

    private val subject =
        NoteSubject(
            title = "Router build",
            channelName = "Wire & Wave",
            channelId = "UC1",
            thumbnailUrl = "https://i.ytimg.com/vi/v1/hq720.jpg",
            durationSeconds = 1120,
        )

    @Test
    fun `a note saves what it is about, and a later edit keeps it`() =
        runTest {
            repository.save(NoteKind.Video, "v1", "12:34 the cache", subject)
            repository.save(NoteKind.Video, "v1", "12:34 the cache misses")

            val note = repository.observe(NoteKind.Video, "v1").first()
            assertEquals(subject, note?.subject)
            assertEquals("12:34 the cache misses", note?.text)
        }

    @Test
    fun `filling in an old note's details leaves its text and time alone`() =
        runTest {
            repository.save(NoteKind.Video, "v1", "keep this")
            val before = repository.get(NoteKind.Video, "v1")!!
            assertNull(before.subject)

            repository.saveSubject(NoteKind.Video, "v1", subject)

            val after = repository.get(NoteKind.Video, "v1")!!
            assertEquals(subject, after.subject)
            assertEquals(before.updatedAt, after.updatedAt)
            assertEquals(before.text, after.text)
        }

    @Test
    fun `every note is listed, channel and video, with a count`() =
        runTest {
            repository.save(NoteKind.Video, "v1", "a")
            repository.save(NoteKind.Channel, "UC1", "b")

            assertEquals(
                setOf("v1", "UC1"),
                repository
                    .observeAll()
                    .first()
                    .map { it.targetId }
                    .toSet(),
            )
            assertEquals(2, repository.observeCount().first())
        }

    @Test
    fun `a hand-made order is kept, and a later edit does not lose its place`() =
        runTest {
            repository.save(NoteKind.Video, "v1", "first")
            repository.save(NoteKind.Video, "v2", "second")
            repository.saveOrder(listOf(repository.get(NoteKind.Video, "v2")!!, repository.get(NoteKind.Video, "v1")!!))

            repository.save(NoteKind.Video, "v1", "first, edited")

            assertEquals(0, repository.get(NoteKind.Video, "v2")?.position)
            assertEquals(1, repository.get(NoteKind.Video, "v1")?.position)
        }
}
