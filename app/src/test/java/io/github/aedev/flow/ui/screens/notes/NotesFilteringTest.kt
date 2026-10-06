package io.github.aedev.flow.ui.screens.notes

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NoteSubject
import org.junit.Test

class NotesFilteringTest {
    private val router =
        Note(
            "v1",
            NoteKind.Video,
            "12:34 the VLAN drops packets",
            updatedAt = 30L,
            subject = NoteSubject("Router build", "Wire & Wave", "UC1"),
        )
    private val bread =
        Note("v2", NoteKind.Video, "fold every 30 min", updatedAt = 10L, subject = NoteSubject("Sourdough", "Crumb Lab", "UC2"))
    private val synth =
        Note("UC3", NoteKind.Channel, "Unsubscribed: too many sponsor reads", updatedAt = 20L, subject = NoteSubject("Northern Synth Club"))
    private val notes = listOf(bread, synth, router)

    @Test
    fun `newest edit first by default, oldest or by title on request`() {
        assertThat(notes.visibleNotes("", NotesFilter.All, NotesSort.Recent)).containsExactly(router, synth, bread).inOrder()
        assertThat(notes.visibleNotes("", NotesFilter.All, NotesSort.Oldest)).containsExactly(bread, synth, router).inOrder()
        assertThat(notes.visibleNotes("", NotesFilter.All, NotesSort.Title)).containsExactly(synth, router, bread).inOrder()
    }

    @Test
    fun `the filter keeps one kind`() {
        assertThat(notes.visibleNotes("", NotesFilter.Videos, NotesSort.Recent)).containsExactly(router, bread).inOrder()
        assertThat(notes.visibleNotes("", NotesFilter.Channels, NotesSort.Recent)).containsExactly(synth)
    }

    @Test
    fun `search reads the note, the title and the channel, ignoring case and accents`() {
        assertThat(notes.visibleNotes("vlan", NotesFilter.All, NotesSort.Recent)).containsExactly(router)
        assertThat(notes.visibleNotes("crumb", NotesFilter.All, NotesSort.Recent)).containsExactly(bread)
        assertThat(notes.visibleNotes("sýnth", NotesFilter.All, NotesSort.Recent)).containsExactly(synth)
        assertThat(notes.visibleNotes("router wave", NotesFilter.All, NotesSort.Recent)).containsExactly(router)
        assertThat(notes.visibleNotes("nothing here", NotesFilter.All, NotesSort.Recent)).isEmpty()
    }

    @Test
    fun `a note opens its video with what it saved`() {
        val video = router.toVideo()

        assertThat(video.id).isEqualTo("v1")
        assertThat(video.title).isEqualTo("Router build")
        assertThat(video.channelId).isEqualTo("UC1")
    }

    @Test
    fun `custom order follows the hand-made places, unplaced notes after them newest first`() {
        val placed = listOf(bread.copy(position = 0), router.copy(position = 1), synth)

        assertThat(placed.visibleNotes("", NotesFilter.All, NotesSort.Custom).map { it.targetId })
            .containsExactly("v2", "v1", "UC3")
            .inOrder()
    }

    @Test
    fun `shared notes carry their title, link and text`() {
        val text = shareText(listOf(router, synth))

        assertThat(text).contains("Router build\nhttps://www.youtube.com/watch?v=v1\n12:34 the VLAN drops packets")
        assertThat(text).contains("Northern Synth Club\nhttps://www.youtube.com/channel/UC3\nUnsubscribed")
    }

    @Test
    fun `an unknown stored sort reads as recently edited`() {
        assertThat(NotesSort.fromName(null)).isEqualTo(NotesSort.Recent)
        assertThat(NotesSort.fromName("Custom")).isEqualTo(NotesSort.Custom)
    }
}
