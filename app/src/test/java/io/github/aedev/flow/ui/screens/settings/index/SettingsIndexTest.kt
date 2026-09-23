package io.github.aedev.flow.ui.screens.settings.index

import io.github.aedev.flow.ui.components.settings.SettingAvailability
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {
    private val all = SettingsIndex.all

    @Test
    fun `every key is unique`() {
        val duplicates = all.groupBy { it.key }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate keys: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every page but the custom theme editor has an entry`() {
        val pages = all.mapNotNull(DestinationIndex::destinationOf).toSet()
        assertEquals(SettingsDestination.entries.toSet() - SettingsDestination.HOME - SettingsDestination.CUSTOM_THEME_EDIT, pages)
    }

    @Test
    fun `reveal targets point at an entry on the same page`() {
        val byKey = all.associateBy { it.key }
        all.filter { it.revealVia != null }.forEach { entry ->
            val via = byKey[entry.revealVia]
            val groupHeader = via == null && entry.revealVia!!.isNotBlank()
            assertTrue("${entry.key} reveals via unknown ${entry.revealVia}", via != null || groupHeader)
            if (via != null) assertEquals(entry.key, entry.destination, via.destination)
        }
    }

    @Test
    fun `foss builds list no github-only entries`() {
        val foss = all.filter { it.availability.isAvailable(githubFeatures = false, sdk = 36) }
        assertFalse(foss.any { it.availability == SettingAvailability.GithubOnly })
    }

    @Test
    fun `targets survive encoding`() {
        val target = SettingsTarget(SettingsDestination.QUALITY, tab = "shorts", highlight = "quality.shorts.wifi")
        assertEquals(target, SettingsTarget.decode(target.encode()))
        assertEquals(SettingsTarget(SettingsDestination.THEME), SettingsTarget.decode("theme"))
        assertNull(SettingsTarget.decode("nope"))
        assertNull(SettingsTarget.decode(null))
    }

    @Test
    fun `sub-pages resolve their root`() {
        assertEquals(SettingsDestination.APPEARANCE, SettingsDestination.CUSTOM_THEME.root)
        assertEquals(
            listOf(SettingsDestination.APPEARANCE, SettingsDestination.THEME, SettingsDestination.CUSTOM_THEME),
            SettingsDestination.CUSTOM_THEME.path,
        )
    }
}
