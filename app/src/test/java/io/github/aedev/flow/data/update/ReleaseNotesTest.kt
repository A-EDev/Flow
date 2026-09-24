package io.github.aedev.flow.data.update

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.update.NoteSpan.Link
import io.github.aedev.flow.data.update.NoteSpan.Plain
import io.github.aedev.flow.data.update.NoteSpan.Strong
import org.junit.Test

class ReleaseNotesTest {
    private val body =
        """
        # Flow v2.3.0

        **Release date:** 2026-09-28

        Enjoying Flow? Consider supporting it on [Patreon](https://patreon.com/A_EDev) to keep it free.

        ## New features

        - Discord RPC support by @PastaHimself
        - Play Shorts as one queue #823
        * A **bold** claim

        ### Improvements
        - Slimmer seekbar gaps #786

        ## Empty section
        """.trimIndent()

    @Test
    fun `the title and release date are dropped and the intro keeps its link`() {
        val notes = parseReleaseNotes(body)

        assertThat(notes.intro).hasSize(1)
        assertThat(notes.intro.single().spans)
            .containsExactly(
                Plain("Enjoying Flow? Consider supporting it on "),
                Link("Patreon", "https://patreon.com/A_EDev"),
                Plain(" to keep it free."),
            ).inOrder()
    }

    @Test
    fun `headings open sections and empty sections are left out`() {
        val notes = parseReleaseNotes(body)

        assertThat(notes.sections.map { it.title }).containsExactly("New features", "Improvements").inOrder()
        assertThat(notes.sections.first().items).hasSize(3)
    }

    @Test
    fun `mentions, issues and bold keep their meaning`() {
        val items = parseReleaseNotes(body).sections.first().items

        assertThat(items[0].spans.last()).isEqualTo(Link("@PastaHimself", "https://github.com/PastaHimself"))
        assertThat(items[1].spans.last()).isEqualTo(Link("#823", "https://github.com/A-EDev/Flow/issues/823"))
        assertThat(items[2].spans).containsExactly(Plain("A "), Strong("bold"), Plain(" claim")).inOrder()
    }

    @Test
    fun `wrapped paragraph lines join into one item and a colour hash is not an issue`() {
        val notes = parseReleaseNotes("## Fixes\nFix the ambient glow\nthat drifted to &#35;FFF and #fff\n")

        val spans =
            notes.sections
                .single()
                .items
                .single()
                .spans
        assertThat(spans).containsExactly(Plain("Fix the ambient glow that drifted to &#35;FFF and #fff"))
    }

    @Test
    fun `bare links become links`() {
        val spans = parseInline("See https://github.com/A-EDev/Flow/releases for more").spans

        assertThat(spans[1]).isEqualTo(Link("https://github.com/A-EDev/Flow/releases", "https://github.com/A-EDev/Flow/releases"))
    }
}
