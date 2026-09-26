package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Run
import io.github.aedev.flow.innertube.models.Runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistPageTrackCountTest {
    private fun subtitle(vararg groups: String) =
        Runs(groups.flatMapIndexed { index, text -> listOfNotNull(Run(" • ", null).takeIf { index > 0 }, Run(text, null)) })

    @Test
    fun `the count is the group before the length whether or not views lead`() {
        assertEquals(49, PlaylistPage.trackCountFrom(subtitle("2.4K views", "49 tracks", "5+ hours")))
        assertEquals(75, PlaylistPage.trackCountFrom(subtitle("75 canciones", "5 horas y 1 minuto")))
    }

    @Test
    fun `grouped thousands keep every digit`() {
        assertEquals(1234, PlaylistPage.trackCountFrom(subtitle("1.234 canciones", "80 horas")))
        assertEquals(1234, PlaylistPage.trackCountFrom(subtitle("1 234 titres", "80 heures")))
    }

    @Test
    fun `no count is read from a subtitle too short to hold one`() {
        assertNull(PlaylistPage.trackCountFrom(subtitle("5+ hours")))
        assertNull(PlaylistPage.trackCountFrom(null))
    }
}
