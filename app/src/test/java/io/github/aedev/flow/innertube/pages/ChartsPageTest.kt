package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.pages.InnerTubeJson.PLAYLIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.USER
import io.github.aedev.flow.innertube.pages.InnerTubeJson.artistRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.browseEndpoint
import io.github.aedev.flow.innertube.pages.InnerTubeJson.carousel
import io.github.aedev.flow.innertube.pages.InnerTubeJson.musicShelf
import io.github.aedev.flow.innertube.pages.InnerTubeJson.run
import io.github.aedev.flow.innertube.pages.InnerTubeJson.separator
import io.github.aedev.flow.innertube.pages.InnerTubeJson.twoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

class ChartsPageTest {
    private fun countryKey(code: String) =
        URLEncoder.encode(Base64.getEncoder().encodeToString("'explore_charts_country_menu_316766567$code (".toByteArray()), "UTF-8")

    private val response =
        InnerTubeJson.browse(
            sections =
                listOf(
                    musicShelf(null, emptyList(), formItemKeys = listOf(countryKey("US"), countryKey("GB"), "not-base64!")),
                    carousel(
                        "Video charts",
                        listOf(
                            twoRow(
                                "VLPL4f",
                                PLAYLIST,
                                "Daily Top Music Videos - Global",
                                listOf(run("Chart"), separator(), run("YouTube Music", browseEndpoint("UCrK", USER))),
                                playlistId = "PL4f",
                            ),
                        ),
                    ),
                    carousel(
                        "Top artists",
                        listOf(
                            artistRow("UCalka", "Alka Yagnik", "2.23M subscribers"),
                            artistRow("UCarijit", "Arijit Singh", "1M subscribers"),
                        ),
                    ),
                ),
            singleColumn = true,
        )

    @Test
    fun `chart shelves are typed by their items`() {
        val page = ChartsPage.fromBrowseResponse(response, country = null)
        assertEquals(listOf(ChartsPage.ChartType.PLAYLISTS, ChartsPage.ChartType.ARTISTS), page.sections.map { it.chartType })
        assertTrue(page.sections[0].items.all { it is PlaylistItem })
        val artists = page.sections[1].items.filterIsInstance<ArtistItem>()
        assertEquals(listOf("Alka Yagnik", "Arijit Singh"), artists.map { it.title })
        assertEquals("2.23M subscribers", artists.first().subscriberCountText)
    }

    @Test
    fun `country is reported only when the chart menu supports it`() {
        assertEquals("US", ChartsPage.fromBrowseResponse(response, country = "US").countryCode)
        assertEquals("GB", ChartsPage.fromBrowseResponse(response, country = "GB").countryCode)
        assertNull(ChartsPage.fromBrowseResponse(response, country = "LB").countryCode)
        assertNull(ChartsPage.fromBrowseResponse(response, country = null).countryCode)
    }

    @Test
    fun `country codes decode from the menu keys`() {
        assertEquals("AR", ChartsPage.countryCodeFromFormItemKey("EidleHBsb3JlX2NoYXJ0c19jb3VudHJ5X21lbnVfMzE2NzY2NTY3QVIgkQEoAQ%3D%3D"))
        assertNull(ChartsPage.countryCodeFromFormItemKey("not-base64!"))
    }
}
