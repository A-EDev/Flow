package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.ui.screens.settings.content.LinkHandling
import io.github.aedev.flow.ui.screens.settings.content.linkHandling
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkHandlingTest {
    @Test
    fun `before android 12 the system asks which app to use`() {
        assertEquals(
            LinkHandling.SystemAsks,
            linkHandling(perAddressChoice = false, allowed = true, enabledAddresses = 0, totalAddresses = 0),
        )
    }

    @Test
    fun `counts the addresses the user turned on`() {
        assertEquals(LinkHandling.All, linkHandling(perAddressChoice = true, allowed = true, enabledAddresses = 9, totalAddresses = 9))
        assertEquals(
            LinkHandling.Some(3, 9),
            linkHandling(perAddressChoice = true, allowed = true, enabledAddresses = 3, totalAddresses = 9),
        )
        assertEquals(LinkHandling.Off, linkHandling(perAddressChoice = true, allowed = true, enabledAddresses = 0, totalAddresses = 9))
    }

    @Test
    fun `link handling switched off overrides the addresses`() {
        assertEquals(LinkHandling.Off, linkHandling(perAddressChoice = true, allowed = false, enabledAddresses = 9, totalAddresses = 9))
    }
}
