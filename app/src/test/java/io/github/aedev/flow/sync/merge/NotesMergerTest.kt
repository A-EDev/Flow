package io.github.aedev.flow.sync.merge

import io.github.aedev.flow.sync.canonical.CanonicalNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A note is one field the user typed, so the merge is last-write-wins. The cases that matter are the
 * ones where losing is silent: a stale peer overwriting a newer edit, or a cleared note coming back.
 */
class NotesMergerTest {
    private fun note(
        id: String,
        text: String,
        updatedAt: Long,
        deleted: Boolean = false,
    ) = CanonicalNote(id = id, targetId = id.substringAfter(':'), kind = "Channel", text = text, updatedAt = updatedAt, deleted = deleted)

    @Test
    fun `the later edit wins whichever side it came from`() {
        val local = listOf(note("channel:UC1", "old", 100L))
        val remote = listOf(note("channel:UC1", "new", 200L))

        assertEquals("new", NotesMerger.merge(local, remote).single().text)
        assertEquals("new", NotesMerger.merge(remote, local).single().text)
    }

    @Test
    fun `a stale peer cannot overwrite a newer note`() {
        val local = listOf(note("channel:UC1", "current", 500L))
        val remote = listOf(note("channel:UC1", "stale", 100L))

        assertEquals("current", NotesMerger.merge(local, remote).single().text)
    }

    @Test
    fun `a deletion carries forward instead of the note reappearing`() {
        val local = listOf(note("channel:UC1", "written", 100L))
        val remote = listOf(note("channel:UC1", "", 200L, deleted = true))

        assertTrue(NotesMerger.merge(local, remote).single().deleted)
    }

    @Test
    fun `a note written after a deletion wins`() {
        val local = listOf(note("channel:UC1", "", 100L, deleted = true))
        val remote = listOf(note("channel:UC1", "rewritten", 200L))

        val merged = NotesMerger.merge(local, remote).single()
        assertEquals("rewritten", merged.text)
        assertTrue(!merged.deleted)
    }

    @Test
    fun `a tie on the same instant resolves to the deletion`() {
        val local = listOf(note("channel:UC1", "kept", 100L))
        val remote = listOf(note("channel:UC1", "", 100L, deleted = true))

        assertTrue(NotesMerger.merge(local, remote).single().deleted)
        assertTrue(NotesMerger.merge(remote, local).single().deleted)
    }

    @Test
    fun `notes only one side has survive the merge`() {
        val local = listOf(note("channel:UC1", "mine", 100L))
        val remote = listOf(note("video:abc", "theirs", 100L))

        val merged = NotesMerger.merge(local, remote)
        assertEquals(listOf("channel:UC1", "video:abc"), merged.map { it.id })
    }

    @Test
    fun `merging is order-independent for the same inputs`() {
        val a = listOf(note("channel:UC1", "a", 300L), note("video:x", "b", 100L))
        val b = listOf(note("channel:UC1", "c", 200L), note("video:x", "d", 400L))

        assertEquals(
            NotesMerger.merge(a, b).map { it.id to it.text },
            NotesMerger.merge(b, a).map { it.id to it.text },
        )
    }

    @Test
    fun `an empty side leaves the other untouched`() {
        val local = listOf(note("channel:UC1", "only", 100L))

        assertEquals(local, NotesMerger.merge(local, emptyList()))
        assertEquals(local, NotesMerger.merge(emptyList(), local))
    }
}
