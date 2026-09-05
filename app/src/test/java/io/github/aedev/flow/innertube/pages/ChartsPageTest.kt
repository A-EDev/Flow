package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartsPageTest {
    private val response = InnerTubeFixtures.browse("charts_global")

    @Test
    fun `chart shelves are typed by their items`() {
        val page = ChartsPage.fromBrowseResponse(response, country = null)
        assertEquals(listOf(ChartsPage.ChartType.PLAYLISTS, ChartsPage.ChartType.ARTISTS), page.sections.map { it.chartType })
        assertTrue(page.sections[0].items.all { it is PlaylistItem })
        assertTrue(page.sections[1].items.all { it is ArtistItem })
        assertEquals(
            "Alka Yagnik",
            page.sections[1]
                .items
                .first()
                .title,
        )
    }

    @Test
    fun `country is reported only when the chart menu supports it`() {
        assertEquals("US", ChartsPage.fromBrowseResponse(response, country = "US").countryCode)
        assertNull(ChartsPage.fromBrowseResponse(response, country = "LB").countryCode)
        assertNull(ChartsPage.fromBrowseResponse(response, country = null).countryCode)
    }

    @Test
    fun `country codes decode from the menu keys`() {
        assertEquals("AR", ChartsPage.countryCodeFromFormItemKey("EidleHBsb3JlX2NoYXJ0c19jb3VudHJ5X21lbnVfMzE2NzY2NTY3QVIgkQEoAQ%3D%3D"))
        assertNull(ChartsPage.countryCodeFromFormItemKey("not-base64!"))
    }
}
