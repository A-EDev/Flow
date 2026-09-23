package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun setting(
        key: String,
        title: String,
        summary: String? = null,
        keywords: List<String> = emptyList(),
        breadcrumb: String = "Appearance",
    ) = SearchableSetting(
        entry = SettingEntry(key = key, title = 0, destination = SettingsDestination.APPEARANCE),
        title = title,
        summary = summary,
        keywords = keywords,
        breadcrumb = breadcrumb,
    )

    private val settings =
        listOf(
            setting("theme", "Theme", summary = "Palette and light or dark style", keywords = listOf("dark mode", "amoled", "night")),
            setting("cache", "Media cache size", summary = "Disk space for cached video", breadcrumb = "Downloads & storage"),
            setting("captions", "Turn captions on automatically", keywords = listOf("subtitles", "cc"), breadcrumb = "Player & playback"),
            setting(
                "country",
                "Content country",
                summary = "Country used for trending and charts",
                keywords = listOf("region", "trending"),
            ),
            setting("resolution", "Resolución en Wi-Fi", breadcrumb = "Calidad"),
        )

    private fun keys(query: String) = SettingsSearch.search(query, settings).map { it.entry.key }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(keys("   ").isEmpty())
    }

    @Test
    fun `keyword finds a setting whose title does not mention it`() {
        assertEquals(listOf("theme"), keys("amoled"))
        assertEquals("country", keys("trending").first())
    }

    @Test
    fun `title prefix outranks summary match`() {
        assertEquals(listOf("captions"), keys("captions"))
        assertEquals("cache", keys("cache").first())
    }

    @Test
    fun `every query word has to match`() {
        assertEquals(listOf("cache"), keys("media size"))
        assertTrue(keys("media amoled").isEmpty())
    }

    @Test
    fun `accents and case are ignored`() {
        assertEquals(listOf("resolution"), keys("RESOLUCION"))
    }

    @Test
    fun `page name matches last`() {
        assertEquals(listOf("cache"), keys("storage"))
    }
}
