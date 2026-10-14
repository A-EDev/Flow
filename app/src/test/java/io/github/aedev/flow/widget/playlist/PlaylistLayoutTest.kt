package io.github.aedev.flow.widget.playlist

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistLayoutTest {
    @Test
    fun aFourByTwoPhoneCellDrawsTheList() {
        assertEquals(PlaylistLayout.LIST, PlaylistLayout.forSize(DpSize(320.dp, 180.dp)))
    }

    @Test
    fun aShortWideCellKeepsTheCoverBesideTheName() {
        assertEquals(PlaylistLayout.WIDE, PlaylistLayout.forSize(DpSize(320.dp, 110.dp)))
    }

    @Test
    fun aTwoByTwoCellStacksTheCoverAboveTheName() {
        assertEquals(PlaylistLayout.TALL, PlaylistLayout.forSize(DpSize(170.dp, 180.dp)))
    }

    @Test
    fun thePickerPreviewShowsTheList() {
        assertEquals(PlaylistLayout.LIST, PlaylistLayout.forSize(PlaylistLayout.PreviewSize))
    }
}
